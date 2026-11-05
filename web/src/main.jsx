import React, { useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
  updateProfile,
} from "firebase/auth";
import {
  addDoc,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  increment,
  limit,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import { auth, db, firebaseConfigured, functions, storage } from "./firebase";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import "./styles.css";

const STATUS_FLOW = {
  BOOKED: ["ACCEPTED", "REJECTED"],
  ACCEPTED: ["PICKED_UP"],
  PICKED_UP: ["WASHING"],
  WASHING: ["DRYING"],
  DRYING: ["READY"],
  READY: ["OUT_FOR_DELIVERY"],
  OUT_FOR_DELIVERY: ["DELIVERED"],
};

const STATUS_LABELS = {
  BOOKED: "Booked",
  ACCEPTED: "Accepted",
  PICKED_UP: "Picked Up",
  WASHING: "Washing",
  DRYING: "Drying",
  READY: "Ready",
  OUT_FOR_DELIVERY: "Out for Delivery",
  DELIVERED: "Delivered",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  UNKNOWN: "Status unavailable",
};

const money = (value) => new Intl.NumberFormat("en-UG", {
  style: "currency",
  currency: "UGX",
  maximumFractionDigits: 0,
}).format(Number(value || 0));

const shortDate = (value) => {
  const millis = timestampMillis(value);
  return millis > 0
    ? new Date(millis).toLocaleDateString("en-UG", {
      day: "numeric", month: "short", year: "numeric",
    })
    : "—";
};
const shortDateTime = (value) => {
  const millis = timestampMillis(value);
  return millis > 0 ? new Intl.DateTimeFormat("en-UG", {
    weekday: "short", day: "numeric", year: "numeric",
    hour: "numeric", minute: "2-digit",
  }).format(new Date(millis)) : "Sending…";
};
const chatMessageTime = (value) => {
  const millis = timestampMillis(value);
  return millis > 0
    ? new Intl.DateTimeFormat("en-UG", {
      day: "numeric", month: "short", year: "numeric",
      hour: "numeric", minute: "2-digit",
    })
      .format(new Date(millis))
    : "Sending…";
};

function statusLabel(value) {
  const status = String(value || "UNKNOWN").toUpperCase();
  return STATUS_LABELS[status] || status.toLowerCase().replaceAll("_", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function fulfillmentLabel(value) {
  const normalized = String(value || "").toLowerCase().replaceAll(" ", "_");
  if (["dropoff", "drop_off"].includes(normalized)) return "Customer drop-off";
  if (["pickup_delivery", "pickup_&_delivery"].includes(normalized)) return "Pickup and delivery";
  return "Not specified";
}

function providerOrderGroup(status) {
  if (status === "BOOKED") return "pending";
  if (["ACCEPTED", "PICKED_UP", "WASHING", "DRYING", "READY", "OUT_FOR_DELIVERY"]
    .includes(status)) return "progress";
  return "history";
}

function serviceFeeForDisplay(booking) {
  const explicitFee = Number(booking.serviceFee);
  return Number.isFinite(explicitFee) && explicitFee > 0
    ? explicitFee : Number(booking.tax || 0);
}

const KNOWN_SERVICES = [
  "Wash & Fold",
  "Dry Cleaning",
  "Ironing & Pressing",
  "Bedding & Blankets",
  "Shoe Cleaning",
  "Stain Removal",
  "Carpet Cleaning",
  "Curtain Cleaning",
  "Express Laundry",
];

function timestampMillis(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? numeric : 0;
}

function matchesSearch(value, search) {
  return String(value || "").toLocaleLowerCase().includes(search.trim().toLocaleLowerCase());
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[character]);
}

function ExportActions({ rows, columns, fileName }) {
  function exportFile(format) {
    if (rows.length === 0) return;
    const heading = columns.map((column) => `<th>${escapeHtml(column.label)}</th>`).join("");
    const body = rows.map((row) => `<tr>${columns.map((column) =>
      `<td>${escapeHtml(column.value(row))}</td>`).join("")}</tr>`).join("");
    const markup = `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(fileName)}</title>
      <style>body{font:14px Arial,sans-serif;color:#172b42;padding:24px}h1{font-size:20px}table{border-collapse:collapse;width:100%}
      th,td{border:1px solid #cbd5e1;padding:8px;text-align:left}th{background:#eaf1f8}
      @media print{body{padding:0}button{display:none}}</style></head><body><h1>${escapeHtml(fileName)}</h1>
      <table><thead><tr>${heading}</tr></thead><tbody>${body}</tbody></table></body></html>`;
    if (format === "pdf") {
      const printWindow = window.open("", "_blank");
      if (!printWindow) return;
      printWindow.opener = null;
      printWindow.document.open();
      printWindow.document.write(`${markup}<script>window.addEventListener("load",()=>window.print());</script>`);
      printWindow.document.close();
      return;
    }
    const mime = format === "doc" ? "application/msword;charset=utf-8" : "application/vnd.ms-excel;charset=utf-8";
    const extension = format === "doc" ? "doc" : "xls";
    const blob = new Blob([`\ufeff${markup}`], { type: mime });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${fileName.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.${extension}`;
    anchor.click();
    URL.revokeObjectURL(url);
  }
  return <div className="export-actions" aria-label={`Export ${fileName}`}>
    <button className="secondary small" disabled={!rows.length} onClick={() => exportFile("doc")}>Word</button>
    <button className="secondary small" disabled={!rows.length} onClick={() => exportFile("excel")}>Excel</button>
    <button className="secondary small" disabled={!rows.length} onClick={() => exportFile("pdf")}>PDF</button>
  </div>;
}

function Brand({ light = false, accent = "blue" }) {
  const logoName = light
    ? `washlink-logo-white-${["blue", "violet", "emerald", "amber"].includes(accent) ? accent : "blue"}.png`
    : "washlink-logo.png";
  return <div className={light ? "brand light" : "brand"}>
    <img className="washlink-logo"
      src={`${import.meta.env.BASE_URL}${logoName}`}
      alt="WashLink" />
    <span className="brand-sub">OPERATIONS</span>
  </div>;
}

function App() {
  const [theme, setTheme] = useState(() => {
    return "light";
  });
  const [accent, setAccent] = useState(() => {
    return "blue";
  });
  const [session, setSession] = useState(null);
  const [role, setRole] = useState("");
  const [authLoading, setAuthLoading] = useState(firebaseConfigured);
  const [page, setPage] = useState("overview");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [targetBookingId, setTargetBookingId] = useState("");
  const menuToggleRef = useRef(null);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    if (session) window.localStorage.setItem("washlink-saved-theme", theme);
  }, [theme, session]);

  useEffect(() => {
    document.documentElement.dataset.accent = accent;
    if (session) window.localStorage.setItem("washlink-saved-accent", accent);
  }, [accent, session]);

  useEffect(() => {
    if (!auth) return undefined;
    let active = true;
    let authCheckId = 0;
    const unsubscribe = onAuthStateChanged(auth, async (user) => {
      const checkId = ++authCheckId;
      setSession(user);
      setRole("");
      setAuthLoading(Boolean(user));
      if (!user) {
        setTheme("light");
        setAccent("blue");
        setAuthLoading(false);
        return;
      }
      const isCurrentCheck = () => active && checkId === authCheckId
        && auth.currentUser?.uid === user.uid;
      try {
        if (!user.emailVerified) {
          if (!isCurrentCheck()) return;
          setNotice("Verify your email address before using the operations workspace.");
          await signOut(auth);
          return;
        }
        const profile = await getDoc(doc(db, "users", user.uid));
        if (!isCurrentCheck()) return;
        const accountRole = profile.exists() ? profile.data().role : "";
        if (!profile.exists() || profile.data().isSuspended === true
            || !["admin", "provider"].includes(accountRole)) {
          setNotice("This workspace is only available to active provider and admin accounts.");
          await signOut(auth);
          return;
        }
        setRole(accountRole);
        const savedTheme = window.localStorage.getItem("washlink-saved-theme")
          || window.localStorage.getItem("washlink-theme");
        const savedAccent = window.localStorage.getItem("washlink-saved-accent")
          || window.localStorage.getItem("washlink-accent");
        if (["dark", "light", "cream"].includes(savedTheme)) setTheme(savedTheme);
        if (["blue", "violet", "emerald", "amber"].includes(savedAccent)) {
          setAccent(savedAccent);
        }
        setPage("overview");
        setAuthLoading(false);
      } catch (error) {
        if (!isCurrentCheck()) return;
        setNotice(error.message || "Could not verify your account role.");
        setAuthLoading(false);
        await signOut(auth);
      }
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  useEffect(() => {
    if (!menuOpen) return undefined;
    document.querySelector(".sidebar .nav-link")?.focus();
    function closeOnEscape(event) {
      if (event.key === "Escape") setMenuOpen(false);
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => {
      window.removeEventListener("keydown", closeOnEscape);
      menuToggleRef.current?.focus();
    };
  }, [menuOpen]);

  if (!firebaseConfigured) return <Setup />;
  if (authLoading) return <div className="center-page" role="status" aria-label="Checking your session">
    <div className="spinner" aria-hidden="true" />
  </div>;
  if (!session || !role) {
    return <Login notice={notice} onNotice={setNotice} />;
  }

  const links = role === "admin"
    ? [["overview", "Overview"], ["bookings", "Bookings"], ["providers", "Providers"], ["users", "Users"], ["reports", "Reports"]]
    : [["overview", "Overview"], ["orders", "Orders"], ["customers", "Customers"], ["inventory", "Inventory"], ["pos", "In-person POS & invoices"], ["staff", "Staff roster"], ["services", "Services"], ["reports", "Reports"], ["riders", "Riders"], ["profile", "Business profile"], ["payouts", "Payout settings"], ["messages", "Messages"]];

  return (
    <div className="workspace">
      {menuOpen && <button className="sidebar-scrim" aria-label="Close navigation menu"
        onClick={() => setMenuOpen(false)} />}
      <aside className={menuOpen ? "sidebar sidebar-open" : "sidebar"}>
        <Brand light accent={accent} />
        <div className="role-chip">{role === "admin" ? "Administrator" : "Laundry provider"}</div>
        <nav id="workspace-navigation" aria-label="Workspace navigation">
          {links.map(([key, label]) => (
            <button key={key} className={page === key ? "nav-link active" : "nav-link"}
              aria-current={page === key ? "page" : undefined}
              onClick={() => { setPage(key); setNotice(""); setMenuOpen(false); }}>
              <span className="nav-dot" />{label}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="account-email">{session.email}</div>
          <button className="signout" onClick={() => signOut(auth)}>Sign out</button>
      </div>
      </aside>
      <main className="main-panel">
        <header className="topbar">
          <button className="menu-toggle" aria-label={menuOpen ? "Close menu" : "Open menu"}
            aria-controls="workspace-navigation" aria-expanded={menuOpen} ref={menuToggleRef}
            onClick={() => setMenuOpen((open) => !open)}>
            <span /><span /><span />
          </button>
          <div className="mobile-brand">WashLink <span>Operations</span></div>
          <div className="topbar-spacer" />
          <div className="topbar-tools">
            <label className="theme-picker">
              <span className="sr-only">Appearance</span>
              <select value={theme} onChange={(event) => setTheme(event.target.value)}
                aria-label="Choose appearance">
                <option value="light">Light theme</option>
                <option value="dark">Dark theme</option>
                <option value="cream">Cream theme</option>
              </select>
            </label>
            <label className="accent-picker">
              <span className="sr-only">Accent color</span>
              <select value={accent} onChange={(event) => setAccent(event.target.value)}
                aria-label="Choose accent color">
                <option value="blue">Blue</option>
                <option value="violet">Violet</option>
                <option value="emerald">Emerald</option>
                <option value="amber">Amber</option>
              </select>
            </label>
          </div>
          <span className="live-dot">Signed in</span>
          <div className="topbar-account">
            {role === "provider" && <ProviderNotifications uid={session.uid} setNotice={setNotice}
              setPage={setPage} setTargetBookingId={setTargetBookingId} />}
            <ProfilePhotoControl uid={session.uid} email={session.email || ""} role={role}
              authPhotoUrl={session.photoURL || ""} setNotice={setNotice} setBusy={setBusy} />
          </div>
        </header>
        <div className="content">
          <nav className="breadcrumbs" aria-label="Breadcrumb">
            <span>Workspace</span><span className="breadcrumb-separator" aria-hidden="true">/</span>
            <span aria-current="page">{links.find(([key]) => key === page)?.[1] || "Overview"}</span>
          </nav>
          {notice && <div className="notice" role="status" aria-live="polite"><span>{notice}</span>
            <button aria-label="Dismiss notification" onClick={() => setNotice("")}>×</button></div>}
          {page === "overview" && role === "admin" && <AdminOverview setPage={setPage} />}
          {page === "bookings" && role === "admin" && <AdminBookings />}
          {page === "providers" && role === "admin" && <AdminProviders setBusy={setBusy} setNotice={setNotice} />}
          {page === "users" && role === "admin" && <AdminUsers setBusy={setBusy} setNotice={setNotice} />}
          {page === "reports" && role === "admin" && <AdminReports />}
          {page === "overview" && role === "provider" && <ProviderOverview uid={session.uid} setPage={setPage} />}
          {page === "orders" && role === "provider" && <ProviderOrders uid={session.uid}
            setNotice={setNotice} setBusy={setBusy} targetBookingId={targetBookingId}
            onTargetResolved={setTargetBookingId} />}
          {page === "customers" && role === "provider" && <ProviderCustomers uid={session.uid} />}
          {page === "inventory" && role === "provider" && <ProviderInventory uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "pos" && role === "provider" && <ProviderPos uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "staff" && role === "provider" && <ProviderStaff uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "reports" && role === "provider" && <ProviderReports uid={session.uid} />}
          {page === "riders" && role === "provider" && <ProviderRiders uid={session.uid} setNotice={setNotice} />}
          {page === "services" && role === "provider" && <ProviderServices uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "profile" && role === "provider" && <ProviderProfile uid={session.uid} setNotice={setNotice} setBusy={setBusy} theme={theme} setTheme={setTheme} />}
          {page === "payouts" && role === "provider" && <ProviderPayouts uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "messages" && role === "provider" && <ProviderMessages uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
        </div>
      </main>
      {busy && <div className="busy-overlay" role="status" aria-live="polite">
        <div className="spinner" aria-hidden="true" /><span>Saving changes…</span></div>}
    </div>
  );
}

function ProfilePhotoControl({ uid, email, role, authPhotoUrl, setNotice, setBusy }) {
  const [savedUrl, setSavedUrl] = useState("");
  const [gravatarUrl, setGravatarUrl] = useState("");
  const [sourceIndex, setSourceIndex] = useState(0);
  const providerProfile = role === "provider";
  const profileRef = doc(db, providerProfile ? "providers" : "users", uid);
  const imageSources = [...new Set([savedUrl, authPhotoUrl, gravatarUrl].filter(Boolean))];
  const source = imageSources[sourceIndex];

  useEffect(() => onSnapshot(profileRef, (snapshot) => {
    const profile = snapshot.exists() ? snapshot.data() : {};
    setSavedUrl(providerProfile
      ? profile.profilePhotoUrl || ""
      : profile.photoUrl || profile.photoUri || "");
  }, (error) => setNotice(error.message || "Could not load your profile picture.")),
  [uid, role]);

  useEffect(() => {
    let active = true;
    if (!email || !window.crypto?.subtle) {
      setGravatarUrl("");
      return () => { active = false; };
    }
    window.crypto.subtle.digest("SHA-256", new TextEncoder().encode(email.trim().toLowerCase()))
      .then((digest) => {
        const hash = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
        if (active) setGravatarUrl(`https://gravatar.com/avatar/${hash}?d=identicon&s=256`);
      })
      .catch((error) => setNotice(error.message || "Could not create your email avatar."));
    return () => { active = false; };
  }, [email]);

  useEffect(() => setSourceIndex(0), [savedUrl, authPhotoUrl, gravatarUrl]);

  async function choosePhoto(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!["image/jpeg", "image/png", "image/webp", "image/gif"].includes(file.type)
        || file.size >= 5 * 1024 * 1024) {
      setNotice("Choose an image smaller than 5 MB.");
      return;
    }
    setBusy(true);
    try {
      const photoRef = ref(storage, `profilePictures/${uid}/avatar`);
      await uploadBytes(photoRef, file, { contentType: file.type });
      const downloadUrl = await getDownloadURL(photoRef);
      await updateDoc(profileRef, providerProfile
        ? { profilePhotoUrl: downloadUrl }
        : { photoUrl: downloadUrl, photoUri: downloadUrl });
      if (auth.currentUser?.uid === uid) {
        await updateProfile(auth.currentUser, { photoURL: downloadUrl });
      }
      setSavedUrl(downloadUrl);
      setSourceIndex(0);
      setNotice("Profile picture updated.");
    } catch (error) {
      setNotice(error.message || "Could not update your profile picture.");
    } finally {
      setBusy(false);
    }
  }

  return <label className="avatar-control" title="Edit profile picture">
    <input className="avatar-file-input" type="file" accept="image/jpeg,image/png,image/webp,image/gif"
      onChange={choosePhoto} aria-label="Choose profile picture" />
    {source ? <img className="avatar-image" src={source} alt="" onError={() => setSourceIndex((index) =>
      Math.min(index + 1, imageSources.length))} /> : <span className="avatar-initial">{(email || "W")[0].toUpperCase()}</span>}
    <span className="avatar-edit-mark" aria-hidden="true">+</span>
  </label>;
}

function ProviderNotifications({ uid, setNotice, setPage, setTargetBookingId }) {
  const [open, setOpen] = useState(false);
  const [state, setState] = useState({ data: [], loading: true, error: "" });
  const menuRef = useRef(null);

  useEffect(() => onSnapshot(collection(db, "users", uid, "notifications"),
  (snapshot) => setState({
    data: snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() }))
      .sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)),
    loading: false,
    error: "",
  }), (error) => setState({
    data: [], loading: false, error: error.message || "Could not load notifications.",
  })), [uid]);

  useEffect(() => {
    if (!open) return undefined;
    function closeOnEscape(event) {
      if (event.key === "Escape") setOpen(false);
    }
    function closeOnOutsideClick(event) {
      if (event.target instanceof Node && !menuRef.current?.contains(event.target)) {
        setOpen(false);
      }
    }
    window.addEventListener("keydown", closeOnEscape);
    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => {
      window.removeEventListener("keydown", closeOnEscape);
      document.removeEventListener("pointerdown", closeOnOutsideClick);
    };
  }, [open]);

  const unread = state.data.filter((notification) => notification.read !== true).length;
  const notificationLimit = 100;
  const visibleNotifications = state.data.slice(0, notificationLimit);
  const notificationsLabel = state.loading
    ? "Notifications, loading unread count"
    : state.error ? "Notifications, unread count unavailable"
      : `Notifications${unread ? `, ${unread} unread` : ""}`;
  async function markRead(notification) {
    if (notification.read === true) return;
    try {
      await updateDoc(doc(db, "users", uid, "notifications", notification.id), { read: true });
    } catch (error) {
      setNotice(error.message || "Could not update notification.");
    }
  }
  function openNotification(notification) {
    if (notification.read !== true) markRead(notification);
    if (!notification.bookingId) return;
    setTargetBookingId(notification.bookingId);
    setPage("orders");
    setOpen(false);
  }

  return <div className="notification-menu" ref={menuRef}>
    <button className="notification-toggle" aria-label={notificationsLabel}
      aria-expanded={open} onClick={() => setOpen((value) => !value)}>
      <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
      {!state.loading && !state.error && unread > 0
        && <span className="notification-count">{unread > 99 ? "99+" : unread}</span>}
    </button>
    {open && <section className="notification-popover" aria-label="Notifications">
      <div className="notification-heading"><strong>Notifications</strong><span>
        {state.loading ? "Loading…" : state.error ? "Count unavailable" : `${unread} unread`}
      </span></div>
      {state.loading ? <Loading /> : state.error ? <ErrorState message={state.error} /> :
        state.data.length === 0 ? <Empty text="No notifications yet." /> :
          <div className="notification-list">{visibleNotifications.map((notification) => <button
            key={notification.id} className={notification.read === true ? "notification-item read" : "notification-item"}
            aria-label={`${notification.read === true ? "Read" : "Unread"} notification: ${notification.title || "Update"}`}
            onClick={() => openNotification(notification)}>
            <strong>{notification.title || "Update"}</strong>
            <span>{notification.body || "You have a new update."}</span>
            <small>{shortDate(notification.createdAt)}</small>
          </button>)}
            {state.data.length > notificationLimit && <p className="muted">
              Showing the latest {notificationLimit} notifications.
            </p>}
          </div>}
    </section>}
  </div>;
}

function Setup() {
  return <div className="center-page"><section className="setup-card">
    <Brand />
    <h1>Connect your Firebase web app</h1>
    <p>Register a Web app in Firebase Console, copy its config values into <code>web/.env.local</code> using <code>web/.env.example</code>, then restart the development server.</p>
    <code className="setup-path">Firebase Console → Project settings → Your apps → Add app → Web</code>
  </section></div>;
}

function Login({ notice, onNotice }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(notice);

  useEffect(() => setError(notice), [notice]);

  async function submit(event) {
    event.preventDefault();
    setError("");
    onNotice("");
    setLoading(true);
    try {
      await signInWithEmailAndPassword(auth, email.trim(), password);
    } catch (failure) {
      setError(failure.message || "Could not sign in.");
    } finally {
      setLoading(false);
    }
  }

  async function resetPassword() {
    if (!email.trim()) {
      setError("Enter your email address first to receive a reset link.");
      return;
    }
    try {
      await sendPasswordResetEmail(auth, email.trim());
      setError("Password reset email sent. Check your inbox.");
    } catch (failure) {
      setError(failure.message || "Could not send password reset email.");
    }
  }

  return <div className="login-layout">
    <div className="login-story"><Brand light />
      <div className="story-copy"><span className="eyebrow">PROVIDER & ADMIN WORKSPACE</span><h1>Run your laundry operations with clarity.</h1><p>Manage orders, customers, riders and business performance in one place.</p></div>
      <div className="story-foot">Secure access for approved team accounts</div>
    </div>
    <div className="login-side"><form className="login-card" onSubmit={submit}>
      <span className="eyebrow blue">WELCOME BACK</span><h2>Sign in to WashLink</h2>
      <p className="muted">Use your existing provider or administrator account.</p>
      {error && <div className={error.includes("sent.") ? "notice success" : "form-error"}
        role={error.includes("sent.") ? "status" : "alert"} aria-live="polite">{error}</div>}
      <label>Email address<input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" /></label>
      <label>Password<input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Enter your password" /></label>
      <button className="text-button reset-link" type="button" onClick={resetPassword}>Forgot password?</button>
      <button className="primary full" disabled={loading}>{loading ? "Signing in…" : "Sign in"}</button>
      <div className="login-note">Customer accounts should continue to use the WashLink mobile app.</div>
    </form></div>
  </div>;
}

function PageHeading({ eyebrow, title, description, action }) {
  return <div className="page-heading"><div><span className="eyebrow blue">{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>{action}</div>;
}

function StatCard({ label, value, note, accent = "" }) {
  return <article className={`stat-card ${accent}`}><div className="stat-label">{label}</div><div className="stat-value">{value}</div><div className="stat-note">{note}</div></article>;
}

function useCollection(load, dependencies) {
  const [state, setState] = useState({ data: [], loading: true, error: "" });
  const [refreshKey, setRefreshKey] = useState(0);
  useEffect(() => {
    let active = true;
    setState((current) => ({ ...current, loading: true, error: "" }));
    Promise.resolve()
      .then(load)
      .then((data) => {
        if (active) setState({ data, loading: false, error: "" });
      })
      .catch((error) => {
        if (active) setState({
          data: [], loading: false, error: error.message || "Could not load data.",
        });
      });
    return () => { active = false; };
  // The caller supplies the query dependencies; refreshKey also reruns that query.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencies, refreshKey]);
  return {
    ...state,
    reload: () => setRefreshKey((key) => key + 1),
  };
}

function useProviderBookings(uid) {
  const [state, setState] = useState({ data: [], loading: true, error: "" });
  const [refreshKey, setRefreshKey] = useState(0);
  useEffect(() => {
    setState((current) => ({ ...current, loading: true, error: "" }));
    return onSnapshot(query(
      collection(db, "bookings"),
      where("providerId", "==", uid),
      orderBy("createdAt", "desc"),
    ), (snapshot) => setState({
      data: snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() })),
      loading: false,
      error: "",
    }), (error) => setState({
      data: [], loading: false, error: error.message || "Could not load bookings.",
    }));
  }, [uid, refreshKey]);
  return {
    ...state,
    reload: () => setRefreshKey((key) => key + 1),
  };
}

function useProviderProfile(uid) {
  const [state, setState] = useState({ data: null, loading: true, error: "" });
  const [refreshKey, setRefreshKey] = useState(0);
  useEffect(() => {
    setState({ data: null, loading: true, error: "" });
    return onSnapshot(doc(db, "providers", uid), (snapshot) => setState({
      data: snapshot.exists() ? snapshot.data() : null,
      loading: false,
      error: "",
    }), (error) => setState({
      data: null, loading: false, error: error.message || "Could not load provider profile.",
    }));
  }, [uid, refreshKey]);
  return {
    ...state,
    reload: () => setRefreshKey((key) => key + 1),
  };
}

function useProviderRiders(uid) {
  const [state, setState] = useState({ data: [], loading: true, error: "" });
  const [refreshKey, setRefreshKey] = useState(0);
  useEffect(() => {
    setState((current) => ({ ...current, loading: true, error: "" }));
    return onSnapshot(collection(db, "providers", uid, "riders"), (snapshot) => setState({
      data: snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() })),
      loading: false,
      error: "",
    }), (error) => setState({
      data: [], loading: false, error: error.message || "Could not load riders.",
    }));
  }, [uid, refreshKey]);
  return {
    ...state,
    reload: () => setRefreshKey((key) => key + 1),
  };
}

function useProviderPayout(uid) {
  const [state, setState] = useState({ data: null, loading: true, error: "" });
  useEffect(() => {
    setState({ data: null, loading: true, error: "" });
    return onSnapshot(doc(db, "users", uid, "payoutProfile", "default"),
      (snapshot) => setState({
        data: snapshot.exists() ? snapshot.data() : null,
        loading: false,
        error: "",
      }),
      (error) => setState({
        data: null, loading: false, error: error.message || "Could not load payout settings.",
      }));
  }, [uid]);
  return state;
}

function AdminOverview({ setPage }) {
  const [dateRange, setDateRange] = useState("30");
  const users = useCollection(async () => (await getDocs(query(collection(db, "users"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const providers = useCollection(async () => (await getDocs(query(collection(db, "providers"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), orderBy("createdAt", "desc"), limit(1000))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)), []);
  const days = Number(dateRange);
  const rangeEnd = Date.now();
  const rangeStart = rangeEnd - days * 86400000;
  const periodBookings = bookings.data.filter((booking) => {
    const createdAt = timestampMillis(booking.createdAt);
    return createdAt >= rangeStart && createdAt <= rangeEnd;
  });
  const inProgress = periodBookings.filter((booking) =>
    !["DELIVERED", "CANCELLED", "REJECTED"].includes(booking.status));
  const waitingProviders = providers.data.filter((provider) => provider.isApproved !== true);
  const collected = periodBookings.reduce((sum, booking) => sum + verifiedRevenue(booking), 0);
  const activeCustomers = new Set(periodBookings.map((booking) => booking.customerId).filter(Boolean)).size;
  const activeProviders = new Set(periodBookings.map((booking) => booking.providerId).filter(Boolean)).size;
  const topCustomers = aggregateDashboardPeople(periodBookings, "customerId", "customerName");
  const topProviders = aggregateDashboardPeople(periodBookings, "providerId", "providerName");
  const statusCounts = periodBookings.reduce((counts, booking) => {
    const key = booking.status || "UNKNOWN";
    counts[key] = (counts[key] || 0) + 1;
    return counts;
  }, {});
  const statusEntries = Object.entries(statusCounts).sort((a, b) => b[1] - a[1]).slice(0, 5);
  const maxStatusCount = Math.max(1, ...statusEntries.map(([, count]) => count));
  const chartBuckets = Array.from({ length: 7 }, (_, index) => ({
    label: dashboardBucketLabel(rangeStart, rangeEnd, index, days),
    orders: 0,
    pending: 0,
    revenue: 0,
  }));
  periodBookings.forEach((booking) => {
    const ratio = (timestampMillis(booking.createdAt) - rangeStart) / (rangeEnd - rangeStart);
    const index = Math.min(6, Math.max(0, Math.floor(ratio * 7)));
    chartBuckets[index].orders++;
    if (booking.status === "BOOKED") chartBuckets[index].pending++;
    chartBuckets[index].revenue += verifiedRevenue(booking);
  });
  const maxOrders = Math.max(1, ...chartBuckets.map((bucket) => bucket.orders));
  const maxRevenue = Math.max(1, ...chartBuckets.map((bucket) => bucket.revenue));
  const revenuePoints = chartBuckets.map((bucket, index) =>
    `${30 + index * 90},${126 - (bucket.revenue / maxRevenue) * 105}`).join(" ");
  return <>
    <PageHeading eyebrow="ADMINISTRATION" title="Operations overview" description="A current snapshot of WashLink activity."
      action={<div className="heading-actions"><label className="dashboard-range"><span>Reporting period</span>
        <select value={dateRange} onChange={(event) => setDateRange(event.target.value)}>
          <option value="7">Last 7 days</option><option value="30">Last 30 days</option><option value="90">Last 90 days</option>
        </select></label><button className="secondary" onClick={() => {
          users.reload(); providers.reload(); bookings.reload();
        }}>Refresh data</button></div>} />
    {(users.loading || providers.loading || bookings.loading) ? <DashboardLoading />
      : users.error || providers.error || bookings.error
        ? <DashboardError message={users.error || providers.error || bookings.error}
          onRetry={() => { users.reload(); providers.reload(); bookings.reload(); }} /> : <>
      <div className="stats-grid admin-dashboard-stats">
        <StatCard label="Collected revenue" value={money(collected)} note={`Verified revenue · last ${days} days`} accent="accent-green" />
        <StatCard label="Total orders" value={periodBookings.length} note={`Bookings · last ${days} days`} />
        <StatCard label="In progress" value={inProgress.length} note="Open orders in this period" accent="accent-blue" />
        <StatCard label="Completed orders" value={periodBookings.filter((booking) => booking.status === "DELIVERED").length}
          note={`${activeCustomers} customers · ${activeProviders} providers`} accent="accent-amber" />
      </div>
      <div className="admin-dashboard-layout">
        <div className="admin-dashboard-main">
          <section className="panel admin-dashboard-chart-panel">
            <div className="panel-heading"><div><h2>Order activity</h2><p>Orders created in the selected reporting period</p></div>
              <span className="dashboard-period-label">Last {days} days</span></div>
            <div className="dashboard-chart-legend"><span><i className="legend-orders" />Orders</span><span><i className="legend-pending" />Awaiting response</span></div>
            {chartBuckets.some((bucket) => bucket.orders > 0)
              ? <div className="dashboard-bar-chart" role="img" aria-label={`Order activity for the last ${days} days`}>
              {chartBuckets.map((bucket, index) => {
                const orderHeight = bucket.orders ? Math.max(5, bucket.orders / maxOrders * 100) : 2;
                const pendingHeight = bucket.orders ? orderHeight * bucket.pending / bucket.orders : 0;
                return <div className="dashboard-bar-column" key={`${bucket.label}-${index}`}>
                  <span className="dashboard-bar-value">{bucket.orders}</span>
                  <div className="dashboard-bar-track"><div className="dashboard-order-bar" style={{ height: `${orderHeight}%` }}>
                    <span className="dashboard-pending-bar" style={{ height: `${pendingHeight}%` }} />
                  </div></div>
                  <span className="dashboard-bar-label">{bucket.label}</span>
                </div>;
              })}
              </div> : <Empty text={`No orders in the last ${days} days.`} />}
            <div className="dashboard-chart-footer"><span>{periodBookings.length} orders in this period</span>
              <button className="text-button" onClick={() => setPage("bookings")}>View orders →</button></div>
          </section>
          <section className="panel admin-dashboard-chart-panel">
            <div className="panel-heading"><div><h2>Verified revenue</h2><p>Collected cash and paid online orders</p></div>
              <strong className="dashboard-chart-total">{money(collected)}</strong></div>
            {chartBuckets.some((bucket) => bucket.revenue > 0)
              ? <div className="dashboard-line-chart" role="img" aria-label={`Verified revenue by time bucket, last ${days} days`}>
              <svg viewBox="0 0 600 150" preserveAspectRatio="none" aria-hidden="true">
                <line x1="30" y1="126" x2="570" y2="126" />
                <line x1="30" y1="74" x2="570" y2="74" />
                <line x1="30" y1="22" x2="570" y2="22" />
                <polyline points={revenuePoints} />
              </svg>
              <div className="dashboard-line-labels">{chartBuckets.map((bucket, index) =>
                <span key={`${bucket.label}-${index}`}>{bucket.label}</span>)}</div>
              </div> : <Empty text="No verified revenue in this period." />}
          </section>
          <section className="panel admin-dashboard-status-panel">
            <div className="panel-heading"><div><h2>Order status</h2><p>Lifecycle breakdown for this period</p></div></div>
            {statusEntries.length === 0 ? <Empty text="No bookings in this period." /> : <div className="dashboard-status-list">
              {statusEntries.map(([statusName, count]) => <div className="dashboard-status-row" key={statusName}>
                <span className={`badge ${statusName.toLowerCase()}`}>{statusLabel(statusName)}</span>
                <div className="dashboard-status-track"><span className={`status-fill ${statusName.toLowerCase()}`}
                  style={{ width: `${count / maxStatusCount * 100}%` }} /></div><strong>{count}</strong>
              </div>)}
            </div>}
          </section>
        </div>
        <aside className="admin-dashboard-side">
          <DashboardRanking title="Top customers" subtitle="By order volume" entries={topCustomers}
            empty="No customers have booked in this period." action={() => setPage("users")} actionLabel="View users" />
          <DashboardRanking title="Top providers" subtitle="By booking volume" entries={topProviders}
            empty="No providers have received bookings in this period." action={() => setPage("providers")} actionLabel="View providers" />
          {waitingProviders.length > 0 && <div className="notice warn"><span>{waitingProviders.length} provider account{waitingProviders.length === 1 ? "" : "s"} need approval.</span>
            <button className="text-button" onClick={() => setPage("providers")}>Review providers →</button></div>}
        </aside>
      </div>
      <section className="panel admin-dashboard-recent">
        <div className="panel-heading"><div><h2>Recent orders</h2><p>Latest bookings across the marketplace</p></div>
          <button className="text-button" onClick={() => setPage("bookings")}>View all →</button></div>
        <BookingTable bookings={bookings.data.slice(0, 8)} admin />
      </section>
    </>}
  </>;
}

function DashboardRanking({ title, subtitle, entries, empty, action, actionLabel }) {
  return <section className="panel dashboard-ranking">
    <div className="panel-heading"><div><h2>{title}</h2><p>{subtitle}</p></div>
      <button className="text-button" onClick={action}>{actionLabel} →</button></div>
    {entries.length === 0 ? <Empty text={empty} /> : <div className="dashboard-ranking-list">
      {entries.slice(0, 5).map((entry, index) => <div className="dashboard-ranking-row" key={entry.id || entry.name}>
        <span className="dashboard-rank">{index + 1}</span><div><strong>{entry.name}</strong>
          <span>{entry.orders} {entry.orders === 1 ? "order" : "orders"}</span></div>
        <strong className="dashboard-ranking-revenue">{money(entry.revenue)}</strong>
      </div>)}
    </div>}
  </section>;
}

function DashboardLoading() {
  return <div className="dashboard-loading" role="status" aria-label="Loading dashboard">
    <div className="stats-grid">{Array.from({ length: 4 }, (_, index) =>
      <div className="dashboard-skeleton dashboard-skeleton-stat" key={index} />)}</div>
    <div className="dashboard-skeleton dashboard-skeleton-panel" />
    <div className="dashboard-skeleton dashboard-skeleton-panel dashboard-skeleton-short" />
  </div>;
}

function DashboardError({ message, onRetry }) {
  return <section className="panel dashboard-error" role="alert">
    <div><strong>Dashboard data could not be loaded</strong><p>{message}</p></div>
    <button className="secondary" onClick={onRetry}>Retry</button>
  </section>;
}

function aggregateDashboardPeople(bookings, idField, nameField) {
  const totals = new Map();
  bookings.forEach((booking) => {
    const id = booking[idField] || booking[nameField] || "unknown";
    const current = totals.get(id) || {
      id, name: booking[nameField] || "Unknown", orders: 0, revenue: 0,
    };
    current.orders++;
    current.revenue += verifiedRevenue(booking);
    totals.set(id, current);
  });
  return [...totals.values()].sort((a, b) =>
    b.orders - a.orders || b.revenue - a.revenue || a.name.localeCompare(b.name));
}

function dashboardBucketLabel(start, end, index, days) {
  const date = new Date(start + ((end - start) / 7) * (index + 0.5));
  return date.toLocaleDateString("en-UG", days <= 7
    ? { weekday: "short" } : { day: "numeric", month: "short" });
}

function AdminBookings() {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("ALL");
  const [view, setView] = useState("list");
  const [sort, setSort] = useState("newest");
  const [dateFilter, setDateFilter] = useState("ALL");
  const [pageSize, setPageSize] = useState(10);
  const [page, setPage] = useState(0);
  const [selectedBooking, setSelectedBooking] = useState(null);
  const data = useCollection(async () => (await getDocs(query(collection(db, "bookings"), orderBy("createdAt", "desc"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)), []);
  const providers = useCollection(async () => (await getDocs(query(collection(db, "providers"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const filtered = data.data.filter((booking) => (status === "ALL" || booking.status === status)
    && (dateFilter === "ALL" || (dateFilter === "TODAY"
      ? isSameLocalDay(booking.createdAt, Date.now())
      : dateFilter === "7D" ? timestampMillis(booking.createdAt) >= Date.now() - 7 * 86400000
        : timestampMillis(booking.createdAt) >= Date.now() - 30 * 86400000))
    && [booking.id, booking.customerName, booking.providerName, booking.serviceName, booking.status,
      booking.customerPhone, booking.paymentMethod, booking.paymentProvider].some((value) => matchesSearch(value, search)))
    .sort((a, b) => sort === "oldest"
      ? timestampMillis(a.createdAt) - timestampMillis(b.createdAt)
      : timestampMillis(b.createdAt) - timestampMillis(a.createdAt));
  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, pageCount - 1);
  const visibleBookings = filtered.slice(currentPage * pageSize, (currentPage + 1) * pageSize);
  const selectedProvider = selectedBooking
    ? providers.data.find((provider) => provider.id === selectedBooking.providerId)
    : null;
  useEffect(() => { setPage(0); }, [search, status, dateFilter, sort, pageSize]);
  useEffect(() => {
    if (!selectedBooking) return undefined;
    function closeOnEscape(event) {
      if (event.key === "Escape") setSelectedBooking(null);
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [selectedBooking]);
  const headingAction = <div className="heading-actions"><ExportActions rows={filtered} fileName="Bookings" columns={[
    { label: "Booking", value: (row) => row.id }, { label: "Customer", value: (row) => row.customerName || "" },
    { label: "Provider", value: (row) => row.providerName || "" }, { label: "Service", value: (row) => row.serviceName || "" },
    { label: "Created", value: (row) => shortDate(row.createdAt) }, { label: "Status", value: (row) => statusLabel(row.status) },
    { label: "Payment", value: (row) => `${row.paymentMethod || "—"} · ${row.paymentStatus || "PENDING"}` },
    { label: "Total (UGX)", value: (row) => Number(row.total || 0) },
  ]} /><button className="secondary" onClick={data.reload}>Refresh</button></div>;
  return <><PageHeading eyebrow="MARKETPLACE" title="Bookings" description="Browse customer orders, monitor progress and payment, and review order details." action={headingAction} />
    <div className="stats-grid">
      <StatCard label="Total orders" value={data.data.length} note="Bookings in WashLink" />
      <StatCard label="Needs attention" value={data.data.filter((booking) => booking.status === "BOOKED").length} note="Waiting for provider response" accent="accent-amber" />
      <StatCard label="In progress" value={data.data.filter((booking) => providerOrderGroup(booking.status) === "progress").length} note="Accepted or being processed" accent="accent-blue" />
      <StatCard label="Completed" value={data.data.filter((booking) => booking.status === "DELIVERED").length} note="Delivered or collected" accent="accent-green" />
    </div>
    <section className="panel admin-users-panel">
      <div className="admin-users-toolbar">
        <div className="admin-view-switch" role="group" aria-label="Booking layout">
          <button className={view === "list" ? "selected" : ""} aria-pressed={view === "list"} onClick={() => setView("list")}>☷ List view</button>
          <button className={view === "grid" ? "selected" : ""} aria-pressed={view === "grid"} onClick={() => setView("grid")}>▦ Grid view</button>
        </div>
        <div className="admin-users-filters admin-bookings-filters">
          <label className="search-control"><span>Search orders</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Order, customer, provider, service…" /></label>
          <label className="search-control status-select"><span>Order status</span><select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="ALL">All statuses</option>{Object.keys(STATUS_LABELS).filter((value) => value !== "UNKNOWN")
              .map((value) => <option key={value} value={value}>{statusLabel(value)}</option>)}
          </select></label>
          <label className="search-control status-select"><span>Order date</span><select value={dateFilter} onChange={(event) => setDateFilter(event.target.value)}>
            <option value="ALL">Any date</option><option value="TODAY">Today</option><option value="7D">Last 7 days</option><option value="30D">Last 30 days</option>
          </select></label>
          <label className="search-control status-select admin-user-sort"><span>Sort by</span><select value={sort} onChange={(event) => setSort(event.target.value)}>
            <option value="newest">Newest</option><option value="oldest">Oldest</option>
          </select></label>
        </div>
      </div>
      {data.loading ? <div className="bookings-loading" role="status" aria-label="Loading bookings">
        {Array.from({ length: 4 }, (_, index) => <div className="bookings-skeleton" key={index} />)}
      </div> : data.error ? <div className="bookings-error" role="alert">
        <ErrorState message={data.error} /><button className="secondary" onClick={data.reload}>Retry</button>
      </div>
        : filtered.length === 0 ? <Empty text="No bookings match these filters." />
          : view === "list" ? <div className="table-scroll admin-users-table-scroll"><table className="admin-users-table admin-bookings-table">
            <thead><tr><th scope="col">Order</th><th scope="col">Order date</th><th scope="col">Customer</th><th scope="col">Provider</th><th scope="col">Service</th><th scope="col">Schedule</th><th scope="col">Total</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Details</span></th></tr></thead>
            <tbody>{visibleBookings.map((booking) => <tr key={booking.id} className="admin-user-row"
              tabIndex={0} aria-label={`View booking ${booking.id}`}
              onClick={() => setSelectedBooking(booking)}
              onKeyDown={(event) => {
                if (event.target !== event.currentTarget) return;
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  setSelectedBooking(booking);
                }
              }}>
              <td className="mono">#{booking.id.slice(0, 10)}</td><td>{shortDate(booking.createdAt)}</td>
              <td><strong>{booking.customerName || "Customer"}</strong><span className="sub-cell">{booking.customerPhone || "Phone unavailable"}</span></td>
              <td>{booking.providerName || "Provider"}</td><td>{booking.serviceName || "Laundry service"}</td>
              <td>{booking.scheduledDateTime || "Not scheduled"}</td><td>{money(booking.total)}</td>
              <td><span className={`badge ${String(booking.status || "").toLowerCase()}`}>{statusLabel(booking.status)}</span></td>
              <td><button className="secondary small" onClick={(event) => { event.stopPropagation(); setSelectedBooking(booking); }}>Details</button></td>
            </tr>)}</tbody>
          </table></div>
            : <div className="admin-user-grid">{visibleBookings.map((booking) => <article className="admin-user-card" key={booking.id}
              role="button" tabIndex={0} aria-label={`View booking ${booking.id}`}
              onClick={() => setSelectedBooking(booking)}
              onKeyDown={(event) => { if (event.key === "Enter") setSelectedBooking(booking); }}>
              <div className="admin-user-card-heading"><span className="admin-booking-mark">#{booking.id.slice(0, 5)}</span>
                <div><strong>{booking.customerName || "Customer"}</strong><span>{booking.providerName || "Provider"}</span></div>
                <span className={`badge ${String(booking.status || "").toLowerCase()}`}>{statusLabel(booking.status)}</span></div>
              <div className="admin-user-contact"><span>{booking.serviceName || "Laundry service"} · {fulfillmentLabel(booking.serviceType)}</span>
                <span>{booking.scheduledDateTime || "Not scheduled"}</span><span>{booking.paymentMethod || "Payment not selected"}</span></div>
              <div className="admin-user-card-footer"><span>{money(booking.total)}</span><span>{shortDate(booking.createdAt)}</span></div>
            </article>)}</div>}
      <div className="admin-users-pagination"><label>Rows per page
        <select value={pageSize} onChange={(event) => setPageSize(Number(event.target.value))}>
          {[10, 25, 50].map((size) => <option key={size} value={size}>{size}</option>)}
        </select>
      </label><span>{filtered.length === 0 ? "0" : `${currentPage * pageSize + 1}–${Math.min((currentPage + 1) * pageSize, filtered.length)}`} of {filtered.length}</span>
        <button className="secondary small" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} aria-label="Previous page">‹</button>
        <button className="secondary small" disabled={currentPage >= pageCount - 1} onClick={() => setPage(currentPage + 1)} aria-label="Next page">›</button>
      </div>
    </section>
    {selectedBooking && <div className="admin-user-drawer-backdrop" onClick={() => setSelectedBooking(null)}>
      <aside className="admin-user-drawer" role="dialog" aria-modal="true" aria-labelledby="admin-booking-drawer-title"
        onClick={(event) => event.stopPropagation()}>
        <header className="admin-user-drawer-header"><h2 id="admin-booking-drawer-title">Order details</h2>
          <button className="secondary small" onClick={() => setSelectedBooking(null)} aria-label="Close order details">×</button></header>
        <div className="admin-user-drawer-content">
          <div className="admin-user-profile"><span className="admin-booking-mark large">#{selectedBooking.id.slice(0, 5)}</span>
            <div><strong>{selectedBooking.serviceName || "Laundry service"}</strong><span>Placed {shortDateTime(selectedBooking.createdAt)}</span></div>
            <span className={`badge ${String(selectedBooking.status || "").toLowerCase()}`}>{statusLabel(selectedBooking.status)}</span></div>
          <div className="admin-user-orders-heading"><h3>Customer &amp; provider</h3></div>
          <div className="admin-user-detail-list">
            <AdminUserDetail label="Customer" value={selectedBooking.customerName} />
            <AdminUserDetail label="Customer phone" value={selectedBooking.customerPhone} />
            <AdminUserDetail label="Provider" value={selectedBooking.providerName} />
            <AdminUserDetail label="Provider contact" value={selectedProvider?.phone || selectedProvider?.email} />
            <AdminUserDetail label="Order ID" value={selectedBooking.id} />
          </div>
          <div className="admin-user-orders-heading"><h3>Service &amp; schedule</h3></div>
          <div className="admin-user-detail-list">
            <AdminUserDetail label="Service" value={selectedBooking.serviceName} />
            <AdminUserDetail label="Fulfilment" value={fulfillmentLabel(selectedBooking.serviceType)} />
            <AdminUserDetail label="Items / weight" value={`${selectedBooking.itemCount || 0} items${selectedBooking.weightKg ? ` · ${selectedBooking.weightKg} kg` : ""}`} />
            <AdminUserDetail label="Scheduled" value={selectedBooking.scheduledDateTime} />
            <AdminUserDetail label="Address" value={selectedBooking.address} />
            <AdminUserDetail label="Instructions" value={selectedBooking.specialInstructions} />
          </div>
          <div className="admin-user-orders-heading"><h3>Payment</h3></div>
          <div className="admin-user-detail-list">
            <AdminUserDetail label="Method" value={selectedBooking.paymentMethod} />
            <AdminUserDetail label="Provider" value={selectedBooking.paymentProvider} />
            <AdminUserDetail label="Payment status" value={selectedBooking.paymentStatus} />
            <AdminUserDetail label="Subtotal" value={money(selectedBooking.subtotal)} />
            <AdminUserDetail label="Service fee" value={money(serviceFeeForDisplay(selectedBooking))} />
            <AdminUserDetail label="Total" value={money(selectedBooking.total)} />
          </div>
          <div className="admin-user-orders-heading"><h3>Order history</h3></div>
          <div className="admin-booking-history">
            <div><span className={`history-dot ${String(selectedBooking.status || "").toLowerCase()}`} /><span><strong>{statusLabel(selectedBooking.status)}</strong><small>Last updated {shortDateTime(selectedBooking.updatedAt)}</small></span></div>
            <span>Booking status is managed by the customer and provider.</span>
          </div>
        </div>
      </aside>
    </div>}
  </>;
}

function isSameLocalDay(value, now) {
  const millis = timestampMillis(value);
  if (!millis) return false;
  const date = new Date(millis);
  const today = new Date(now);
  return date.getFullYear() === today.getFullYear()
    && date.getMonth() === today.getMonth()
    && date.getDate() === today.getDate();
}

function AdminReports() {
  const [dateRange, setDateRange] = useState("30");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const bookings = useCollection(async () => (await getDocs(query(
    collection(db, "bookings"), orderBy("createdAt", "desc"), limit(1000),
  ))).docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const providers = useCollection(async () => (await getDocs(query(collection(db, "providers"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const now = Date.now();
  const start = dateRange === "ALL" ? 0 : now - Number(dateRange) * 86400000;
  const periodBookings = bookings.data.filter((booking) => {
    const created = timestampMillis(booking.createdAt);
    return created >= start && created <= now
      && (statusFilter === "ALL" || booking.status === statusFilter);
  });
  const completed = periodBookings.filter((booking) => booking.status === "DELIVERED");
  const verifiedCash = completed.filter((booking) => booking.paymentProvider?.toLowerCase() === "cash")
    .reduce((sum, booking) => sum + Number(booking.total || 0), 0);
  const verifiedOnline = completed.filter((booking) =>
    isOnlinePaymentProvider(booking.paymentProvider)
      && booking.paymentStatus?.toUpperCase() === "PAID")
    .reduce((sum, booking) => sum + Number(booking.subtotal || 0), 0);
  const revenue = verifiedCash + verifiedOnline;
  const providerRows = aggregateDashboardPeople(periodBookings, "providerId", "providerName")
    .map((entry) => {
      const provider = providers.data.find((candidate) => candidate.id === entry.id);
      const ownBookings = periodBookings.filter((booking) => booking.providerId === entry.id);
      return {
        ...entry,
        businessName: provider?.businessName || entry.name,
        completed: ownBookings.filter((booking) => booking.status === "DELIVERED").length,
        pending: ownBookings.filter((booking) => booking.status === "BOOKED").length,
        revenue: ownBookings.reduce((sum, booking) => sum + verifiedRevenue(booking), 0),
      };
    }).sort((a, b) => b.revenue - a.revenue || b.orders - a.orders);
  const exportBookings = periodBookings.map((booking) => ({
    ...booking,
    bookingId: booking.id,
    created: shortDateTime(booking.createdAt),
    displayStatus: statusLabel(booking.status),
    verifiedRevenue: verifiedRevenue(booking),
  }));
  const rangeLabel = dateRange === "ALL" ? "loaded history (up to the latest 1,000 bookings)"
    : `the last ${dateRange} days within the latest 1,000 bookings`;
  return <>
    <PageHeading eyebrow="ADMINISTRATION" title="Platform reports"
      description="Review booking volume, verified collections, payment mix, and provider performance."
      action={<div className="heading-actions">
        <label className="dashboard-range"><span>Reporting period</span><select value={dateRange}
          onChange={(event) => setDateRange(event.target.value)}>
          <option value="7">Last 7 days</option><option value="30">Last 30 days</option>
          <option value="90">Last 90 days</option><option value="ALL">Loaded history (up to 1,000)</option>
        </select></label>
        <ExportActions rows={exportBookings} fileName="WashLink-platform-report" columns={[
          { label: "Booking ID", value: (row) => row.bookingId },
          { label: "Customer", value: (row) => row.customerName || "" },
          { label: "Provider", value: (row) => row.providerName || "" },
          { label: "Service", value: (row) => row.serviceName || "" },
          { label: "Created", value: (row) => row.created },
          { label: "Status", value: (row) => row.displayStatus },
          { label: "Payment method", value: (row) => row.paymentMethod || "" },
          { label: "Payment status", value: (row) => row.paymentStatus || "PENDING" },
          { label: "Order total (UGX)", value: (row) => Number(row.total || 0) },
          { label: "Verified revenue (UGX)", value: (row) => row.verifiedRevenue },
        ]} />
        <button className="secondary" onClick={() => { bookings.reload(); providers.reload(); }}>Refresh</button>
      </div>} />
    {(bookings.loading || providers.loading) ? <DashboardLoading />
      : bookings.error || providers.error
        ? <DashboardError message={bookings.error || providers.error}
          onRetry={() => { bookings.reload(); providers.reload(); }} /> : <>
      <div className="table-controls admin-report-controls">
        <label className="search-control status-select"><span>Order status</span><select value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value)}>
          <option value="ALL">All statuses</option>
          {Object.keys(STATUS_LABELS).filter((key) => key !== "UNKNOWN")
            .map((key) => <option key={key} value={key}>{statusLabel(key)}</option>)}
        </select></label>
        <span className="admin-report-context">Showing {periodBookings.length} bookings for {rangeLabel}.</span>
      </div>
      <div className="stats-grid admin-dashboard-stats">
        <StatCard label="Verified revenue" value={money(revenue)} note="Delivered cash + verified online subtotal" accent="accent-green" />
        <StatCard label="Bookings" value={periodBookings.length} note="Matching period and status" />
        <StatCard label="Completed" value={completed.length} note="Delivered or collected" accent="accent-blue" />
        <StatCard label="Average order" value={periodBookings.length
          ? money(periodBookings.reduce((sum, booking) => sum + Number(booking.total || 0), 0) / periodBookings.length)
          : money(0)} note="Across matching bookings" accent="accent-amber" />
      </div>
      <div className="admin-report-payment-grid">
        <section className="panel">
          <div className="panel-heading"><div><h2>Verified payment collections</h2><p>Revenue only counts completed and verifiably paid orders.</p></div></div>
          <div className="admin-report-payment-row"><span>Cash collected</span><strong>{money(verifiedCash)}</strong></div>
          <div className="admin-report-payment-row"><span>Online payment collected</span><strong>{money(verifiedOnline)}</strong></div>
          <div className="admin-report-payment-row total"><span>Total verified revenue</span><strong>{money(revenue)}</strong></div>
          <p className="admin-report-note">Online collections use the provider subtotal; service fees are excluded from provider revenue.</p>
        </section>
        <section className="panel">
          <div className="panel-heading"><div><h2>Booking outcomes</h2><p>Current status of bookings in this report.</p></div></div>
          <div className="dashboard-status-list">
            {[
              ["BOOKED", periodBookings.filter((booking) => booking.status === "BOOKED").length],
              ["IN_PROGRESS", periodBookings.filter((booking) => providerOrderGroup(booking.status) === "progress").length],
              ["DELIVERED", completed.length],
              ["CLOSED", periodBookings.filter((booking) => ["CANCELLED", "REJECTED"].includes(booking.status)).length],
            ].map(([key, count]) => <div className="dashboard-status-row" key={key}>
              <span className="badge">{key === "IN_PROGRESS" ? "In progress"
                : key === "CLOSED" ? "Cancelled / rejected" : statusLabel(key)}</span>
              <div className="dashboard-status-track"><span className={`status-fill ${String(key).toLowerCase()}`}
                style={{ width: `${periodBookings.length ? count / periodBookings.length * 100 : 0}%` }} /></div>
              <strong>{count}</strong>
            </div>)}
          </div>
        </section>
      </div>
      <section className="panel">
        <div className="panel-heading"><div><h2>Provider performance</h2><p>Booking and verified revenue totals by provider.</p></div>
          <button className="text-button" onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}>Top of report ↑</button></div>
        {providerRows.length === 0 ? <Empty text="No provider bookings match this report." />
          : <div className="table-scroll"><table className="admin-report-table"><thead><tr>
            <th scope="col">Provider</th><th scope="col">Bookings</th><th scope="col">Completed</th><th scope="col">Waiting response</th><th scope="col">Verified revenue</th>
          </tr></thead><tbody>{providerRows.map((provider) => <tr key={provider.id}>
            <td>{provider.businessName}</td><td>{provider.orders}</td><td>{provider.completed}</td>
            <td>{provider.pending}</td><td>{money(provider.revenue)}</td>
          </tr>)}</tbody></table></div>}
      </section>
      <section className="panel">
        <div className="panel-heading"><div><h2>Booking details</h2><p>Up to 1,000 most recent bookings; export the matching set using the controls above.</p></div></div>
        <BookingTable bookings={periodBookings.slice(0, 50)} admin />
        {periodBookings.length > 50 && <p className="admin-report-note">Showing 50 of {periodBookings.length}; the export includes all matching bookings.</p>}
      </section>
    </>}
  </>;
}

function AdminProviders({ setBusy, setNotice }) {
  const [search, setSearch] = useState("");
  const [approvalFilter, setApprovalFilter] = useState("ALL");
  const [accountFilter, setAccountFilter] = useState("ALL");
  const [view, setView] = useState("list");
  const [sort, setSort] = useState("newest");
  const [pageSize, setPageSize] = useState(10);
  const [page, setPage] = useState(0);
  const [selectedProvider, setSelectedProvider] = useState(null);
  const providers = useCollection(async () => (await getDocs(query(collection(db, "providers"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const accounts = useCollection(async () => (await getDocs(query(collection(db, "users"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), limit(1000))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const accountById = new Map(accounts.data.map((account) => [account.id, account]));
  const accountFor = (provider) => accountById.get(provider.id);
  const filteredProviders = providers.data.filter((provider) => [
    provider.businessName, provider.ownerName, provider.email, provider.phone, provider.address,
    accountFor(provider)?.email, accountFor(provider)?.phone,
    provider.isApproved !== true ? "pending" : "approved",
    accountFor(provider)?.isSuspended === true ? "suspended" : "active",
  ].some((value) => matchesSearch(value, search))
    && (approvalFilter === "ALL" || (approvalFilter === "PENDING"
      ? provider.isApproved !== true : provider.isApproved === true))
    && (accountFilter === "ALL" || (accountFilter === "SUSPENDED"
      ? accountFor(provider)?.isSuspended === true
      : accountFor(provider)?.isSuspended !== true)))
    .sort((a, b) => {
      if (sort === "name") return String(a.businessName || "").localeCompare(String(b.businessName || ""));
      if (sort === "oldest") return adminDateMillis(a.createdAt) - adminDateMillis(b.createdAt);
      return adminDateMillis(b.createdAt) - adminDateMillis(a.createdAt);
    });
  const pageCount = Math.max(1, Math.ceil(filteredProviders.length / pageSize));
  const currentPage = Math.min(page, pageCount - 1);
  const visibleProviders = filteredProviders.slice(currentPage * pageSize, (currentPage + 1) * pageSize);
  const selectedBookings = selectedProvider
    ? bookings.data.filter((booking) => booking.providerId === selectedProvider.id)
      .sort((a, b) => adminDateMillis(b.createdAt) - adminDateMillis(a.createdAt))
    : [];
  useEffect(() => { setPage(0); }, [search, approvalFilter, accountFilter, sort, pageSize]);
  useEffect(() => {
    if (!selectedProvider) return undefined;
    function closeOnEscape(event) {
      if (event.key === "Escape") setSelectedProvider(null);
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [selectedProvider]);
  async function approve(provider, value) {
    setBusy(true);
    try {
      await updateDoc(doc(db, "providers", provider.id), { isApproved: value });
      setNotice(value ? "Provider approved." : "Provider approval removed.");
      setSelectedProvider((selected) => selected?.id === provider.id
        ? { ...selected, isApproved: value } : selected);
      await providers.reload();
    } catch (error) { setNotice(error.message || "Could not update provider approval."); }
    finally { setBusy(false); }
  }
  async function suspend(provider, value) {
    const account = accountFor(provider);
    if (!account || account.role !== "provider") {
      setNotice("The provider's linked account could not be verified, so access was not changed.");
      return;
    }
    setBusy(true);
    try {
      await updateDoc(doc(db, "users", account.id), { isSuspended: value });
      setNotice(value ? "Provider account suspended." : "Provider account restored.");
      setSelectedProvider((selected) => selected?.id === provider.id
        ? { ...selected, account: { ...account, isSuspended: value } } : selected);
      await accounts.reload();
    } catch (error) { setNotice(error.message || "Could not update provider account."); }
    finally { setBusy(false); }
  }
  const headingAction = <div className="heading-actions"><ExportActions rows={filteredProviders} fileName="Providers" columns={[
    { label: "Business", value: (row) => row.businessName || "" }, { label: "Owner", value: (row) => row.ownerName || "" },
    { label: "Email", value: (row) => accountFor(row)?.email || row.email || "" },
    { label: "Phone", value: (row) => row.phone || "" }, { label: "Address", value: (row) => row.address || "" },
    { label: "Marketplace approval", value: (row) => row.isApproved === true ? "Approved" : "Pending" },
    { label: "Account access", value: (row) => accountFor(row)?.isSuspended === true ? "Suspended" : "Active" },
  ]} /><button className="secondary" onClick={() => { providers.reload(); accounts.reload(); bookings.reload(); }}>Refresh</button></div>;
  const providerCount = providers.data.length;
  const pendingCount = providers.data.filter((provider) => provider.isApproved !== true).length;
  const approvedCount = providers.data.filter((provider) => provider.isApproved === true).length;
  const suspendedCount = providers.data.filter((provider) => accountFor(provider)?.isSuspended === true).length;
  return <><PageHeading eyebrow="ADMINISTRATION" title="Providers" description="Review provider businesses, marketplace approval, and account access." action={headingAction} />
    <div className="stats-grid">
      <StatCard label="Total providers" value={providerCount} note="Registered business profiles" />
      <StatCard label="Awaiting approval" value={pendingCount} note="Not yet listed in marketplace" accent="accent-amber" />
      <StatCard label="Approved" value={approvedCount} note="Eligible to receive bookings" accent="accent-green" />
      <StatCard label="Suspended accounts" value={suspendedCount} note="Provider sign-in disabled" accent="accent-blue" />
    </div>
    <section className="panel admin-users-panel">
      <div className="admin-users-toolbar">
        <div className="admin-view-switch" role="group" aria-label="Provider layout">
          <button className={view === "list" ? "selected" : ""} onClick={() => setView("list")}>☷ List view</button>
          <button className={view === "grid" ? "selected" : ""} onClick={() => setView("grid")}>▦ Grid view</button>
        </div>
        <div className="admin-users-filters">
          <label className="search-control"><span>Search providers</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Business, owner, email, phone…" /></label>
          <label className="search-control status-select"><span>Marketplace approval</span><select value={approvalFilter} onChange={(event) => setApprovalFilter(event.target.value)}>
        <option value="ALL">All providers</option><option value="PENDING">Pending</option><option value="APPROVED">Approved</option>
          </select></label>
          <label className="search-control status-select"><span>Account access</span><select value={accountFilter} onChange={(event) => setAccountFilter(event.target.value)}>
            <option value="ALL">All accounts</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option>
          </select></label>
          <label className="search-control status-select admin-user-sort"><span>Sort by</span><select value={sort} onChange={(event) => setSort(event.target.value)}>
            <option value="newest">Newest</option><option value="oldest">Oldest</option><option value="name">Business name</option>
          </select></label>
        </div>
      </div>
      {providers.loading || accounts.loading ? <div className="providers-loading" role="status" aria-label="Loading providers">
        {Array.from({ length: 4 }, (_, index) => <div className="bookings-skeleton" key={index} />)}
      </div> : providers.error || accounts.error
        ? <div className="providers-error" role="alert"><ErrorState message={providers.error || accounts.error} />
          <button className="secondary" onClick={() => { providers.reload(); accounts.reload(); }}>Retry</button>
        </div>
        : providers.data.length === 0 ? <Empty text="No provider profiles found." />
          : filteredProviders.length === 0 ? <Empty text="No providers match these filters." />
            : view === "list" ? <div className="table-scroll admin-users-table-scroll"><table className="admin-users-table admin-providers-table">
              <thead><tr><th scope="col">Business</th><th scope="col">Owner</th><th scope="col">Mobile</th><th scope="col">Email</th><th scope="col">Bookings</th><th scope="col">Approval</th><th scope="col">Account access</th><th scope="col"><span className="sr-only">Details</span></th></tr></thead>
              <tbody>{visibleProviders.map((provider) => {
                const account = accountFor(provider);
                const providerBookings = bookings.data.filter((booking) => booking.providerId === provider.id);
                const selected = { ...provider, account };
                return <tr key={provider.id} className="admin-user-row" tabIndex={0}
                  aria-label={`View ${provider.businessName || "provider"} details`}
                  onClick={() => setSelectedProvider(selected)}
                  onKeyDown={(event) => {
                    if (event.target !== event.currentTarget) return;
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      setSelectedProvider(selected);
                    }
                  }}>
                  <td><div className="admin-user-identity"><UserAvatar user={{ name: provider.businessName, photoUrl: provider.profilePhotoUrl }} /><strong>{provider.businessName || "Laundry provider"}</strong></div></td>
                  <td>{provider.ownerName || account?.name || "—"}</td><td>{provider.phone || account?.phone || "—"}</td>
                  <td>{provider.email || account?.email || "—"}</td><td>{providerBookings.length}</td>
                  <td><span className={provider.isApproved === true ? "badge delivered" : "badge pending"}>{provider.isApproved === true ? "Approved" : "Pending"}</span></td>
                  <td><span className={account?.isSuspended === true ? "badge rejected" : account ? "badge delivered" : "badge"}>{account?.isSuspended === true ? "Suspended" : account ? "Active" : "Unavailable"}</span></td>
                  <td><button className="secondary small" onClick={(event) => { event.stopPropagation(); setSelectedProvider(selected); }}>Details</button></td>
                </tr>;
              })}</tbody>
            </table></div>
              : <div className="admin-user-grid">{visibleProviders.map((provider) => {
                const account = accountFor(provider);
                const providerBookings = bookings.data.filter((booking) => booking.providerId === provider.id);
                const selected = { ...provider, account };
                return <article className="admin-user-card" key={provider.id} role="button" tabIndex={0}
                  aria-label={`View ${provider.businessName || "provider"} details`}
                  onClick={() => setSelectedProvider(selected)}
                  onKeyDown={(event) => { if (event.key === "Enter") setSelectedProvider(selected); }}>
                  <div className="admin-user-card-heading"><UserAvatar user={{ name: provider.businessName, photoUrl: provider.profilePhotoUrl }} />
                    <div><strong>{provider.businessName || "Laundry provider"}</strong><span>{provider.ownerName || account?.name || "Owner unavailable"}</span></div>
                    <span className={provider.isApproved === true ? "badge delivered" : "badge pending"}>{provider.isApproved === true ? "Approved" : "Pending"}</span></div>
                  <div className="admin-user-contact"><span>{provider.email || account?.email || "No email"}</span>
                    <span>{provider.phone || account?.phone || "No phone"}</span><span>{provider.address || "Address not set"}</span></div>
                  <div className="admin-user-card-footer"><span>{providerBookings.length} bookings</span>
                    <span>{account?.isSuspended === true ? "Suspended" : account ? "Account active" : "Account unavailable"}</span></div>
                </article>;
              })}</div>}
      <div className="admin-users-pagination"><label>Rows per page
        <select value={pageSize} onChange={(event) => setPageSize(Number(event.target.value))}>
          {[10, 25, 50].map((size) => <option key={size} value={size}>{size}</option>)}
        </select>
      </label><span>{filteredProviders.length === 0 ? "0" : `${currentPage * pageSize + 1}–${Math.min((currentPage + 1) * pageSize, filteredProviders.length)}`} of {filteredProviders.length}</span>
        <button className="secondary small" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} aria-label="Previous page">‹</button>
        <button className="secondary small" disabled={currentPage >= pageCount - 1} onClick={() => setPage(currentPage + 1)} aria-label="Next page">›</button>
      </div>
    </section>
    {selectedProvider && <div className="admin-user-drawer-backdrop" onClick={() => setSelectedProvider(null)}>
      <aside className="admin-user-drawer" role="dialog" aria-modal="true" aria-labelledby="admin-provider-drawer-title"
        onClick={(event) => event.stopPropagation()}>
        <header className="admin-user-drawer-header"><h2 id="admin-provider-drawer-title">Provider details</h2>
          <button className="secondary small" onClick={() => setSelectedProvider(null)} aria-label="Close provider details">×</button></header>
        <div className="admin-user-drawer-content">
          <div className="admin-user-profile"><UserAvatar user={{ name: selectedProvider.businessName, photoUrl: selectedProvider.profilePhotoUrl }} large />
            <div><strong>{selectedProvider.businessName || "Laundry provider"}</strong><span>Registered {adminDateLabel(selectedProvider.createdAt)}</span></div>
            <span className={selectedProvider.isApproved === true ? "badge delivered" : "badge pending"}>
              {selectedProvider.isApproved === true ? "Approved" : "Pending approval"}</span></div>
          <div className="admin-user-detail-list">
            <AdminUserDetail label="Owner" value={selectedProvider.ownerName || selectedProvider.account?.name} />
            <AdminUserDetail label="Email" value={selectedProvider.email || selectedProvider.account?.email} />
            <AdminUserDetail label="Phone" value={selectedProvider.phone || selectedProvider.account?.phone} />
            <AdminUserDetail label="Address" value={selectedProvider.address} />
            <AdminUserDetail label="Services" value={Array.isArray(selectedProvider.services)
              ? selectedProvider.services.map((service) => service.name).filter(Boolean).join(", ") : null} />
            <AdminUserDetail label="Booking modes" value={[
              selectedProvider.supportsPickupDelivery !== false ? "Pickup and delivery" : null,
              selectedProvider.supportsDropoff !== false ? "Customer drop-off" : null,
            ].filter(Boolean).join(", ")} />
            <AdminUserDetail label="Rating" value={Number(selectedProvider.reviewCount || 0) > 0
              ? `${Number(selectedProvider.rating || 0).toFixed(1)} (${selectedProvider.reviewCount} reviews)` : "No reviews"} />
            <AdminUserDetail label="Account access" value={!selectedProvider.account ? "Linked account unavailable"
              : selectedProvider.account.isSuspended === true ? "Suspended" : "Active"} />
            <AdminUserDetail label="Provider ID" value={selectedProvider.id} />
          </div>
          <div className="admin-provider-actions">
            <button className={selectedProvider.isApproved === true ? "secondary" : "primary"}
              onClick={() => approve(selectedProvider, selectedProvider.isApproved !== true)}>
              {selectedProvider.isApproved === true ? "Remove marketplace approval" : "Approve provider"}
            </button>
            {selectedProvider.account?.role === "provider" && <button
              className={selectedProvider.account.isSuspended === true ? "primary" : "danger"}
              onClick={() => suspend(selectedProvider, selectedProvider.account.isSuspended !== true)}>
              {selectedProvider.account.isSuspended === true ? "Restore provider account" : "Suspend provider account"}
            </button>}
          </div>
          <div className="admin-user-orders-heading"><h3>Bookings</h3><span>{selectedBookings.length} total</span></div>
          {bookings.loading ? <Loading /> : bookings.error
            ? <div className="providers-error"><ErrorState message={bookings.error} />
              <button className="secondary" onClick={bookings.reload}>Retry</button>
            </div>
            : selectedBookings.length === 0 ? <Empty text="No bookings found for this provider." />
              : <div className="admin-user-orders">{selectedBookings.map((booking) => <article className="admin-user-order" key={booking.id}>
                <div><strong>#{booking.id}</strong><span>{booking.customerName || "Customer"} · {booking.serviceName || "Laundry service"}</span></div>
                <div><strong>{money(booking.total)}</strong><span>{statusLabel(booking.status)} · {adminDateLabel(booking.createdAt)}</span></div>
              </article>)}</div>}
        </div>
      </aside>
    </div>}
  </>;
}

function AdminUsers({ setBusy, setNotice }) {
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [accountFilter, setAccountFilter] = useState("ALL");
  const [view, setView] = useState("list");
  const [sort, setSort] = useState("newest");
  const [pageSize, setPageSize] = useState(10);
  const [page, setPage] = useState(0);
  const [selectedUser, setSelectedUser] = useState(null);
  const users = useCollection(async () => (await getDocs(query(collection(db, "users"), limit(500))))
   .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), limit(1000))))
   .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const filteredUsers = users.data.filter((user) => [
   user.name, user.email, user.phone, user.role, user.isSuspended ? "suspended" : "active",
  ].some((value) => matchesSearch(value, search))
   && (roleFilter === "ALL" || user.role === roleFilter)
   && (accountFilter === "ALL" || (accountFilter === "SUSPENDED"
     ? user.isSuspended === true : user.isSuspended !== true)))
   .sort((a, b) => {
     if (sort === "name") return String(a.name || "").localeCompare(String(b.name || ""));
     if (sort === "oldest") return adminDateMillis(a.createdAt) - adminDateMillis(b.createdAt);
     return adminDateMillis(b.createdAt) - adminDateMillis(a.createdAt);
   });
  const pageCount = Math.max(1, Math.ceil(filteredUsers.length / pageSize));
  const currentPage = Math.min(page, pageCount - 1);
  const visibleUsers = filteredUsers.slice(currentPage * pageSize, (currentPage + 1) * pageSize);
  const selectedOrders = selectedUser
   ? bookings.data.filter((booking) => booking.customerId === selectedUser.id)
     .sort((a, b) => adminDateMillis(b.createdAt) - adminDateMillis(a.createdAt))
   : [];
  useEffect(() => { setPage(0); }, [search, roleFilter, accountFilter, sort, pageSize]);
  useEffect(() => {
   if (!selectedUser) return undefined;
   function closeOnEscape(event) {
     if (event.key === "Escape") setSelectedUser(null);
   }
   window.addEventListener("keydown", closeOnEscape);
   return () => window.removeEventListener("keydown", closeOnEscape);
  }, [selectedUser]);
  async function suspend(user, suspended) {
   setBusy(true);
   try {
     await updateDoc(doc(db, "users", user.id), { isSuspended: suspended });
     setNotice(suspended ? "Account suspended." : "Account restored.");
     setSelectedUser((selected) => selected?.id === user.id
       ? { ...selected, isSuspended: suspended } : selected);
     await users.reload();
   } catch (error) { setNotice(error.message || "Could not update account."); }
   finally { setBusy(false); }
  }
  const exportRows = filteredUsers;
  const headingAction = <div className="heading-actions"><ExportActions rows={exportRows} fileName="Users" columns={[
    { label: "Name", value: (row) => row.name || "" }, { label: "Email", value: (row) => row.email || "" },
    { label: "Phone", value: (row) => row.phone || "" }, { label: "Role", value: (row) => row.role || "" },
    { label: "Status", value: (row) => row.isSuspended ? "Suspended" : "Active" },
  ]} /><button className="secondary" onClick={() => { users.reload(); bookings.reload(); }}>Refresh</button></div>;
  return <><PageHeading eyebrow="ADMINISTRATION" title="User accounts" description="Browse customer and provider accounts, review their orders, and manage access." action={headingAction} />
   <div className="stats-grid">
     <StatCard label="Total users" value={users.data.length} note="Registered accounts" />
     <StatCard label="Customers" value={users.data.filter((user) => user.role === "customer").length} note="Customer accounts" accent="accent-blue" />
     <StatCard label="Providers" value={users.data.filter((user) => user.role === "provider").length} note="Laundry businesses" accent="accent-green" />
     <StatCard label="Suspended" value={users.data.filter((user) => user.isSuspended === true).length} note="Accounts with access disabled" accent="accent-amber" />
   </div>
   <section className="panel admin-users-panel">
     <div className="admin-users-toolbar">
       <div className="admin-view-switch" role="group" aria-label="User layout">
         <button className={view === "list" ? "selected" : ""} onClick={() => setView("list")}>☷ List view</button>
         <button className={view === "grid" ? "selected" : ""} onClick={() => setView("grid")}>▦ Grid view</button>
       </div>
       <div className="admin-users-filters">
         <label className="search-control"><span>Search users</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name, email, phone…" /></label>
         <label className="search-control status-select"><span>Role</span><select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}>
       <option value="ALL">All roles</option><option value="customer">Customer</option><option value="provider">Provider</option><option value="admin">Admin</option>
     </select></label>
     <label className="search-control status-select"><span>Account status</span><select value={accountFilter} onChange={(event) => setAccountFilter(event.target.value)}>
       <option value="ALL">All accounts</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option>
     </select></label></div>
       <label className="search-control status-select admin-user-sort"><span>Sort by</span><select value={sort} onChange={(event) => setSort(event.target.value)}>
     <option value="newest">Newest</option><option value="oldest">Oldest</option><option value="name">Name</option>
       </select></label>
     </div>
     {users.loading ? <Loading /> : users.error ? <ErrorState message={users.error} /> : filteredUsers.length === 0
       ? <Empty text="No user accounts match your search." />
       : view === "list" ? <div className="table-scroll admin-users-table-scroll"><table className="admin-users-table">
         <thead><tr><th>User</th><th>Mobile</th><th>Email</th><th>Role</th><th>Orders</th><th>Joined</th><th>Status</th><th><span className="sr-only">Actions</span></th></tr></thead>
         <tbody>{visibleUsers.map((user) => {
           const userOrders = bookings.data.filter((booking) => booking.customerId === user.id);
           return <tr key={user.id} className="admin-user-row" onClick={() => setSelectedUser(user)}>
             <td><div className="admin-user-identity"><UserAvatar user={user} /><strong>{user.name || "Unnamed account"}</strong></div></td>
             <td>{user.phone || "—"}</td><td>{user.email || "—"}</td><td><span className="role-pill">{user.role || "Unknown"}</span></td>
             <td>{userOrders.length}</td><td>{adminDateLabel(user.createdAt)}</td>
             <td><span className={user.isSuspended ? "badge rejected" : "badge delivered"}>{user.isSuspended ? "Suspended" : "Active"}</span></td>
             <td><button className="secondary small" aria-label={`View ${user.name || "user"} details`} onClick={(event) => { event.stopPropagation(); setSelectedUser(user); }}>Details</button></td>
           </tr>;
         })}</tbody>
       </table></div>
         : <div className="admin-user-grid">{visibleUsers.map((user) => <article className="admin-user-card" key={user.id}
           onClick={() => setSelectedUser(user)} onKeyDown={(event) => { if (event.key === "Enter") setSelectedUser(user); }}
           tabIndex={0} role="button" aria-label={`View ${user.name || "user"} details`}>
           <div className="admin-user-card-heading"><UserAvatar user={user} /><div><strong>{user.name || "Unnamed account"}</strong><span>{user.role || "Unknown role"}</span></div>
             <span className={user.isSuspended ? "badge rejected" : "badge delivered"}>{user.isSuspended ? "Suspended" : "Active"}</span></div>
           <div className="admin-user-contact"><span>{user.email || "No email"}</span><span>{user.phone || "No phone"}</span></div>
           <div className="admin-user-card-footer"><span>{bookings.data.filter((booking) => booking.customerId === user.id).length} orders</span><span>Joined {adminDateLabel(user.createdAt)}</span></div>
         </article>)}</div>}
     <div className="admin-users-pagination"><label>Rows per page
       <select value={pageSize} onChange={(event) => setPageSize(Number(event.target.value))}>
         {[10, 25, 50].map((size) => <option key={size} value={size}>{size}</option>)}
       </select>
     </label><span>{filteredUsers.length === 0 ? "0" : `${currentPage * pageSize + 1}–${Math.min((currentPage + 1) * pageSize, filteredUsers.length)}`} of {filteredUsers.length}</span>
       <button className="secondary small" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} aria-label="Previous page">‹</button>
       <button className="secondary small" disabled={currentPage >= pageCount - 1} onClick={() => setPage(currentPage + 1)} aria-label="Next page">›</button>
     </div>
   </section>
   {selectedUser && <div className="admin-user-drawer-backdrop" onClick={() => setSelectedUser(null)}>
     <aside className="admin-user-drawer" role="dialog" aria-modal="true" aria-labelledby="admin-user-drawer-title"
       onClick={(event) => event.stopPropagation()}>
       <header className="admin-user-drawer-header"><h2 id="admin-user-drawer-title">User details</h2>
         <button className="secondary small" onClick={() => setSelectedUser(null)} aria-label="Close user details">×</button></header>
       <div className="admin-user-drawer-content">
         <div className="admin-user-profile"><UserAvatar user={selectedUser} large />
           <div><strong>{selectedUser.name || "Unnamed account"}</strong><span>Joined {adminDateLabel(selectedUser.createdAt)}</span></div>
           <span className={selectedUser.isSuspended ? "badge rejected" : "badge delivered"}>{selectedUser.isSuspended ? "Suspended" : "Active"}</span></div>
         <div className="admin-user-detail-list">
           <AdminUserDetail label="Email" value={selectedUser.email} />
           <AdminUserDetail label="Phone" value={selectedUser.phone} />
           <AdminUserDetail label="Role" value={selectedUser.role} />
           <AdminUserDetail label="Address" value={selectedUser.address || selectedUser.savedAddresses?.[0]} />
           <AdminUserDetail label="Account ID" value={selectedUser.id} />
         </div>
         {selectedUser.role !== "admin" && <button className={selectedUser.isSuspended ? "primary" : "danger"}
           onClick={() => suspend(selectedUser, !selectedUser.isSuspended)}>{selectedUser.isSuspended ? "Restore account" : "Suspend account"}</button>}
         <div className="admin-user-orders-heading"><h3>Orders</h3><span>{selectedOrders.length} total</span></div>
         {bookings.loading ? <Loading /> : bookings.error ? <ErrorState message={bookings.error} /> : selectedOrders.length === 0
           ? <Empty text="No orders found for this account." />
           : <div className="admin-user-orders">{selectedOrders.map((booking) => <article className="admin-user-order" key={booking.id}>
             <div><strong>#{booking.id}</strong><span>{booking.serviceName || "Laundry service"} · {statusLabel(booking.status)}</span></div>
             <div><strong>{money(booking.total)}</strong><span>{adminDateLabel(booking.createdAt)}</span></div>
           </article>)}</div>}
       </div>
     </aside>
   </div>}
  </>;
}

function UserAvatar({ user, large = false }) {
  const photo = user.photoUrl || user.photoUri || "";
  const initials = String(user.name || user.email || "?").trim().split(/\s+/)
   .slice(0, 2).map((part) => part[0]).join("").toUpperCase();
  return <span className={large ? "admin-user-avatar large" : "admin-user-avatar"}>
   {photo ? <img src={photo} alt="" /> : initials}
  </span>;
}

function AdminUserDetail({ label, value }) {
  return <div><span>{label}</span><strong>{value || "Not provided"}</strong></div>;
}

function adminDateMillis(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (typeof value === "string" && value.trim()) {
   const parsed = Date.parse(value);
   if (Number.isFinite(parsed)) return parsed;
  }
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? numeric : 0;
}

function adminDateLabel(value) {
  const millis = adminDateMillis(value);
  return millis > 0 ? new Date(millis).toLocaleDateString("en-UG", {
   day: "2-digit", month: "short", year: "numeric",
  }) : "—";
}

function ProviderOverview({ uid, setPage }) {
  const [rangeDays, setRangeDays] = useState(30);
  const bookings = useProviderBookings(uid);
  const provider = useProviderProfile(uid);
  const data = bookings.data;
  const now = Date.now();
  const today = new Date(now);
  today.setHours(0, 0, 0, 0);
  const todayStart = today.getTime();
  const rangeStart = now - rangeDays * 24 * 60 * 60 * 1000;
  const periodBookings = data.filter((booking) => {
    const created = timestampMillis(booking.createdAt);
    return created >= rangeStart && created <= now;
  });
  const todayCount = data.filter((booking) => timestampMillis(booking.createdAt) >= todayStart
    && timestampMillis(booking.createdAt) <= now).length;
  const pending = data.filter((booking) => booking.status === "BOOKED").length;
  const periodRevenue = data.reduce((sum, booking) => {
    const updated = timestampMillis(booking.updatedAt);
    if (booking.status !== "DELIVERED" || updated < rangeStart || updated > now) return sum;
    return sum + verifiedRevenue(booking);
  }, 0);
  const activityBuckets = Array.from({ length: 7 }, (_, index) => ({
    label: dashboardBucketLabel(rangeStart, now, index, rangeDays),
    total: 0,
    pending: 0,
  }));
  periodBookings.forEach((booking) => {
    const ratio = (timestampMillis(booking.createdAt) - rangeStart) / (now - rangeStart);
    const index = Math.min(6, Math.max(0, Math.floor(ratio * 7)));
    activityBuckets[index].total++;
    if (booking.status === "BOOKED") activityBuckets[index].pending++;
  });
  const maxActivity = Math.max(1, ...activityBuckets.map((bucket) => bucket.total));
  const frequentCustomers = aggregateDashboardPeople(periodBookings, "customerId", "customerName")
    .slice(0, 5);
  const profile = provider.data || {};
  const reviewCount = Number(profile?.reviewCount || 0);
  const rating = Number(profile?.rating);
  const hasRating = reviewCount > 0 && Number.isFinite(rating);
  const approved = profile?.isApproved === true;
  const isOpen = profile?.isOpen !== false;
  const bookingModes = [
    profile?.supportsPickupDelivery !== false ? "Pickup and delivery" : null,
    profile?.supportsDropoff !== false ? "Customer drop-off" : null,
  ].filter(Boolean);
  return <>
    <PageHeading eyebrow="PROVIDER WORKSPACE" title={profile?.businessName || "Business overview"} description="Manage your laundry orders and day-to-day operations."
      action={<button className="secondary" onClick={() => setPage("profile")}>Business profile</button>} />
    {bookings.loading || provider.loading ? <DashboardLoading /> : bookings.error || provider.error
      ? <DashboardError message={bookings.error || provider.error}
        onRetry={() => { bookings.reload(); provider.reload(); }} /> : <>
      {!approved
        ? <div className="notice warn" role="status">Business review pending. Your business will appear to customers after administrator approval.</div>
        : <div className={isOpen ? "notice success" : "notice warn"} role="status">
          <span>{isOpen ? "Accepting new bookings" : "Bookings paused"}
            {bookingModes.length > 0 ? ` · ${bookingModes.join(" · ")}` : ""}
          </span>
          <button className="text-button" onClick={() => setPage("profile")}>Manage availability</button>
        </div>}
      <div className="provider-overview-toolbar"><span>Provider performance</span>
        <div className="range-switch" role="group" aria-label="Provider overview date range">
          {[7, 30, 90].map((days) => <button key={days} className={rangeDays === days ? "selected" : ""}
            aria-pressed={rangeDays === days} onClick={() => setRangeDays(days)}>{days} days</button>)}
        </div>
      </div>
      <div className="stats-grid">
        <StatCard label="Orders today" value={todayCount} note="Bookings created today" />
        <StatCard label="Needs attention" value={pending} note="New requests waiting for a response" accent="accent-amber" />
        <StatCard label="Collected revenue" value={money(periodRevenue)}
          note={`Verified collections · last ${rangeDays} days`} accent="accent-green" />
        <StatCard label="Customer rating" value={hasRating ? rating.toFixed(1) : "—"}
          note={hasRating ? `${reviewCount} customer ${reviewCount === 1 ? "review" : "reviews"}` : "No reviews yet"} />
      </div>
      <div className="provider-overview-insights">
        <section className="panel provider-activity-panel">
          <div className="panel-heading"><div><h2>Order activity</h2>
            <p>Bookings created · last {rangeDays} days</p></div>
            <span className="dashboard-period-label">{periodBookings.length} orders</span></div>
          <div className="dashboard-chart-legend"><span><i className="legend-orders" />All orders</span>
            <span><i className="legend-pending" />Awaiting response</span></div>
          {activityBuckets.some((bucket) => bucket.total > 0)
            ? <div className="dashboard-bar-chart" role="img"
              aria-label={`Provider order activity for the last ${rangeDays} days`}>
            {activityBuckets.map((bucket, index) => {
              const barHeight = bucket.total ? Math.max(5, bucket.total / maxActivity * 100) : 2;
              const pendingHeight = bucket.total ? barHeight * bucket.pending / bucket.total : 0;
              return <div className="dashboard-bar-column" key={`${bucket.label}-${index}`}>
                <span className="dashboard-bar-value">{bucket.total}</span>
                <div className="dashboard-bar-track"><div className="dashboard-order-bar"
                  style={{ height: `${barHeight}%` }}>
                  <span className="dashboard-pending-bar" style={{ height: `${pendingHeight}%` }} />
                </div></div>
                <span className="dashboard-bar-label">{bucket.label}</span>
              </div>;
            })}
            </div> : <Empty text={`No bookings in the last ${rangeDays} days.`} />}
          <div className="dashboard-chart-footer"><span>New booking requests arrive in real time.</span>
            <button className="text-button" onClick={() => setPage("orders")}>Manage orders →</button></div>
        </section>
        <section className="panel provider-customer-ranking">
          <div className="panel-heading"><div><h2>Frequent customers</h2>
            <p>Customers with the most bookings in this period</p></div>
            <button className="text-button" onClick={() => setPage("customers")}>View customers →</button></div>
          {frequentCustomers.length === 0 ? <Empty text={`No customer bookings in the last ${rangeDays} days.`} />
            : <div className="dashboard-ranking-list">{frequentCustomers.map((customer, index) =>
              <div className="dashboard-ranking-row" key={customer.id || customer.name}>
                <span className="dashboard-rank">{index + 1}</span>
                <div><strong>{customer.name}</strong><span>{customer.orders} {customer.orders === 1 ? "booking" : "bookings"}</span></div>
                <strong className="dashboard-ranking-revenue">{money(customer.revenue)}</strong>
              </div>)}</div>}
        </section>
      </div>
      <section className="panel"><div className="panel-heading"><div><h2>Recent bookings</h2><p>Latest customer orders</p></div>
        <button className="text-button" onClick={() => setPage("orders")}>View all orders</button>
      </div><BookingTable bookings={data.slice(0, 5)} /></section>
    </>}
  </>;
}

function ProviderOrders({ uid, setNotice, setBusy, targetBookingId, onTargetResolved }) {
  const [filter, setFilter] = useState("pending");
  const [search, setSearch] = useState("");
  const provider = useProviderProfile(uid);
  const canManageOrders = !provider.loading && !provider.error
    && provider.data?.isApproved === true;
  const bookings = useProviderBookings(uid);
  useEffect(() => {
    if (!targetBookingId || bookings.loading) return;
    if (bookings.error) {
      onTargetResolved("");
      return;
    }
    const booking = bookings.data.find((entry) => entry.id === targetBookingId);
    if (booking) {
      setSearch(targetBookingId);
      setFilter(providerOrderGroup(booking.status));
    } else {
      setNotice("The linked booking is not available in this provider account.");
    }
    onTargetResolved("");
  }, [targetBookingId, bookings.loading, bookings.error, bookings.data,
    onTargetResolved, setNotice]);
  const counts = bookings.data.reduce((result, booking) => {
    result[providerOrderGroup(booking.status)] += 1;
    return result;
  }, { pending: 0, progress: 0, history: 0 });
  const filtered = bookings.data.filter((booking) => providerOrderGroup(booking.status) === filter
    && [booking.id, booking.customerName, booking.customerPhone, booking.serviceName, booking.status,
      booking.address, booking.assignedRiderName].some((value) => matchesSearch(value, search)));
  const riders = useProviderRiders(uid);
  const ridersForAssignment = riders.data.filter((rider) => rider.isActive === true);
  async function updateStatus(booking, status) {
    setBusy(true);
    setNotice("");
    try {
      await httpsCallable(functions, "updateBookingStatus")({ bookingId: booking.id, status });
      setNotice(`Order #${booking.id} updated to ${statusLabel(status)}.`);
      await bookings.reload();
    } catch (error) { setNotice(error.message || "Could not update order. The Functions deployment may be required."); }
    finally { setBusy(false); }
  }
  async function assignRider(booking, riderId) {
    if (!riderId) {
      setNotice("Choose a rider before assigning the order.");
      return;
    }
    if (!ridersForAssignment.some((rider) => rider.id === riderId)) {
      setNotice("Choose an active rider before assigning the order.");
      return;
    }
    setBusy(true);
    try {
      await httpsCallable(functions, "assignBookingRider")({ bookingId: booking.id, riderId });
      setNotice("Rider assigned to booking.");
      await bookings.reload();
    } catch (error) { setNotice(error.message || "Could not assign rider. Functions deployment may be required."); }
    finally { setBusy(false); }
  }
  return <>
    <PageHeading eyebrow="ORDER MANAGEMENT" title="Orders" description="Review customer details and move each order through its valid next status." action={<div className="heading-actions"><ExportActions rows={filtered} fileName="Orders" columns={[
      { label: "Booking", value: (row) => row.id }, { label: "Customer", value: (row) => row.customerName || "" },
      { label: "Phone", value: (row) => row.customerPhone || "" }, { label: "Service", value: (row) => row.serviceName || "" },
      { label: "Created", value: (row) => shortDate(row.createdAt) }, { label: "Status", value: (row) => row.status || "" },
      { label: "Total (UGX)", value: (row) => Number(row.total || 0) },
    ]} /><button className="secondary" onClick={bookings.reload}>Refresh</button></div>} />
    <label className="search-control"><span>Search orders</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Customer, service, phone, address…" /></label>
    <div className="filter-row" aria-label="Filter orders">
      {[["pending", "Pending"], ["progress", "In progress"], ["history", "History"]].map(([group, label]) =>
      <button key={group} className={filter === group ? "filter active" : "filter"}
        aria-pressed={filter === group} onClick={() => setFilter(group)}>
        {label} · {counts[group]}
      </button>)}</div>
    {provider.loading && <div className="notice" role="status" aria-live="polite">Checking business approval before enabling order updates…</div>}
    {provider.error && <div className="notice warn" role="alert">
      <span>Could not verify business approval. Order status changes and rider assignments are disabled. {provider.error}</span>
      <button className="secondary small" onClick={provider.reload}>Retry</button>
    </div>}
    {riders.error && <div className="notice warn" role="alert"><span>Could not load active riders for assignment. {riders.error}</span>
      <button className="secondary small" onClick={riders.reload}>Retry</button></div>}
    {!provider.loading && !provider.error && !canManageOrders
      && <div className="notice warn" role="status">Your profile is awaiting administrator approval. Order status changes and rider assignments will be available after approval.</div>}
    <section className="panel">{bookings.loading ? <div className="bookings-loading" role="status" aria-label="Loading orders">
      {Array.from({ length: 3 }, (_, index) => <div className="bookings-skeleton order-skeleton" key={index} />)}
    </div> : bookings.error ? <div className="bookings-error" role="alert">
      <ErrorState message={bookings.error} /><button className="secondary" onClick={bookings.reload}>Retry</button>
    </div> :
      filtered.length === 0 ? <Empty text="No orders match this filter." /> : <div className="order-list">
        {filtered.map((booking) => <OrderCard key={booking.id} booking={booking} riders={ridersForAssignment}
          onStatus={updateStatus} onAssign={assignRider} canManageOrders={canManageOrders} />)}
      </div>}</section>
  </>;
}

function ProviderCustomers({ uid }) {
  const bookings = useProviderBookings(uid);
  const [search, setSearch] = useState("");
  const [selectedKey, setSelectedKey] = useState("");
  const customers = useMemo(() => {
    const grouped = new Map();
    bookings.data.forEach((booking) => {
      const phone = String(booking.customerPhone || "").trim();
      const name = String(booking.customerName || "").trim();
      const key = booking.customerId || (phone
        ? `phone:${phone.replace(/\D/g, "")}`
        : name ? `name:${name.toLocaleLowerCase()}` : `booking:${booking.id}`);
      const customer = grouped.get(key) || {
        id: key,
        name: name || "Customer",
        phone,
        bookings: [],
      };
      if (!customer.phone && phone) customer.phone = phone;
      if (customer.name === "Customer" && name) customer.name = name;
      customer.bookings.push(booking);
      grouped.set(key, customer);
    });
    return [...grouped.values()];
  }, [bookings.data]);
  const visibleCustomers = customers.filter((customer) => [
    customer.name, customer.phone,
  ].some((value) => matchesSearch(value, search)));
  const selected = visibleCustomers.find((customer) => customer.id === selectedKey)
    || customers.find((customer) => customer.id === selectedKey);
  const totalBookings = bookings.data.length;
  const totalBookingValue = bookings.data.reduce((sum, booking) =>
    sum + Number(booking.total || 0), 0);
  return <>
    <PageHeading eyebrow="CUSTOMER MANAGEMENT" title="Customers"
      description="See customers who have booked with your business and review their order history."
      action={<ExportActions rows={visibleCustomers} fileName="Customers" columns={[
        { label: "Customer", value: (row) => row.name },
        { label: "Phone", value: (row) => row.phone },
        { label: "Orders", value: (row) => row.bookings.length },
        { label: "Booking value (UGX)", value: (row) =>
          row.bookings.reduce((sum, booking) => sum + Number(booking.total || 0), 0) },
        { label: "Latest booking", value: (row) => shortDate(row.bookings[0]?.createdAt) },
      ]} />} />
    {bookings.loading ? <Loading /> : bookings.error ? <ErrorState message={bookings.error} /> : null}
    {!bookings.loading && !bookings.error && <>
      <div className="stats-grid">
        <StatCard label="Customers" value={customers.length} note="Distinct customers from your bookings" />
        <StatCard label="Bookings" value={totalBookings} note="All bookings for this business" accent="accent-blue" />
        <StatCard label="Booking value" value={money(totalBookingValue)} note="Sum of recorded booking totals" accent="accent-green" />
      </div>
      <section className="panel">
        <label className="search-control"><span>Search customers</span>
          <input value={search} onChange={(event) => setSearch(event.target.value)}
            placeholder="Name or phone number…" /></label>
        {visibleCustomers.length === 0 ? <Empty text={customers.length
          ? "No customers match your search." : "Customers appear here after they book with your business."} />
          : <div className="customer-directory">
            <div className="table-scroll"><table><thead><tr>
              <th>Customer</th><th>Phone</th><th>Orders</th><th>Booking value</th><th>Latest booking</th>
            </tr></thead><tbody>
              {visibleCustomers.map((customer) => <tr key={customer.id} tabIndex={0}
                role="button" aria-pressed={selected?.id === customer.id}
                className={selected?.id === customer.id ? "customer-row selected" : "customer-row"}
                onClick={() => setSelectedKey(customer.id)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    setSelectedKey(customer.id);
                  }
                }}>
                <td><strong>{customer.name}</strong></td><td>{customer.phone || "Not provided"}</td>
                <td>{customer.bookings.length}</td>
                <td>{money(customer.bookings.reduce((sum, booking) =>
                  sum + Number(booking.total || 0), 0))}</td>
                <td>{shortDate(customer.bookings[0]?.createdAt)}</td>
              </tr>)}
            </tbody></table></div>
          </div>}
      </section>
      {selected && <section className="panel">
        <div className="panel-heading"><div><h2>{selected.name}</h2>
          <p>{selected.phone || "Phone not recorded"} · {selected.bookings.length} booking{selected.bookings.length === 1 ? "" : "s"}</p>
        </div></div>
        <BookingTable bookings={selected.bookings} />
      </section>}
    </>}
  </>;
}

function ProviderServices({ uid, setNotice, setBusy }) {
  const provider = useProviderProfile(uid);
  const [editing, setEditing] = useState(-1);
  const [serviceChoice, setServiceChoice] = useState(KNOWN_SERVICES[0]);
  const [customName, setCustomName] = useState("");
  const [search, setSearch] = useState("");
  const [availability, setAvailability] = useState("ALL");
  const services = provider.data?.services || [];
  const filteredServices = services.filter((service) => (availability === "ALL"
    || (availability === "AVAILABLE" ? service.isAvailable !== false : service.isAvailable === false))
    && matchesSearch(service.name, search));

  function startEdit(index) {
    setEditing(index);
    const service = index < 0 ? null : services[index];
    const name = service?.name || "";
    setServiceChoice(KNOWN_SERVICES.includes(name) ? name : "__custom");
    setCustomName(service && !KNOWN_SERVICES.includes(name) ? name : "");
    const form = document.getElementById("provider-service-form");
    if (!form) return;
    form.elements.namedItem("pricePerKg").value = service?.pricePerKg ?? "";
  }

  async function save(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const name = String(serviceChoice === "__custom" ? customName : serviceChoice).trim();
    const pricePerKg = Number(formData.get("pricePerKg"));
    if (name.length < 2 || name.length > 80 || !Number.isFinite(pricePerKg) || pricePerKg <= 0) {
      setNotice("Enter a service name (2–80 characters) and a price above zero.");
      return;
    }
    if (services.some((service, index) => index !== editing
      && String(service.name || "").toLowerCase() === name.toLowerCase())) {
      setNotice("A service with that name already exists.");
      return;
    }
    const updated = [...services];
    const prior = editing < 0 ? null : services[editing];
    const service = { name, pricePerKg, isAvailable: prior?.isAvailable !== false };
    if (editing < 0) updated.push(service);
    else updated[editing] = service;
    setBusy(true);
    try {
      await updateDoc(doc(db, "providers", uid), { services: updated });
      setNotice(editing < 0 ? "Service added." : "Service updated.");
      setEditing(-1);
      form.reset();
      setServiceChoice(KNOWN_SERVICES[0]);
      setCustomName("");
      await provider.reload();
    } catch (error) { setNotice(error.message || "Could not save service."); }
    finally { setBusy(false); }
  }

  async function toggle(service, index) {
    const updated = services.map((entry, current) => current === index
      ? { ...entry, isAvailable: entry.isAvailable === false } : entry);
    setBusy(true);
    try {
      await updateDoc(doc(db, "providers", uid), { services: updated });
      setNotice(service.isAvailable === false ? "Service is available to customers." : "Service paused.");
      await provider.reload();
    } catch (error) { setNotice(error.message || "Could not update service availability."); }
    finally { setBusy(false); }
  }

  async function remove(index) {
    if (!window.confirm(`Remove ${services[index]?.name || "this service"} from your catalog?`)) return;
    const updated = services.filter((_, current) => current !== index);
    setBusy(true);
    try {
      await updateDoc(doc(db, "providers", uid), { services: updated });
      if (editing === index) setEditing(-1);
      setNotice("Service removed.");
      await provider.reload();
    } catch (error) { setNotice(error.message || "Could not remove service."); }
    finally { setBusy(false); }
  }

  return <>
    <PageHeading eyebrow="SERVICE CATALOG" title="Services & pricing" description="Set the services customers can book and control availability without deleting your prices." action={<button className="secondary" onClick={() => startEdit(-1)}>Add service</button>} />
    <section className="panel service-form-panel"><div className="panel-heading"><div><h2>{editing < 0 ? "Add a service" : "Edit service"}</h2><p>Prices are stored in UGX per kilogram and shared with the Android app.</p></div></div>
      <form id="provider-service-form" className="service-form" onSubmit={save}>
        <label>Service name<select name="serviceChoice" value={serviceChoice} onChange={(event) => setServiceChoice(event.target.value)}>
          {KNOWN_SERVICES.map((name) => <option key={name} value={name}>{name}</option>)}
          <option value="__custom">Custom service</option>
        </select></label>
        {serviceChoice === "__custom" && <label>Custom service name<input name="customName" required minLength="2" maxLength="80" value={customName}
          onChange={(event) => setCustomName(event.target.value)} placeholder="Enter your service" /></label>}
        <label>Price per kg (UGX)<input name="pricePerKg" required type="number" min="1" step="1" inputMode="numeric" placeholder="5000" /></label>
        <button className="primary">{editing < 0 ? "Add service" : "Save changes"}</button>
        {editing >= 0 && <button className="secondary" type="button" onClick={() => {
          setEditing(-1); setServiceChoice(KNOWN_SERVICES[0]); setCustomName("");
          document.getElementById("provider-service-form")?.reset();
        }}>Cancel</button>}
      </form>
    </section>
    <section className="panel"><div className="panel-heading"><div><h2>Your services</h2><p>{services.filter((service) => service.isAvailable !== false).length} available · {services.length} total</p></div>
      <div className="heading-actions"><ExportActions rows={filteredServices} fileName="Services" columns={[
        { label: "Service", value: (row) => row.name || "" }, { label: "Price per kg (UGX)", value: (row) => Number(row.pricePerKg || 0) },
        { label: "Availability", value: (row) => row.isAvailable === false ? "Unavailable" : "Available" },
      ]} /><button className="secondary" onClick={provider.reload}>Refresh</button></div></div>
      <div className="table-controls"><label className="search-control"><span>Search services</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Service name…" /></label>
        <label className="search-control status-select"><span>Availability</span><select value={availability} onChange={(event) => setAvailability(event.target.value)}>
          <option value="ALL">All services</option><option value="AVAILABLE">Available</option><option value="UNAVAILABLE">Unavailable</option>
        </select></label></div>
      {provider.loading ? <div className="providers-loading" role="status" aria-label="Loading services">
        {Array.from({ length: 3 }, (_, index) => <div className="bookings-skeleton service-skeleton" key={index} />)}
      </div> : provider.error ? <div className="providers-error" role="alert">
        <ErrorState message={provider.error} /><button className="secondary" onClick={provider.reload}>Retry</button>
      </div> : services.length === 0 ? <Empty text="No services yet. Add the services and prices customers can book." /> :
        filteredServices.length === 0 ? <Empty text="No services match your search." /> : <div className="records">{filteredServices.map((service) => {
          const index = services.indexOf(service);
          return <div className="record-row service-row" key={`${service.name}-${index}`}>
          <div className="record-main"><strong>{service.name || "Laundry service"}</strong><span>{money(service.pricePerKg)} / kg</span></div>
          <span className={service.isAvailable === false ? "badge rejected" : "badge delivered"}>{service.isAvailable === false ? "Unavailable" : "Available"}</span>
          <button className="secondary small" onClick={() => startEdit(index)}>Edit</button>
          <button className="secondary small" onClick={() => toggle(service, index)}>{service.isAvailable === false ? "Enable" : "Pause"}</button>
          <button className="danger small" onClick={() => remove(index)}>Remove</button>
        </div>;
        })}</div>}
    </section>
  </>;
}

function ProviderInventory({ uid, setNotice, setBusy }) {
  const provider = useProviderProfile(uid);
  const [items, setItems] = useState({ data: [], loading: true, error: "" });
  const [editing, setEditing] = useState("");
  const [form, setForm] = useState({
    name: "", sku: "", unit: "", quantity: "0", lowStockThreshold: "0", unitCost: "",
  });
  const approved = provider.data?.isApproved === true;

  useEffect(() => {
    setItems({ data: [], loading: true, error: "" });
    return onSnapshot(collection(db, "providers", uid, "inventory"), (snapshot) => {
      const data = snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() }))
        .sort((left, right) => String(left.name || "").localeCompare(String(right.name || "")));
      setItems({ data, loading: false, error: "" });
    }, (error) => setItems({
      data: [], loading: false, error: error.message || "Could not load inventory.",
    }));
  }, [uid]);

  function startEdit(item = null) {
    setEditing(item?.id || "");
    setForm({
      name: item?.name || "",
      sku: item?.sku || "",
      unit: item?.unit || "",
      quantity: String(item?.quantity ?? 0),
      lowStockThreshold: String(item?.lowStockThreshold ?? 0),
      unitCost: item?.unitCost == null ? "" : String(item.unitCost),
    });
  }

  async function save(event) {
    event.preventDefault();
    if (!approved || provider.loading || provider.error) {
      setNotice("Provider approval must be verified before changing inventory.");
      return;
    }
    const name = form.name.trim();
    const sku = form.sku.trim();
    const unit = form.unit.trim();
    const quantity = Number(form.quantity);
    const lowStockThreshold = Number(form.lowStockThreshold);
    const unitCost = form.unitCost.trim() === "" ? null : Number(form.unitCost);
    if (!name || name.length > 100 || sku.length > 60 || unit.length > 30
      || !Number.isInteger(quantity) || quantity < 0 || quantity > 1000000
      || !Number.isInteger(lowStockThreshold) || lowStockThreshold < 0
      || lowStockThreshold > 1000000
      || (unitCost !== null && (!Number.isFinite(unitCost) || unitCost < 0 || unitCost > 1000000000))) {
      setNotice("Enter a name (1–100 characters), whole-number stock and threshold (0–1,000,000), and a valid optional unit cost.");
      return;
    }
    const record = {
      name, sku, unit, quantity, lowStockThreshold,
      updatedAt: serverTimestamp(),
    };
    if (unitCost !== null) record.unitCost = unitCost;
    setBusy(true);
    try {
      if (editing) {
        const updates = { ...record };
        if (unitCost === null) updates.unitCost = deleteField();
        await updateDoc(doc(db, "providers", uid, "inventory", editing), updates);
        setNotice("Inventory item updated.");
      } else {
        await addDoc(collection(db, "providers", uid, "inventory"), {
          ...record, createdAt: serverTimestamp(),
        });
        setNotice("Inventory item added.");
      }
      startEdit();
    } catch (error) {
      setNotice(error.message || "Could not save inventory item.");
    } finally {
      setBusy(false);
    }
  }

  async function adjust(item, direction) {
    if (!approved || provider.loading || provider.error) {
      setNotice("Provider approval is required to adjust stock.");
      return;
    }
    const entered = window.prompt(`How many ${item.unit || "units"} to ${direction > 0 ? "add" : "remove"}?`, "1");
    if (entered === null) return;
    const amount = Number(entered);
    if (!Number.isInteger(amount) || amount < 1 || amount > 1000000) {
      setNotice("Enter a whole-number adjustment between 1 and 1,000,000.");
      return;
    }
    setBusy(true);
    try {
      await runTransaction(db, async (transaction) => {
        const ref = doc(db, "providers", uid, "inventory", item.id);
        const snapshot = await transaction.get(ref);
        if (!snapshot.exists()) throw new Error("This inventory item no longer exists.");
        const current = snapshot.data().quantity;
        const next = current + direction * amount;
        if (!Number.isInteger(current) || next < 0 || next > 1000000) {
          throw new Error(next < 0 ? "Stock cannot be reduced below zero." : "Stock quantity exceeds the allowed limit.");
        }
        transaction.update(ref, { quantity: next, updatedAt: serverTimestamp() });
      });
      setNotice(`Stock ${direction > 0 ? "increased" : "decreased"}.`);
    } catch (error) {
      setNotice(error.message || "Could not adjust stock.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(item) {
    if (!approved || provider.loading || provider.error) {
      setNotice("Provider approval is required to delete inventory items.");
      return;
    }
    if (!window.confirm(`Delete ${item.name} from inventory? This does not affect laundry services or invoices.`)) return;
    setBusy(true);
    try {
      await deleteDoc(doc(db, "providers", uid, "inventory", item.id));
      if (editing === item.id) startEdit();
      setNotice("Inventory item deleted.");
    } catch (error) {
      setNotice(error.message || "Could not delete inventory item.");
    } finally {
      setBusy(false);
    }
  }

  return <>
    <PageHeading eyebrow="PROVIDER STOCK" title="Inventory"
      description="Track supplies and products for your business. Inventory is separate from per-kg laundry services and POS invoices; stock is never deducted automatically." />
    {(provider.loading || provider.error) && <div className="notice warn" role="status">
      {provider.loading ? "Verifying provider approval…" : `Could not verify provider approval: ${provider.error}`}
    </div>}
    {!provider.loading && !provider.error && !approved
      && <div className="notice warn" role="status">Inventory changes are disabled until the provider account is approved.</div>}
    <section className="panel">
      <div className="panel-heading"><div><h2>{editing ? "Edit inventory item" : "Add inventory item"}</h2>
        <p>Stock quantities are managed independently; no service or invoice consumes inventory.</p></div></div>
      <form className="inventory-form" onSubmit={save}>
        <label>Item name<input required maxLength="100" value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
        <label>SKU (optional)<input maxLength="60" value={form.sku}
          onChange={(event) => setForm({ ...form, sku: event.target.value })} /></label>
        <label>Unit (optional)<input maxLength="30" placeholder="bottles, packs…" value={form.unit}
          onChange={(event) => setForm({ ...form, unit: event.target.value })} /></label>
        <label>Current stock<input required type="number" min="0" max="1000000" step="1" value={form.quantity}
          onChange={(event) => setForm({ ...form, quantity: event.target.value })} /></label>
        <label>Low-stock threshold<input required type="number" min="0" max="1000000" step="1" value={form.lowStockThreshold}
          onChange={(event) => setForm({ ...form, lowStockThreshold: event.target.value })} /></label>
        <label>Unit cost (UGX, optional)<input type="number" min="0" max="1000000000" step="0.01" value={form.unitCost}
          onChange={(event) => setForm({ ...form, unitCost: event.target.value })} /></label>
        <div className="inventory-form-actions"><button className="primary" disabled={!approved || provider.loading || Boolean(provider.error)}>
          {editing ? "Save changes" : "Add to inventory"}
        </button>{editing && <button className="secondary" type="button" onClick={() => startEdit()}>Cancel</button>}</div>
      </form>
    </section>
    <section className="panel">
      <div className="panel-heading"><div><h2>Current stock</h2>
        <p>{items.data.length} item{items.data.length === 1 ? "" : "s"} · low-stock items are flagged at or below their threshold</p></div></div>
      {items.loading ? <Loading /> : items.error ? <ErrorState message={items.error} />
        : items.data.length === 0 ? <Empty text="No inventory items yet. Add supplies or products above." />
          : <div className="inventory-list">{items.data.map((item) => {
            const low = Number(item.quantity) <= Number(item.lowStockThreshold);
            return <article className="inventory-item" key={item.id}>
              <div className="inventory-item-main"><strong>{item.name}</strong>
                <span>{item.sku ? `SKU ${item.sku} · ` : ""}{item.unit || "units"}
                  {item.unitCost != null ? ` · ${money(item.unitCost)} per ${item.unit || "unit"}` : ""}</span></div>
              <div className="inventory-stock"><strong>{item.quantity} {item.unit || "units"}</strong>
                <span>Low at {item.lowStockThreshold}</span></div>
              <span className={low ? "badge pending" : "badge delivered"}>{low ? "Low stock" : "In stock"}</span>
              <div className="heading-actions inventory-actions">
                <button className="secondary small" disabled={!approved} onClick={() => adjust(item, -1)}>Adjust stock</button>
                <button className="secondary small" disabled={!approved} onClick={() => startEdit(item)}>Edit</button>
                <button className="danger small" disabled={!approved} onClick={() => remove(item)}>Delete</button>
              </div>
            </article>;
          })}</div>}
    </section>
  </>;
}

function ProviderPos({ uid, setNotice, setBusy }) {
 const provider = useProviderProfile(uid);
 const [invoices, setInvoices] = useState({ data: [], loading: true, error: "" });
 const [selectedService, setSelectedService] = useState("");
 const profile = provider.data || {};
 const services = (profile.services || []).map((service, index) => ({ ...service, index }))
   .filter((service) => service.isAvailable !== false
     && Number.isFinite(Number(service.pricePerKg)) && Number(service.pricePerKg) > 0);
 const approved = profile.isApproved === true;
 useEffect(() => {
   setInvoices({ data: [], loading: true, error: "" });
   return onSnapshot(collection(db, "providers", uid, "invoices"), (snapshot) => {
     const data = snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() }))
       .sort((left, right) => timestampMillis(right.createdAt) - timestampMillis(left.createdAt));
     setInvoices({ data, loading: false, error: "" });
   }, (error) => setInvoices({
     data: [], loading: false, error: error.message || "Could not load invoices.",
   }));
 }, [uid]);

 async function recordSale(event) {
   event.preventDefault();
   if (!approved || provider.loading || provider.error) {
     setNotice("Provider approval must be verified before recording a sale.");
     return;
   }
   const form = event.currentTarget;
   const values = new FormData(form);
   const index = Number(selectedService);
   const service = services.find((entry) => entry.index === index);
   const customerName = String(values.get("customerName") || "").trim();
   const customerPhone = String(values.get("customerPhone") || "").trim();
   const quantityKg = Number(values.get("quantityKg"));
   const paymentMethod = String(values.get("paymentMethod") || "cash");
   if (!service || customerName.length < 2 || customerName.length > 100
     || customerPhone.length > 30 || !Number.isFinite(quantityKg)
     || quantityKg <= 0 || quantityKg > 1000) {
     setNotice("Choose an available catalog service and enter valid customer and quantity details.");
     return;
   }
   const total = Number(service.pricePerKg) * quantityKg;
   const invoiceRef = doc(collection(db, "providers", uid, "invoices"));
   const saleRef = doc(db, "providers", uid, "sales", invoiceRef.id);
   const record = {
     providerId: uid,
     invoiceNumber: `WL-${invoiceRef.id.slice(0, 8).toUpperCase()}`,
     customerName,
     customerPhone,
     serviceName: service.name,
     serviceIndex: index,
     pricePerKg: Number(service.pricePerKg),
     quantityKg,
     subtotal: total,
     total,
     currency: "UGX",
     paymentMethod,
     createdAt: serverTimestamp(),
   };
   setBusy(true);
   try {
     const batch = writeBatch(db);
     batch.set(invoiceRef, record);
     batch.set(saleRef, record);
     await batch.commit();
     form.reset();
     setSelectedService("");
     setNotice(`Sale recorded. Invoice ${record.invoiceNumber} is saved and immutable.`);
   } catch (error) {
     setNotice(error.message || "Could not record the sale and invoice.");
   } finally {
     setBusy(false);
   }
 }

 function invoiceMarkup(invoice) {
   const lines = [
     ["Invoice", invoice.invoiceNumber],
     ["Date", shortDateTime(invoice.createdAt)],
     ["Customer", invoice.customerName],
     ["Phone", invoice.customerPhone || "Not provided"],
     ["Service", invoice.serviceName],
     ["Price per kg", money(invoice.pricePerKg)],
     ["Quantity", `${invoice.quantityKg} kg`],
     ["Payment method", String(invoice.paymentMethod || "").replaceAll("_", " ")],
     ["TOTAL", money(invoice.total)],
   ];
   return `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(invoice.invoiceNumber)}</title>
     <style>body{font:16px Arial,sans-serif;color:#172b42;padding:32px;max-width:650px;margin:auto}
     h1{color:#1652e6}table{width:100%;border-collapse:collapse}td{padding:10px;border-bottom:1px solid #dce4ed}
     td:last-child{text-align:right}tr:last-child{font-weight:bold;font-size:20px}</style></head><body>
     <h1>WashLink</h1><p>In-person sale invoice</p><table>${lines.map(([label, value]) =>
   `<tr><td>${escapeHtml(label)}</td><td>${escapeHtml(value ?? "")}</td></tr>`).join("")}</table>
     <p>Thank you for your business.</p></body></html>`;
 }

 function printInvoice(invoice) {
   const printWindow = window.open("", "_blank");
   if (!printWindow) {
     setNotice("Allow pop-ups to print this invoice.");
     return;
   }
   printWindow.opener = null;
   printWindow.document.open();
   printWindow.document.write(`${invoiceMarkup(invoice)}<script>window.addEventListener("load",()=>window.print());</script>`);
   printWindow.document.close();
 }

 async function shareInvoice(invoice) {
   const text = `${invoice.invoiceNumber}\n${invoice.customerName}\n${invoice.serviceName} · ${invoice.quantityKg} kg\nTotal: ${money(invoice.total)}`;
   try {
     if (navigator.share) await navigator.share({ title: "WashLink invoice", text });
     else if (navigator.clipboard) {
       await navigator.clipboard.writeText(text);
       setNotice("Invoice summary copied to clipboard.");
     } else setNotice(text);
   } catch (error) {
     if (error.name !== "AbortError") setNotice(error.message || "Could not share invoice.");
   }
 }

 return <>
   <PageHeading eyebrow="PROVIDER POINT OF SALE" title="In-person POS & invoices"
     description="Record walk-in sales independently of marketplace bookings. Prices are read from your service catalog." />
   {(provider.loading || provider.error) && <div className="notice warn" role="status">
     {provider.loading ? "Verifying provider approval and service catalog…" : `Could not verify your service catalog: ${provider.error}`}
   </div>}
   {!provider.loading && !provider.error && !approved
     && <div className="notice warn" role="status">In-person sales are disabled until the provider account is approved.</div>}
   <section className="panel">
     <div className="panel-heading"><div><h2>New walk-in sale</h2>
       <p>Customer and service details are snapshotted into a permanent invoice.</p></div></div>
     {services.length === 0 && !provider.loading && !provider.error
       ? <Empty text="Add an available service and price in Services before recording a sale." />
       : <form className="pos-form" onSubmit={recordSale}>
         <label>Service<select required value={selectedService}
           onChange={(event) => setSelectedService(event.target.value)}>
           <option value="" disabled>Select from your catalog</option>
           {services.map((service) => <option key={service.index} value={service.index}>
             {service.name} — {money(service.pricePerKg)} / kg
           </option>)}
         </select></label>
         <label>Customer name<input name="customerName" required minLength="2" maxLength="100" /></label>
         <label>Phone number (optional)<input name="customerPhone" type="tel" maxLength="30" /></label>
         <label>Quantity (kg)<input name="quantityKg" required type="number" min="0.01" max="1000" step="0.01" /></label>
         <label>Payment method<select name="paymentMethod" defaultValue="cash">
           <option value="cash">Cash</option><option value="mobile_money">Mobile money</option>
           <option value="card">Card</option><option value="other">Other</option>
         </select></label>
         <button className="primary" disabled={!approved || provider.loading || !services.length}>
           Record sale & issue invoice
         </button>
       </form>}
   </section>
   <section className="panel">
     <div className="panel-heading"><div><h2>Immutable invoices</h2>
       <p>Invoices and sales are stored separately from marketplace bookings.</p></div>
     </div>
     {invoices.loading ? <Loading /> : invoices.error ? <ErrorState message={invoices.error} />
       : invoices.data.length === 0 ? <Empty text="No in-person invoices have been issued yet." />
         : <div className="table-scroll"><table><thead><tr>
           <th>Invoice</th><th>Date</th><th>Customer</th><th>Service</th><th>Qty</th><th>Total</th><th>Payment</th><th>Actions</th>
         </tr></thead><tbody>{invoices.data.map((invoice) => <tr key={invoice.id}>
           <td className="mono">{invoice.invoiceNumber}</td><td>{shortDate(invoice.createdAt)}</td>
           <td>{invoice.customerName}{invoice.customerPhone && <span className="sub-cell">{invoice.customerPhone}</span>}</td>
           <td>{invoice.serviceName}</td><td>{invoice.quantityKg} kg</td><td>{money(invoice.total)}</td>
           <td>{String(invoice.paymentMethod || "").replaceAll("_", " ")}</td>
           <td><div className="heading-actions"><button className="secondary small" onClick={() => printInvoice(invoice)}>Print</button>
             <button className="secondary small" onClick={() => shareInvoice(invoice)}>Share</button></div></td>
         </tr>)}</tbody></table></div>}
   </section>
 </>;
}

function ProviderStaff({ uid, setNotice, setBusy }) {
 const provider = useProviderProfile(uid);
 const [staff, setStaff] = useState({ data: [], loading: true, error: "" });
 const [editing, setEditing] = useState("");
 const [form, setForm] = useState({ name: "", phone: "", role: "Attendant" });
 const [customRole, setCustomRole] = useState("");
 const standardRoles = ["Attendant", "Cashier", "Manager", "Other"];
 const phonePattern = "[+]?[0-9() .-]{7,30}";
 const namePattern = "[A-Za-zÀ-ÖØ-öø-ÿ][A-Za-zÀ-ÖØ-öø-ÿ .'-]{1,79}";
 const rolePattern = "[A-Za-zÀ-ÖØ-öø-ÿ][A-Za-zÀ-ÖØ-öø-ÿ0-9 &.'/-]{1,39}";
 const customRoles = [...new Set(staff.data.map((entry) => String(entry.role || "").trim())
   .filter((role) => role && !standardRoles.some((standard) => standard.toLowerCase() === role.toLowerCase())))];
 const approved = provider.data?.isApproved === true;
 useEffect(() => {
   setStaff({ data: [], loading: true, error: "" });
   return onSnapshot(collection(db, "providers", uid, "staff"), (snapshot) => setStaff({
     data: snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() })),
     loading: false,
     error: "",
   }), (error) => setStaff({
     data: [], loading: false, error: error.message || "Could not load staff roster.",
   }));
 }, [uid]);

 function startEdit(entry) {
   setEditing(entry?.id || "");
   setForm({
     name: entry?.name || "",
     phone: entry?.phone || "",
     role: entry?.role || "Attendant",
   });
   setCustomRole("");
 }
 async function save(event) {
   event.preventDefault();
   if (!event.currentTarget.reportValidity()) return;
   if (!approved || provider.loading || provider.error) {
     setNotice("Provider approval must be verified before changing the staff roster.");
     return;
   }
   const name = form.name.trim();
   const phone = form.phone.trim();
   const phoneDigits = phone.replace(/\D/g, "");
   const role = form.role === "__CUSTOM_ROLE__" ? customRole.trim() : form.role.trim();
   if (name.length < 2 || name.length > 80
       || !new RegExp(`^${namePattern}$`).test(name)
       || phone.length < 7 || phone.length > 30
       || !new RegExp(`^${phonePattern}$`).test(phone)
       || phoneDigits.length < 7 || phoneDigits.length > 15
       || role.length < 2 || role.length > 40
       || !new RegExp(`^${rolePattern}$`).test(role)) {
     setNotice("Enter a valid name, phone number (7–15 digits), and role (2–40 characters).");
     return;
   }
   setBusy(true);
   try {
     if (editing) {
       await updateDoc(doc(db, "providers", uid, "staff", editing), {
         name, phone, role,
       });
       setNotice("Staff roster entry updated.");
     } else {
       await addDoc(collection(db, "providers", uid, "staff"), {
         name, phone, role, active: true, createdAt: serverTimestamp(),
       });
       setNotice("Staff roster entry added. No login account was created.");
     }
     startEdit(null);
   } catch (error) {
     setNotice(error.message || "Could not save staff roster entry.");
   } finally {
     setBusy(false);
   }
 }
 async function toggle(entry) {
   if (!approved || provider.loading || provider.error) {
     setNotice("Provider approval is required to update the staff roster.");
     return;
   }
   setBusy(true);
   try {
     await updateDoc(doc(db, "providers", uid, "staff", entry.id), { active: !entry.active });
     setNotice(entry.active ? "Staff roster entry deactivated." : "Staff roster entry activated.");
   } catch (error) {
     setNotice(error.message || "Could not update staff roster entry.");
   } finally { setBusy(false); }
 }
 async function remove(entry) {
   if (!approved || provider.loading || provider.error) {
     setNotice("Provider approval is required to remove staff roster entries.");
     return;
   }
   if (!window.confirm(`Remove ${entry.name} from the staff roster? This does not affect any account.`)) return;
   setBusy(true);
   try {
     await deleteDoc(doc(db, "providers", uid, "staff", entry.id));
     if (editing === entry.id) startEdit(null);
     setNotice("Staff roster entry removed.");
   } catch (error) {
     setNotice(error.message || "Could not remove staff roster entry.");
   } finally { setBusy(false); }
 }

 return <>
   <PageHeading eyebrow="PROVIDER TEAM" title="Staff roster"
     description="A provider-scoped contact roster only. Adding staff here never creates login accounts." />
   {(provider.loading || provider.error) && <div className="notice warn">
     {provider.loading ? "Verifying provider approval…" : `Could not verify provider approval: ${provider.error}`}
   </div>}
   {!provider.loading && !provider.error && !approved
     && <div className="notice warn">Roster changes are disabled until this provider is approved.</div>}
   <section className="panel staff-form-panel">
     <div className="panel-heading"><div><h2>{editing ? "Edit roster entry" : "Add staff member"}</h2>
       <p>Roster only — this does not create a login account.</p></div></div>
     <form className="staff-form" onSubmit={save}>
       <label>Name<input required type="text" autoComplete="name" minLength="2" maxLength="80"
         pattern={namePattern} title="Use letters, spaces, apostrophes, periods, or hyphens."
         value={form.name}
         onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
       <label>Phone<input required type="tel" inputMode="tel" autoComplete="tel" minLength="7"
         maxLength="30" pattern={phonePattern}
         title="Use digits and optional +, spaces, parentheses, periods, or hyphens."
         value={form.phone}
         onChange={(event) => setForm({ ...form, phone: event.target.value.replace(/[^0-9+(). -]/g, "") })} /></label>
       <label>Role<select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })}>
         {standardRoles.map((role) => <option key={role}>{role}</option>)}
         {customRoles.map((role) => <option key={role}>{role}</option>)}
         <option value="__CUSTOM_ROLE__">＋ Add a new role…</option>
       </select></label>
       {form.role === "__CUSTOM_ROLE__" && <label>New role name
         <input required type="text" minLength="2" maxLength="40" pattern={rolePattern}
           title="Use letters, numbers, spaces, and basic punctuation."
           value={customRole}
           onChange={(event) => setCustomRole(event.target.value)} placeholder="e.g. Delivery coordinator" />
       </label>}
       <button className="primary" disabled={!approved || provider.loading || Boolean(provider.error)}>
         {editing ? "Save changes" : "Add to roster"}
       </button>
       {editing && <button className="secondary" type="button" onClick={() => startEdit(null)}>Cancel</button>}
     </form>
   </section>
   <section className="panel">
     <div className="panel-heading"><div><h2>Your staff roster</h2>
       <p>{staff.data.filter((entry) => entry.active).length} active · {staff.data.length} total</p>
     </div><ExportActions rows={staff.data} fileName="Staff roster" columns={[
       { label: "Name", value: (row) => row.name }, { label: "Phone", value: (row) => row.phone },
       { label: "Role", value: (row) => row.role }, { label: "Status", value: (row) => row.active ? "Active" : "Inactive" },
     ]} /></div>
     {staff.loading ? <Loading /> : staff.error ? <ErrorState message={staff.error} />
       : staff.data.length === 0 ? <Empty text="No staff members are listed yet." />
         : <div className="records">{staff.data.map((entry) => <div className="record-row" key={entry.id}>
           <div className="record-main"><strong>{entry.name}</strong><span>{entry.phone} · {entry.role}</span></div>
           <span className={entry.active ? "badge delivered" : "badge rejected"}>{entry.active ? "Active" : "Inactive"}</span>
           <button className="secondary small" disabled={!approved} onClick={() => startEdit(entry)}>Edit</button>
           <button className="secondary small" disabled={!approved} onClick={() => toggle(entry)}>
             {entry.active ? "Deactivate" : "Activate"}
           </button>
           <button className="danger small" disabled={!approved} onClick={() => remove(entry)}>Remove</button>
         </div>)}</div>}
   </section>
 </>;
}

function ProviderProfile({ uid, setNotice, setBusy, theme, setTheme }) {
  const provider = useProviderProfile(uid);
  const profile = provider.data || {};
  const [supportsPickupDelivery, setSupportsPickupDelivery] = useState(true);
  const [supportsDropoff, setSupportsDropoff] = useState(true);
  useEffect(() => {
    if (provider.loading || provider.error) return;
    setSupportsPickupDelivery(profile.supportsPickupDelivery !== false);
    setSupportsDropoff(profile.supportsDropoff !== false);
  }, [provider.loading, provider.error, profile.supportsPickupDelivery, profile.supportsDropoff]);

  async function save(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const businessName = String(values.get("businessName") || "").trim();
    const phone = String(values.get("phone") || "").trim();
    const address = String(values.get("address") || "").trim();
    if (!businessName || !phone || !address) {
      setNotice("Business name, phone, and address are required.");
      return;
    }
    if (!supportsPickupDelivery && !supportsDropoff) {
      setNotice("Choose at least one booking option: pickup and delivery or customer drop-off.");
      return;
    }
    const updates = {
      businessName,
      ownerName: String(values.get("ownerName") || "").trim(),
      phone,
      address,
      description: String(values.get("description") || "").trim(),
      operatingHours: String(values.get("operatingHours") || "").trim(),
      isOpen: values.get("isOpen") === "on",
      supportsPickupDelivery,
      supportsDropoff,
    };
    setBusy(true);
    try {
      await updateDoc(doc(db, "providers", uid), updates);
      setNotice("Business profile updated.");
      await provider.reload();
    } catch (error) { setNotice(error.message || "Could not save business profile."); }
    finally { setBusy(false); }
  }
  const services = profile.services || [];
  const availableCount = services.filter((service) => service.isAvailable !== false).length;
  const formKey = JSON.stringify([
    profile.businessName, profile.ownerName, profile.phone, profile.address,
    profile.description, profile.operatingHours, profile.isOpen,
    profile.supportsPickupDelivery, profile.supportsDropoff,
  ]);
  return <>
    <PageHeading eyebrow="BUSINESS SETTINGS" title="Business profile" description="Keep your public business details and booking availability current across the app and web." />
    <section className="panel profile-panel">
      {provider.loading ? <Loading /> : provider.error ? <div className="settings-error" role="alert">
        <ErrorState message={provider.error} /><button className="secondary" onClick={provider.reload}>Retry</button>
      </div> : <>
        {profile?.isApproved !== true && <div className="notice warn">Your profile is awaiting administrator approval. Order status changes and rider assignments will be available after approval.</div>}
        <div className="profile-summary"><div><span className="detail-label">Profile completeness</span><strong>{[profile.businessName, profile.phone, profile.address, profile.description, profile.operatingHours, services.length > 0].filter(Boolean).length} / 6 details</strong></div><div><span className="detail-label">Service catalog</span><strong>{availableCount} available · {services.length} total</strong></div><div><span className="detail-label">Customer bookings</span><strong>{profile.isOpen === false ? "Paused" : "Accepting bookings"}</strong></div></div>
        <div className="appearance-setting">
          <div><strong>Dark mode</strong><span>Use a darker appearance across the operations workspace.</span></div>
          <label className="switch-control">
            <input type="checkbox" checked={theme === "dark"}
              onChange={(event) => setTheme(event.target.checked ? "dark" : "light")}
              aria-label="Toggle dark mode" />
            <span aria-hidden="true" />
          </label>
        </div>
        <form key={formKey} className="profile-form" onSubmit={save}>
          <div className="settings-section-heading wide"><h2>Business details</h2>
            <p>These details are shared with customers in WashLink.</p></div>
          <label>Business name<input name="businessName" autoComplete="organization" required maxLength="100" defaultValue={profile.businessName || ""} /></label>
          <label>Owner name<input name="ownerName" autoComplete="name" maxLength="100" defaultValue={profile.ownerName || ""} /></label>
          <label>Business phone<input name="phone" type="tel" autoComplete="tel" required maxLength="30" defaultValue={profile.phone || ""} /></label>
          <label>Business address<input name="address" autoComplete="street-address" required maxLength="200" defaultValue={profile.address || ""} /></label>
          <label className="wide">Description<textarea name="description" rows="3" maxLength="1000" defaultValue={profile.description || ""} /></label>
          <div className="settings-section-heading wide"><h2>Availability and booking options</h2>
            <p>Choose when your business accepts requests and how customers can access your service.</p></div>
          <label>Operating hours<input name="operatingHours" maxLength="100" placeholder="Mon–Sat, 8am–6pm" defaultValue={profile.operatingHours || ""} /></label>
          <label className="availability-check"><input name="isOpen" type="checkbox" defaultChecked={profile.isOpen !== false} /> Accept new bookings</label>
          <fieldset className="booking-mode-options wide">
            <legend>Booking options</legend>
            <p>Choose how customers can bring laundry to your business.</p>
            <label className="availability-check"><input type="checkbox" checked={supportsPickupDelivery}
              onChange={(event) => setSupportsPickupDelivery(event.target.checked)} /> Pickup and delivery</label>
            <label className="availability-check"><input type="checkbox" checked={supportsDropoff}
              onChange={(event) => setSupportsDropoff(event.target.checked)} /> Customer drop-off</label>
          </fieldset>
          <div className="settings-form-actions wide">
            <button className="primary">Save profile</button>
          </div>
        </form>
      </>}
    </section>
  </>;
}

function ProviderMessages({ uid, setNotice, setBusy }) {
  const [chats, setChats] = useState({ data: [], loading: true, error: "" });
  const [refreshKey, setRefreshKey] = useState(0);
  const [selectedId, setSelectedId] = useState("");
  const [search, setSearch] = useState("");
  const [messages, setMessages] = useState({ data: [], loading: false, error: "" });
  useEffect(() => onSnapshot(query(
    collection(db, "chats"),
    where("providerId", "==", uid),
    orderBy("lastMessageAt", "desc"),
    limit(200),
  ), (snapshot) => {
    const data = snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() }));
    setChats({ data, loading: false, error: "" });
    setSelectedId((current) => current && data.some((chat) => chat.id === current)
      ? current : "");
  }, (error) => {
    setChats({ data: [], loading: false, error: error.code === "permission-denied"
      ? `Firestore denied access to conversations in washlink-main for provider UID ${uid}. Confirm each chat's providerId matches this exact UID and that the deployed chat rules allow provider reads.`
      : error.message || "Could not load conversations." });
    setSelectedId("");
  }),
  [uid, refreshKey]);
  const selected = chats.data.find((chat) => chat.id === selectedId);
  useEffect(() => {
    if (!selectedId) {
      setMessages({ data: [], loading: false, error: "" });
      return undefined;
    }
    setMessages({ data: [], loading: true, error: "" });
    return onSnapshot(query(collection(db, "chats", selectedId, "messages"), limit(500)),
    (snapshot) => setMessages({
      data: snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() }))
        .sort((a, b) => timestampMillis(a.createdAt) - timestampMillis(b.createdAt)),
      loading: false,
      error: "",
    }), (error) => setMessages({ data: [], loading: false, error: error.code === "permission-denied"
      ? `Firestore denied access to this conversation's messages for provider UID ${uid}. Confirm the chat's providerId matches this UID and deployed rules allow participants to read messages.`
      : error.message || "Could not load messages." }));
  }, [selectedId, uid]);
  useEffect(() => {
    if (!selectedId) return;
    updateDoc(doc(db, "chats", selectedId), { unreadProvider: 0 })
      .catch((error) => setNotice(error.message || "Could not mark messages as read."));
  }, [selectedId, setNotice]);
  const visibleChats = chats.data.filter((chat) => [
    chat.customerName, chat.customerPhone, chat.lastMessage,
  ].some((value) => matchesSearch(value, search)));

  async function send(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const text = String(new FormData(form).get("message") || "").trim();
    if (!selected || !text || text.length > 2000) {
      setNotice("Select a conversation and enter a message (1–2,000 characters).");
      return;
    }
    const batch = writeBatch(db);
    const chatRef = doc(db, "chats", selected.id);
    batch.set(doc(collection(db, "chats", selected.id, "messages")), {
      senderId: uid,
      senderRole: "provider",
      text,
      createdAt: serverTimestamp(),
    });
    batch.update(chatRef, {
      lastMessage: text,
      lastMessageAt: serverTimestamp(),
      lastSenderId: uid,
      unreadCustomer: increment(1),
    });
    setBusy(true);
    try {
      await batch.commit();
      form.reset();
    } catch (error) { setNotice(error.message || "Could not send message."); }
    finally { setBusy(false); }
  }
  return <>
    <PageHeading eyebrow="CUSTOMER SUPPORT" title="Messages" description="Read and reply to customer conversations from the app or the provider workspace." action={<button className="secondary" onClick={() => setRefreshKey((value) => value + 1)}>Refresh</button>} />
    <section className="panel messages-panel">
      <label className="search-control"><span>Search conversations</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Customer, phone, message…" /></label>
      {chats.loading ? <Loading /> : chats.error ? <ErrorState message={chats.error} /> : chats.data.length === 0
        ? <Empty text="No customer conversations yet. Chats started in the WashLink app will appear here." />
        : visibleChats.length === 0 ? <Empty text="No conversations match your search." />
        : <div className="messages-layout">
          <div className="conversation-list">{visibleChats.map((chat) => <button key={chat.id}
            className={selectedId === chat.id ? "conversation-item selected" : "conversation-item"}
            onClick={() => setSelectedId(chat.id)}>
            <strong>{chat.customerName || "Customer"}</strong>
            <span>{chat.lastMessage || "Conversation started"}</span>
            <small>{shortDateTime(chat.lastMessageAt)}</small>
            {Number(chat.unreadProvider || 0) > 0
              && <b className="unread-count">{Number(chat.unreadProvider) > 99 ? "99+" : chat.unreadProvider}</b>}
          </button>)}</div>
          <div className="message-thread">
            {!selected ? <Empty text="Choose a conversation to view messages." /> : <>
              <div className="thread-heading"><strong>{selected.customerName || "Customer"}</strong><span>{selected.customerPhone || ""}</span></div>
              <div className="thread-messages">{messages.loading ? <Loading /> : messages.error ? <ErrorState message={messages.error} /> : messages.data.map((message) =>
                <div key={message.id} className={message.senderId === uid ? "message-bubble mine" : "message-bubble"}>
                  <span>{message.text}</span><small>{chatMessageTime(message.createdAt)}</small>
                </div>)}</div>
              <form className="message-form" onSubmit={send}><input name="message" required maxLength="2000" placeholder="Write a reply…" /><button className="primary">Send</button></form>
            </>}
          </div>
        </div>}
    </section>
  </>;
}

function ProviderPayouts({ uid, setNotice, setBusy }) {
  const saved = useProviderPayout(uid);
  const current = saved.data || {};
  const [destinationType, setDestinationType] = useState("");
  const [banks, setBanks] = useState([]);
  const [branches, setBranches] = useState([]);
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [banksLoading, setBanksLoading] = useState(Boolean(functions));
  const [banksRefreshKey, setBanksRefreshKey] = useState(0);
  const [bankCode, setBankCode] = useState("");
  const [branchCode, setBranchCode] = useState("");
  const [optionsError, setOptionsError] = useState("");

  useEffect(() => {
    if (!functions) {
      setBanksLoading(false);
      setOptionsError("Payout options are unavailable because the server is not configured.");
      return undefined;
    }
    let active = true;
    setBanksLoading(true);
    setOptionsError("");
    httpsCallable(functions, "getUgandaPayoutBanks")()
      .then((result) => {
        if (active) setBanks(Array.isArray(result.data) ? result.data : []);
      })
      .catch((error) => {
        if (active) setOptionsError(error.message || "Could not load Uganda banks.");
      })
      .finally(() => { if (active) setBanksLoading(false); });
    return () => { active = false; };
  }, [banksRefreshKey]);
  useEffect(() => {
    let active = true;
    const bank = banks.find((entry) => entry.code === bankCode);
    if (!bank?.hasBranches) {
      setBranches([]);
      setBranchCode("");
      setBranchesLoading(false);
      return () => { active = false; };
    }
    setBranches([]);
    setBranchesLoading(true);
    setOptionsError("");
    httpsCallable(functions, "getUgandaPayoutBranches")({ bankId: bank.id })
      .then((result) => {
        if (!active) return;
        const values = Array.isArray(result.data) ? result.data : [];
        setBranches(values);
        setBranchCode((previous) => values.some((entry) => entry.code === previous)
          ? previous : "");
        setBranchesLoading(false);
      })
      .catch((error) => {
        if (!active) return;
        setOptionsError(error.message || "Could not load bank branches.");
        setBranchesLoading(false);
      });
    return () => { active = false; };
  }, [bankCode, banks]);

  useEffect(() => {
    if (!current.destinationType) return;
    setDestinationType(current.destinationType);
    setBankCode(current.destinationType === "bank" ? current.accountBank || "" : "");
    setBranchCode(current.destinationBranchCode || "");
  }, [current.destinationType, current.accountBank, current.destinationBranchCode]);

  async function save(event) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const data = {
      destinationType,
      beneficiaryName: String(values.get("beneficiaryName") || "").trim(),
      accountNumber: String(values.get("accountNumber") || "").trim(),
    };
    if (destinationType === "bank") {
      data.accountBank = bankCode;
      data.destinationBranchCode = branchCode || null;
    }
    setBusy(true);
    try {
      await httpsCallable(functions, "saveProviderPayoutProfile")(data);
      setNotice("Payout destination saved securely.");
    } catch (error) { setNotice(error.message || "Could not save payout settings."); }
    finally { setBusy(false); }
  }

  const selectedBank = banks.find((bank) => bank.code === bankCode);
  return <>
    <PageHeading eyebrow="PAYMENT DESTINATION" title="Payout settings" description="Save a Uganda bank or mobile-money destination for eligible verified online order payouts." />
    <section className="panel profile-panel">
      {saved.loading ? <Loading /> : saved.error ? <div className="settings-error" role="alert">
        <ErrorState message={saved.error} /><button className="secondary" onClick={saved.reload}>Retry</button>
      </div> : <>
        <div className="notice" role="note">Only your signed-in account can read these payout details. Cash-on-delivery orders are collected directly by your business.</div>
        {current.accountNumber && <p className="payout-summary">Current destination: <strong>{current.beneficiaryName}</strong> · {current.destinationType} ending {current.accountNumber.slice(-4)}</p>}
        {optionsError && <div className="settings-options-error" role="alert">
          <span>{optionsError}</span><button className="secondary small" type="button"
            onClick={() => setBanksRefreshKey((key) => key + 1)}>Retry</button>
        </div>}
        <form key={JSON.stringify([
          current.destinationType, current.beneficiaryName, current.accountNumber,
          current.accountBank, current.destinationBranchCode,
        ])} className="profile-form payout-form" onSubmit={save}>
          <div className="settings-section-heading wide"><h2>Destination details</h2>
            <p>Choose where eligible online payouts should be sent. Account details remain private.</p></div>
          <label className="wide">Destination type<select required value={destinationType} onChange={(event) => setDestinationType(event.target.value)}>
            <option value="">Choose destination</option><option value="bank">Uganda bank account</option><option value="airtel">Airtel Money</option><option value="mtn">MTN Mobile Money</option>
          </select></label>
          {destinationType === "bank" && <>
            <label>Bank<select required value={bankCode} disabled={banksLoading || Boolean(optionsError)}
              onChange={(event) => { setBankCode(event.target.value); setBranchCode(""); }}>
              <option value="">{banksLoading ? "Loading banks…" : "Choose bank"}</option>{banks.map((bank) => <option key={bank.code} value={bank.code}>{bank.name}</option>)}
            </select></label>
            {selectedBank?.hasBranches && <label>Branch<select required value={branchCode} disabled={branchesLoading}
              onChange={(event) => setBranchCode(event.target.value)}>
              <option value="">{branchesLoading ? "Loading branches…" : "Choose branch"}</option>
              {branches.map((branch) => <option key={branch.code} value={branch.code}>{branch.name}</option>)}
            </select></label>}
          </>}
          <label>Beneficiary full name<input name="beneficiaryName" autoComplete="name" required maxLength="100" defaultValue={current.beneficiaryName || ""} /></label>
          <label>{destinationType === "bank" ? "Bank account number" : "Uganda phone number (2567XXXXXXXX)"}
            <input name="accountNumber" inputMode={destinationType === "bank" ? "numeric" : "tel"}
              autoComplete={destinationType === "bank" ? "off" : "tel"} required minLength="5" maxLength="32"
              defaultValue={current.accountNumber || ""} />
          </label>
          <div className="settings-form-actions wide">
            <button className="primary" disabled={!functions || (destinationType === "bank"
              && (!bankCode || (selectedBank?.hasBranches && !branchCode)
                || banksLoading || branchesLoading || Boolean(optionsError)))}>
              Save payout destination
            </button>
          </div>
        </form>
      </>}
    </section>
  </>;
}

function OrderCard({ booking, riders, onStatus, onAssign, canManageOrders }) {
  const [expanded, setExpanded] = useState(false);
  const [riderId, setRiderId] = useState(booking.assignedRiderId || "");
  useEffect(() => setRiderId(booking.assignedRiderId || ""), [booking.assignedRiderId]);
  const next = STATUS_FLOW[booking.status] || [];
  const hasSelectedActiveRider = riders.some((rider) => rider.id === riderId);
  return <article className="order-card" aria-label={`Order ${booking.id}, ${statusLabel(booking.status)}`}>
    <div className="order-top"><div><span className="order-number">Order #{booking.id}</span><span className="muted inline"> · {shortDate(booking.createdAt)}</span></div><span className={`badge ${String(booking.status || "").toLowerCase()}`}>{statusLabel(booking.status)}</span></div>
    <div className="order-grid"><div><span className="detail-label">Customer</span><strong>{booking.customerName || "Customer"}</strong><span>{booking.customerPhone || "Phone unavailable"}</span></div><div><span className="detail-label">Service</span><strong>{booking.serviceName || "Laundry service"}</strong><span>{booking.itemCount || 0} items · {fulfillmentLabel(booking.serviceType)}</span></div><div><span className="detail-label">Schedule</span><strong>{booking.scheduledDateTime || "Not scheduled"}</strong><span>{booking.address || "Address unavailable"}</span></div><div><span className="detail-label">Payment</span><strong>{money(booking.total)}</strong><span>{booking.paymentStatus || "PENDING"} · {booking.paymentProvider || "—"}</span></div></div>
    {booking.assignedRiderName && <div className="assigned-rider">Rider: {booking.assignedRiderName} · {booking.assignedRiderPhone || "No phone"}</div>}
    {expanded && <div className="order-extra"><span><b>Instructions:</b> {booking.specialInstructions || "None provided"}</span><span><b>Service &amp; logistics subtotal:</b> {money(booking.subtotal)}</span><span><b>Service fee:</b> {money(serviceFeeForDisplay(booking))} · <b>Total:</b> {money(booking.total)}</span><span><b>Payment method:</b> {booking.paymentMethod || "Not selected"}</span></div>}
    <div className="order-actions"><button className="text-button" onClick={() => setExpanded(!expanded)}>{expanded ? "Hide details" : "More details"}</button>
      {["ACCEPTED", "PICKED_UP", "WASHING", "DRYING", "READY", "OUT_FOR_DELIVERY"].includes(booking.status) && <>
        <select className="rider-select" aria-label={`Rider for booking ${booking.id}`} value={riderId}
          onChange={(event) => setRiderId(event.target.value)}>
          <option value="">Select rider</option>
          {riderId && !hasSelectedActiveRider && <option value={riderId} disabled>
            {booking.assignedRiderName || "Previously assigned rider"} · unavailable
          </option>}
          {riders.map((rider) => <option key={rider.id} value={rider.id}>{rider.name} · {rider.phone}</option>)}
          </select>
          <button className="secondary small" disabled={!canManageOrders || !hasSelectedActiveRider}
            onClick={() => onAssign(booking, riderId)}>
          {booking.assignedRiderId ? "Change rider" : "Assign rider"}
        </button>
      </>}
      {next.map((status) => <button key={status} className={status === "REJECTED" ? "danger small" : "primary small"}
        disabled={!canManageOrders} onClick={() => onStatus(booking, status)}>{statusLabel(status)}</button>)}
    </div>
  </article>;
}

function ProviderReports({ uid }) {
  const [range, setRange] = useState(30);
  const [reportType, setReportType] = useState("earnings");
  const [paymentFilter, setPaymentFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("newest");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const bookings = useProviderBookings(uid);
  const cutoff = range === 0 ? 0 : Date.now() - range * 24 * 60 * 60 * 1000;
  const periodBookings = bookings.data.filter((b) => timestampMillis(b.createdAt) >= cutoff);
  const complete = periodBookings.filter((b) => b.status === "DELIVERED");
  const cancelled = periodBookings.filter((b) => ["CANCELLED", "REJECTED"].includes(b.status)).length;
  const revenue = periodBookings.reduce((sum, b) => sum + verifiedRevenue(b), 0);
  const cashRevenue = periodBookings.reduce((sum, booking) => sum
    + (booking.status === "DELIVERED" && booking.paymentProvider?.toLowerCase() === "cash"
      ? Number(booking.total || 0) : 0), 0);
  const onlineRevenue = periodBookings.reduce((sum, booking) => sum
    + (booking.status === "DELIVERED" && isOnlinePaymentProvider(booking.paymentProvider)
      && booking.paymentStatus?.toUpperCase() === "PAID"
      ? Number(booking.subtotal || 0) : 0), 0);
  const reportConfigMap = {
    earnings: "Earning report", orders: "Order report",
    services: "Service report", customers: "Customer report",
  };
  const sourceBookings = periodBookings.filter((booking) => {
    const provider = booking.paymentProvider?.toLowerCase();
    const matchesPayment = paymentFilter === "ALL"
      || (paymentFilter === "CASH" && provider === "cash")
      || (paymentFilter === "ONLINE" && isOnlinePaymentProvider(provider));
    return matchesPayment && (statusFilter === "ALL" || booking.status === statusFilter);
  });
  const reportRows = (() => {
    if (reportType === "services") {
      const services = new Map();
      sourceBookings.forEach((booking) => {
        const name = booking.serviceName || "Laundry service";
        const current = services.get(name) || { id: name, name, orders: 0, revenue: 0 };
        current.orders++;
        current.revenue += verifiedRevenue(booking);
        services.set(name, current);
      });
      return [...services.values()];
    }
    if (reportType === "customers") {
      const customers = new Map();
      sourceBookings.forEach((booking) => {
        const id = booking.customerId || booking.customerName || "unknown";
        const current = customers.get(id) || {
          id, name: booking.customerName || "Customer", orders: 0, items: 0, revenue: 0,
          paymentProvider: booking.paymentProvider || "—",
        };
        current.orders++;
        current.items += Number(booking.itemCount || 0);
        current.revenue += verifiedRevenue(booking);
        customers.set(id, current);
      });
      return [...customers.values()];
    }
    return sourceBookings.filter((booking) => reportType !== "earnings"
      || verifiedRevenue(booking) > 0);
  })();
  const filteredRows = reportRows.filter((row) => {
    const values = [row.id, row.name, row.customerName, row.serviceName, row.status,
      row.paymentProvider, row.paymentStatus];
    return values.some((value) => matchesSearch(value, search));
  }).sort((a, b) => sort === "oldest"
    ? timestampMillis(a.createdAt) - timestampMillis(b.createdAt)
    : sort === "amount"
      ? Number(b.revenue ?? b.total ?? 0) - Number(a.revenue ?? a.total ?? 0)
      : sort === "name"
        ? String(a.name || a.customerName || "").localeCompare(String(b.name || b.customerName || ""))
        : sort === "count"
          ? Number(b.orders || 0) - Number(a.orders || 0)
          : timestampMillis(b.createdAt) - timestampMillis(a.createdAt));
  const pageCount = Math.max(1, Math.ceil(filteredRows.length / pageSize));
  const visibleRows = filteredRows.slice(page * pageSize, (page + 1) * pageSize);
  const reportConfig = {
    earnings: { label: "Earning report", title: "Verified earnings", description: "Completed orders with recorded cash or verified online payment." },
    orders: { label: "Order report", title: "Order report", description: "Booking status, customer, service, and payment details for the selected period." },
    services: { label: "Service report", title: "Service performance", description: "Booking volume and verified collections grouped by laundry service." },
    customers: { label: "Customer report", title: "Customer report", description: "Customer booking frequency, items, and verified collections." },
  }[reportType];
  const exportColumns = reportType === "services"
    ? [{ label: "Service", value: (row) => row.name }, { label: "Bookings", value: (row) => row.orders },
      { label: "Verified revenue (UGX)", value: (row) => row.revenue }]
    : reportType === "customers"
      ? [{ label: "Customer", value: (row) => row.name }, { label: "Bookings", value: (row) => row.orders },
        { label: "Items", value: (row) => row.items }, { label: "Verified revenue (UGX)", value: (row) => row.revenue }]
      : [{ label: "Booking", value: (row) => row.id }, { label: "Customer", value: (row) => row.customerName || "" },
        { label: "Service", value: (row) => row.serviceName || "" }, { label: "Created", value: (row) => shortDate(row.createdAt) },
        { label: "Status", value: (row) => row.status || "" }, { label: "Payment status", value: (row) => row.paymentStatus || "" },
        { label: "Total (UGX)", value: (row) => Number(row.total || 0) }];
  useEffect(() => { setPage(0); }, [range, reportType, paymentFilter, statusFilter, search, sort, pageSize]);
  return <>
    <PageHeading eyebrow="BUSINESS PERFORMANCE" title="Reports" description="Review your earnings, orders, service performance, and customers."
      action={<div className="heading-actions"><ExportActions rows={filteredRows} fileName={`WashLink-${reportType}-report`} columns={exportColumns} />
        <button className="secondary" onClick={bookings.reload}>Refresh</button></div>} />
    <div className="provider-report-tabs" role="tablist" aria-label="Provider reports">
      {Object.entries(reportConfigMap).map(([key, config]) => <button key={key} role="tab"
        id={`provider-report-tab-${key}`} aria-controls="provider-report-results"
        aria-selected={reportType === key} tabIndex={reportType === key ? 0 : -1}
        className={reportType === key ? "selected" : ""}
        onKeyDown={(event) => {
          const tabs = [...event.currentTarget.parentElement.querySelectorAll('[role="tab"]')];
          const currentIndex = tabs.indexOf(event.currentTarget);
          const nextIndex = event.key === "ArrowRight" ? (currentIndex + 1) % tabs.length
            : event.key === "ArrowLeft" ? (currentIndex - 1 + tabs.length) % tabs.length
              : event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : -1;
          if (nextIndex >= 0) {
            event.preventDefault();
            tabs[nextIndex].focus();
            tabs[nextIndex].click();
          }
        }}
        onClick={() => { setReportType(key); setSort(["services", "customers"].includes(key) ? "count" : "newest"); }}>{config}</button>)}
    </div>
    <div className="provider-report-controls">
      <div className="range-switch" role="group" aria-label="Report date range">
        {[[7, "7 days"], [30, "30 days"], [0, "All time"]].map(([days, text]) =>
          <button key={days} className={range === days ? "selected" : ""} aria-pressed={range === days}
            onClick={() => setRange(days)}>{text}</button>)}
      </div>
      <label className="search-control"><span>Payment method</span><select value={paymentFilter}
        onChange={(event) => setPaymentFilter(event.target.value)}>
        <option value="ALL">All payment methods</option><option value="CASH">Cash</option>
        <option value="ONLINE">Verified online</option></select></label>
      <label className="search-control"><span>Order status</span><select value={statusFilter}
        onChange={(event) => setStatusFilter(event.target.value)}>
        <option value="ALL">All statuses</option>{[...new Set(periodBookings.map((booking) => booking.status).filter(Boolean))]
          .sort().map((status) => <option key={status} value={status}>{statusLabel(status)}</option>)}
      </select></label>
      <label className="search-control provider-report-search"><span>Search report</span>
        <input value={search} onChange={(event) => setSearch(event.target.value)}
          placeholder="Booking, customer, service…" /></label>
      <label className="search-control"><span>Sort by</span><select value={sort} onChange={(event) => setSort(event.target.value)}>
        {["services", "customers"].includes(reportType)
          ? <><option value="count">Most bookings</option><option value="name">Name A-Z</option>
            <option value="amount">Highest revenue</option></>
          : <><option value="newest">Newest</option><option value="oldest">Oldest</option>
            <option value="amount">Highest amount</option></>}
      </select></label>
    </div>
    {bookings.loading ? <DashboardLoading /> : bookings.error
      ? <DashboardError message={bookings.error} onRetry={bookings.reload} /> : <>
      <div className="stats-grid provider-report-stats">
        <StatCard label="Bookings" value={periodBookings.length} note={range ? `Created in the last ${range} days` : "All time"} />
        <StatCard label="Delivered" value={complete.length} note="Orders marked delivered" accent="accent-blue" />
        <StatCard label="Awaiting action" value={periodBookings.filter((b) => b.status === "BOOKED").length}
          note="New requests" accent="accent-amber" />
        <StatCard label="Cancelled / rejected" value={cancelled} note="Closed without delivery" />
        <StatCard label="Collected revenue" value={money(revenue)}
          note="Cash totals + paid online subtotal" accent="accent-green" />
      </div>
      {reportType === "earnings" && <section className="panel provider-report-revenue"><div className="panel-heading">
        <div><h2>Revenue by payment method</h2><p>Only delivered cash and verified online payments count.</p></div>
        <strong>{money(revenue)}</strong></div>
        <div className="report-rows">
          <div className="report-row"><span>Cash</span><strong>{money(cashRevenue)}</strong></div>
          <div className="report-row"><span>Verified online payment</span><strong>{money(onlineRevenue)}</strong></div>
        </div>
      </section>}
      <section className="panel provider-report-table-panel" id="provider-report-results"
        role="tabpanel" aria-labelledby={`provider-report-tab-${reportType}`}>
        <div className="panel-heading"><div><h2>{reportConfig.title}</h2><p>{reportConfig.description}</p></div>
          <span className="dashboard-period-label">{filteredRows.length} records</span></div>
        {filteredRows.length === 0 ? <Empty text="No report records match the selected filters." />
          : <div className="table-scroll"><table className="provider-report-table">
            <thead><tr>{reportType === "services"
              ? <><th scope="col">Service</th><th scope="col">Bookings</th><th scope="col">Verified revenue</th></>
              : reportType === "customers"
                ? <><th scope="col">Customer</th><th scope="col">Bookings</th><th scope="col">Items</th><th scope="col">Verified revenue</th></>
                : <><th>{reportType === "earnings" ? "Earning / booking ID" : "Order ID"}</th>
                  <th scope="col">Date</th><th scope="col">Customer</th><th scope="col">Service</th><th scope="col">Payment method</th>
                  <th scope="col">Total</th><th scope="col">Status</th></>}</tr></thead>
            <tbody>{visibleRows.map((row) => reportType === "services"
              ? <tr key={row.id}><td><strong>{row.name}</strong></td><td>{row.orders}</td><td>{money(row.revenue)}</td></tr>
              : reportType === "customers"
                ? <tr key={row.id}><td><strong>{row.name}</strong></td><td>{row.orders}</td>
                  <td>{row.items}</td><td>{money(row.revenue)}</td></tr>
                : <tr key={row.id}><td className="mono">#{String(row.id).slice(0, 10)}</td>
                  <td>{shortDate(row.createdAt)}</td><td>{row.customerName || "Customer"}</td>
                  <td>{row.serviceName || "Laundry service"}</td>
                  <td>{row.paymentProvider || "—"}</td><td>{money(row.total)}</td>
                  <td><span className={`badge ${String(row.status || "").toLowerCase()}`}>{statusLabel(row.status)}</span></td></tr>)}</tbody>
          </table></div>}
        {filteredRows.length > 0 && <div className="admin-users-pagination"><label>Rows per page
          <select value={pageSize} onChange={(event) => setPageSize(Number(event.target.value))}>
            {[10, 20, 50].map((size) => <option key={size} value={size}>{size}</option>)}
          </select></label><span>{page * pageSize + 1}–{Math.min((page + 1) * pageSize, filteredRows.length)} of {filteredRows.length}</span>
          <button className="secondary small" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <button className="secondary small" disabled={page + 1 >= pageCount} onClick={() => setPage(page + 1)}>Next</button>
        </div>}
      </section>
    </>}
  </>;
}

function ProviderRiders({ uid, setNotice }) {
  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState("ALL");
  const [sort, setSort] = useState("newest");
  const [view, setView] = useState("list");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [selectedId, setSelectedId] = useState("");
  const [editor, setEditor] = useState(null);
  const riders = useProviderRiders(uid);
  const bookings = useProviderBookings(uid);
  const filteredRiders = riders.data.filter((rider) => [
    rider.name, rider.phone, rider.isActive ? "active" : "inactive",
  ].some((value) => matchesSearch(value, search))
    && (activeFilter === "ALL" || (activeFilter === "ACTIVE" ? rider.isActive : !rider.isActive)));
  filteredRiders.sort((a, b) => sort === "name"
    ? String(a.name || "").localeCompare(String(b.name || ""))
    : Number(b.createdAt || 0) - Number(a.createdAt || 0));
  const pageCount = Math.max(1, Math.ceil(filteredRiders.length / pageSize));
  const visibleRiders = filteredRiders.slice(page * pageSize, (page + 1) * pageSize);
  const selectedRider = riders.data.find((rider) => rider.id === selectedId);
  const riderBookings = selectedRider ? bookings.data.filter((booking) =>
    booking.assignedRiderId === selectedRider.id
      || (!booking.assignedRiderId && booking.assignedRiderName === selectedRider.name)) : [];
  const activeAssignments = riderBookings.filter((booking) =>
    !["DELIVERED", "CANCELLED", "REJECTED"].includes(String(booking.status || "").toUpperCase()));
  useEffect(() => { setPage(0); }, [search, activeFilter, sort, pageSize]);
  async function save(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") || "").trim();
    const phone = String(form.get("phone") || "").trim();
    if (name.length < 2 || name.length > 80 || phone.length < 7 || phone.length > 30) {
      setNotice("Enter a rider name (2–80 characters) and phone number (7–30 characters).");
      return;
    }
    try {
      if (editor?.id) {
        await updateDoc(doc(db, "providers", uid, "riders", editor.id), { name, phone });
        setNotice("Rider details updated.");
        setSelectedId(editor.id);
      } else {
        const rider = await addDoc(collection(db, "providers", uid, "riders"), {
          name, phone, isActive: true, createdAt: Date.now(),
        });
        setNotice("Rider added.");
        setSelectedId(rider.id);
      }
      setEditor(null);
      await riders.reload();
    } catch (error) { setNotice(error.message || "Could not save rider."); }
  }
  async function toggle(rider) {
    try {
      await updateDoc(doc(db, "providers", uid, "riders", rider.id), { isActive: !rider.isActive });
      setNotice(rider.isActive ? "Rider deactivated." : "Rider activated.");
      await riders.reload();
    } catch (error) { setNotice(error.message || "Could not update rider."); }
  }
  return <>
    <PageHeading eyebrow="DELIVERY TEAM" title="Riders" description="Manage your delivery contacts and see which riders are currently assigned to orders."
      action={<button className="primary" onClick={() => setEditor({})}>＋ Add rider</button>} />
    {bookings.error && <div className="notice warn" role="alert">Could not load rider assignments. {bookings.error}</div>}
    <section className="panel provider-riders-panel"><div className="panel-heading"><div><h2>Your riders</h2>
      <p>{riders.data.length} total · {riders.data.filter((r) => r.isActive).length} active</p></div>
      <div className="heading-actions"><ExportActions rows={filteredRiders} fileName="Riders" columns={[
        { label: "Name", value: (row) => row.name || "" }, { label: "Phone", value: (row) => row.phone || "" },
        { label: "Status", value: (row) => row.isActive ? "Active" : "Inactive" },
      ]} /><button className="secondary" onClick={riders.reload}>Refresh</button></div></div>
      <div className="provider-riders-toolbar">
        <div className="provider-rider-view-switch" role="group" aria-label="Rider layout">
          <button className={view === "list" ? "selected" : ""} aria-pressed={view === "list"}
            onClick={() => setView("list")}>☷ List</button>
          <button className={view === "grid" ? "selected" : ""} aria-pressed={view === "grid"}
            onClick={() => setView("grid")}>▦ Grid</button>
        </div>
        <label className="search-control provider-rider-search"><span>Search riders</span>
          <input value={search} onChange={(event) => setSearch(event.target.value)}
            placeholder="Search name or phone…" /></label>
        <label className="search-control provider-rider-filter"><span>Availability</span>
          <select value={activeFilter} onChange={(event) => setActiveFilter(event.target.value)}>
          <option value="ALL">All riders</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
        </select></label></div>
      <div className="provider-rider-sort">
        <label>Sort by <select value={sort} onChange={(event) => setSort(event.target.value)}>
          <option value="newest">Newest</option><option value="name">Name</option>
        </select></label>
      </div>
      {riders.loading ? <Loading /> : riders.error ? <ErrorState message={riders.error} /> : riders.data.length === 0
        ? <Empty text="No riders yet. Add your first rider to assign deliveries." />
        : filteredRiders.length === 0 ? <Empty text="No riders match your search or availability filter." />
          : view === "list" ? <div className="provider-riders-table-wrap"><table className="provider-riders-table">
            <thead><tr><th>Rider</th><th>Phone</th><th>Assignments</th><th>Availability</th><th>Actions</th></tr></thead>
            <tbody>{visibleRiders.map((rider) => {
              const assigned = bookings.data.filter((booking) => booking.assignedRiderId === rider.id
                || (!booking.assignedRiderId && booking.assignedRiderName === rider.name));
              return <tr key={rider.id} onClick={() => setSelectedId(rider.id)}>
                <td><button className="provider-rider-identity" onClick={(event) => {
                  event.stopPropagation(); setSelectedId(rider.id);
                }}><span className="provider-rider-avatar">{String(rider.name || "?").slice(0, 1).toUpperCase()}</span>
                  <strong>{rider.name || "Unnamed rider"}</strong></button></td>
                <td>{rider.phone || "No phone"}</td><td>{assigned.length}</td>
                <td><span className={`badge ${rider.isActive ? "delivered" : "rejected"}`}>
                  {rider.isActive ? "Active" : "Inactive"}</span></td>
                <td><div className="provider-rider-actions">
                  <button className="secondary small" onClick={(event) => {
                    event.stopPropagation(); setEditor(rider);
                  }}>Edit</button>
                  <button className="text-button" onClick={(event) => {
                    event.stopPropagation(); toggle(rider);
                  }}>{rider.isActive ? "Deactivate" : "Activate"}</button>
                </div></td>
              </tr>;
            })}</tbody></table></div>
            : <div className="provider-riders-grid">{visibleRiders.map((rider) => {
              const assigned = bookings.data.filter((booking) => booking.assignedRiderId === rider.id
                || (!booking.assignedRiderId && booking.assignedRiderName === rider.name));
              return <article className="provider-rider-card" key={rider.id}>
                <button className="provider-rider-card-main" onClick={() => setSelectedId(rider.id)}>
                  <span className="provider-rider-avatar large">{String(rider.name || "?").slice(0, 1).toUpperCase()}</span>
                  <strong>{rider.name || "Unnamed rider"}</strong><span>{rider.phone || "No phone provided"}</span>
                </button>
                <div className="provider-rider-card-meta">
                  <span className={`badge ${rider.isActive ? "delivered" : "rejected"}`}>
                    {rider.isActive ? "Active" : "Inactive"}</span>
                  <span>{assigned.length} {assigned.length === 1 ? "assignment" : "assignments"}</span>
                </div>
                <div className="provider-rider-actions">
                  <button className="secondary small" onClick={() => setEditor(rider)}>Edit</button>
                  <button className="text-button" onClick={() => toggle(rider)}>
                    {rider.isActive ? "Deactivate" : "Activate"}</button>
                </div>
              </article>;
            })}</div>}
      {!riders.loading && !riders.error && filteredRiders.length > 0 && <div className="admin-users-pagination">
        <label>Rows per page <select value={pageSize} onChange={(event) => setPageSize(Number(event.target.value))}>
          {[10, 20, 50].map((size) => <option key={size} value={size}>{size}</option>)}
        </select></label>
        <span>{page * pageSize + 1}–{Math.min((page + 1) * pageSize, filteredRiders.length)} of {filteredRiders.length}</span>
        <button className="secondary small" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
        <button className="secondary small" disabled={page + 1 >= pageCount} onClick={() => setPage(page + 1)}>Next</button>
      </div>}
    </section>
    {editor && <div className="admin-user-drawer-backdrop" onClick={() => setEditor(null)}>
      <aside className="admin-user-drawer provider-rider-drawer" role="dialog" aria-modal="true"
        aria-labelledby="provider-rider-editor-title" onClick={(event) => event.stopPropagation()}>
        <header className="admin-user-drawer-header"><h2 id="provider-rider-editor-title">
          {editor.id ? "Edit rider" : "Add rider"}</h2>
          <button className="secondary" aria-label="Close" onClick={() => setEditor(null)}>×</button></header>
        <div className="admin-user-drawer-content"><p className="provider-rider-form-note">
          Rider records currently store a name, phone number, and availability. Keep the contact details current
          so active riders can be assigned to orders.</p>
          <form className="provider-rider-edit-form" onSubmit={save}>
            <label>Full name<input name="name" required minLength="2" maxLength="80"
              defaultValue={editor.name || ""} autoFocus /></label>
            <label>Phone number<input name="phone" type="tel" required minLength="7" maxLength="30"
              defaultValue={editor.phone || ""} /></label>
            <div className="provider-rider-form-actions">
              <button type="button" className="secondary" onClick={() => setEditor(null)}>Cancel</button>
              <button className="primary">{editor.id ? "Save changes" : "Add rider"}</button>
            </div>
          </form>
        </div>
      </aside>
    </div>}
    {selectedRider && !editor && <div className="admin-user-drawer-backdrop" onClick={() => setSelectedId("")}>
      <aside className="admin-user-drawer provider-rider-drawer" role="dialog" aria-modal="true"
        aria-labelledby="provider-rider-details-title" onClick={(event) => event.stopPropagation()}>
        <header className="admin-user-drawer-header"><h2 id="provider-rider-details-title">Rider details</h2>
          <button className="secondary" aria-label="Close" onClick={() => setSelectedId("")}>×</button></header>
        <div className="admin-user-drawer-content">
          <div className="provider-rider-profile">
            <span className="provider-rider-avatar large">{String(selectedRider.name || "?").slice(0, 1).toUpperCase()}</span>
            <div><strong>{selectedRider.name || "Unnamed rider"}</strong><span>{selectedRider.phone || "No phone provided"}</span></div>
            <span className={`badge ${selectedRider.isActive ? "delivered" : "rejected"}`}>
              {selectedRider.isActive ? "Active" : "Inactive"}</span>
          </div>
          <div className="provider-rider-summary">
            <div><span>Total assigned</span><strong>{riderBookings.length}</strong></div>
            <div><span>In progress</span><strong>{activeAssignments.length}</strong></div>
            <div><span>Availability</span><strong>{selectedRider.isActive ? "Available" : "Inactive"}</strong></div>
          </div>
          <section className="provider-rider-detail-section"><h3>Assigned orders</h3>
            {bookings.loading ? <Loading /> : riderBookings.length === 0 ? <Empty text="No orders have been assigned to this rider yet." />
              : riderBookings.slice(0, 10).map((booking) => <div className="provider-rider-booking" key={booking.id}>
                <div><strong>#{booking.id.slice(0, 10)}</strong><span>{booking.customerName || "Customer"} · {shortDate(booking.createdAt)}</span></div>
                <span className={`badge ${String(booking.status || "").toLowerCase()}`}>{statusLabel(booking.status)}</span>
              </div>)}
          </section>
          <div className="provider-rider-form-actions">
            <button className="secondary" onClick={() => setEditor(selectedRider)}>Edit rider</button>
            <button className={selectedRider.isActive ? "danger" : "primary"} onClick={() => toggle(selectedRider)}>
              {selectedRider.isActive ? "Deactivate rider" : "Activate rider"}</button>
          </div>
        </div>
      </aside>
    </div>}
  </>;
}

function BookingTable({ bookings, admin = false }) {
  if (!bookings.length) return <Empty text="No bookings found." />;
  return <div className="table-scroll"><table><thead><tr><th>Booking</th><th>{admin ? "Customer / provider" : "Customer"}</th><th>Service</th><th>Created</th><th>Total</th><th>Status</th></tr></thead><tbody>
    {bookings.map((b) => <tr key={b.id}><td className="mono">#{b.id.slice(0, 10)}</td><td>{b.customerName || "Customer"}{admin && <span className="sub-cell">{b.providerName || "Provider"}</span>}</td><td>{b.serviceName || "Laundry service"}</td><td>{shortDate(b.createdAt)}</td><td>{money(b.total)}</td><td><span className={`badge ${String(b.status || "").toLowerCase()}`}>{statusLabel(b.status)}</span></td></tr>)}
  </tbody></table></div>;
}

function verifiedRevenue(booking) {
  if (booking.status !== "DELIVERED") return 0;
  if (booking.paymentProvider?.toLowerCase() === "cash") return Number(booking.total || 0);
  if (isOnlinePaymentProvider(booking.paymentProvider)
      && booking.paymentStatus?.toUpperCase() === "PAID") return Number(booking.subtotal || 0);
  return 0;
}

function isOnlinePaymentProvider(provider) {
  return ["flutterwave", "pesapal"].includes(String(provider || "").toLowerCase());
}

function Loading() { return <div className="loading-row"><div className="spinner" /> Loading…</div>; }
function Empty({ text }) { return <div className="empty-state"><div className="empty-mark">—</div>{text}</div>; }
function ErrorState({ message }) { return <div className="form-error">{message}</div>; }

createRoot(document.getElementById("root")).render(<React.StrictMode><App /></React.StrictMode>);
