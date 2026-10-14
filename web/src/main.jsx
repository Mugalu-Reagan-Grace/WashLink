import React, { useEffect, useRef, useState } from "react";
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
  doc,
  getDoc,
  getDocs,
  increment,
  limit,
  onSnapshot,
  orderBy,
  query,
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

function Brand({ light = false }) {
  return <div className={light ? "brand light" : "brand"}>
    <img className="washlink-logo"
      src={light ? "/washlink-logo-white.png" : "/washlink-logo.png"} alt="WashLink" />
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
    function closeOnEscape(event) {
      if (event.key === "Escape") setMenuOpen(false);
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [menuOpen]);

  if (!firebaseConfigured) return <Setup />;
  if (authLoading) return <div className="center-page"><div className="spinner" /></div>;
  if (!session || !role) {
    return <Login notice={notice} onNotice={setNotice} />;
  }

  const links = role === "admin"
    ? [["overview", "Overview"], ["bookings", "Bookings"], ["providers", "Providers"], ["users", "Users"]]
    : [["overview", "Overview"], ["orders", "Orders"], ["services", "Services"], ["reports", "Reports"], ["riders", "Riders"], ["profile", "Business profile"], ["payouts", "Payout settings"], ["messages", "Messages"]];

  return (
    <div className="workspace">
      {menuOpen && <button className="sidebar-scrim" aria-label="Close navigation menu"
        onClick={() => setMenuOpen(false)} />}
      <aside className={menuOpen ? "sidebar sidebar-open" : "sidebar"}>
        <Brand light />
        <div className="role-chip">{role === "admin" ? "Administrator" : "Laundry provider"}</div>
        <nav aria-label="Workspace navigation">
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
            aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}>
            <span /><span /><span />
          </button>
          <div className="mobile-brand">WashLink <span>Operations</span></div>
          <div className="topbar-spacer" />
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
          <span className="live-dot">Signed in</span>
          {role === "provider" && <ProviderNotifications uid={session.uid} setNotice={setNotice}
            setPage={setPage} setTargetBookingId={setTargetBookingId} />}
          <ProfilePhotoControl uid={session.uid} email={session.email || ""} role={role}
            authPhotoUrl={session.photoURL || ""} setNotice={setNotice} setBusy={setBusy} />
        </header>
        <div className="content">
          {notice && <div className="notice" role="status"><span>{notice}</span><button onClick={() => setNotice("")}>×</button></div>}
          {page === "overview" && role === "admin" && <AdminOverview setPage={setPage} />}
          {page === "bookings" && role === "admin" && <AdminBookings />}
          {page === "providers" && role === "admin" && <AdminProviders setBusy={setBusy} setNotice={setNotice} />}
          {page === "users" && role === "admin" && <AdminUsers setBusy={setBusy} setNotice={setNotice} />}
          {page === "overview" && role === "provider" && <ProviderOverview uid={session.uid} setPage={setPage} />}
          {page === "orders" && role === "provider" && <ProviderOrders uid={session.uid}
            setNotice={setNotice} setBusy={setBusy} targetBookingId={targetBookingId}
            onTargetResolved={setTargetBookingId} />}
          {page === "reports" && role === "provider" && <ProviderReports uid={session.uid} />}
          {page === "riders" && role === "provider" && <ProviderRiders uid={session.uid} setNotice={setNotice} />}
          {page === "services" && role === "provider" && <ProviderServices uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "profile" && role === "provider" && <ProviderProfile uid={session.uid} setNotice={setNotice} setBusy={setBusy} theme={theme} setTheme={setTheme} />}
          {page === "payouts" && role === "provider" && <ProviderPayouts uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "messages" && role === "provider" && <ProviderMessages uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
        </div>
      </main>
      {busy && <div className="busy-overlay"><div className="spinner" /><span>Saving changes…</span></div>}
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
      {error && <div className={error.includes("sent.") ? "notice success" : "form-error"}>{error}</div>}
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
  const users = useCollection(async () => (await getDocs(query(collection(db, "users"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const providers = useCollection(async () => (await getDocs(query(collection(db, "providers"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), orderBy("createdAt", "desc"), limit(300))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)), []);
  const inProgress = bookings.data.filter((b) => !["DELIVERED", "CANCELLED", "REJECTED"].includes(b.status));
  const waitingProviders = providers.data.filter((p) => p.isApproved !== true);
  const collected = bookings.data.reduce((sum, b) => sum + verifiedRevenue(b), 0);
  return <>
    <PageHeading eyebrow="ADMINISTRATION" title="Operations overview" description="A current snapshot of WashLink activity." action={<button className="secondary" onClick={() => window.location.reload()}>Refresh data</button>} />
    {(users.error || providers.error || bookings.error) && <ErrorState message={users.error || providers.error || bookings.error} />}
    {(users.loading || providers.loading || bookings.loading) ? <Loading /> : <>
      <div className="stats-grid">
        <StatCard label="Total users" value={users.data.length} note={`${users.data.filter((u) => u.role === "customer").length} customers`} />
        <StatCard label="Providers" value={providers.data.length} note={`${waitingProviders.length} awaiting approval`} accent="accent-amber" />
        <StatCard label="Open bookings" value={inProgress.length} note="Not yet delivered or closed" accent="accent-blue" />
        <StatCard label="Collected revenue" value={money(collected)} note="Delivered cash + paid online bookings" accent="accent-green" />
      </div>
      <section className="panel"><div className="panel-heading"><div><h2>Recent bookings</h2><p>Latest orders across the marketplace</p></div><button className="text-button" onClick={() => setPage("bookings")}>View all →</button></div>
        <BookingTable bookings={bookings.data.slice(0, 8)} admin />
      </section>
      {waitingProviders.length > 0 && <div className="notice warn"><span>{waitingProviders.length} provider account{waitingProviders.length === 1 ? "" : "s"} need approval.</span><button className="text-button" onClick={() => setPage("providers")}>Review providers →</button></div>}
    </>}
  </>;
}

function AdminBookings() {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("ALL");
  const data = useCollection(async () => (await getDocs(query(collection(db, "bookings"), orderBy("createdAt", "desc"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)), []);
  const filtered = data.data.filter((booking) => (status === "ALL" || booking.status === status)
    && [booking.id, booking.customerName, booking.providerName, booking.serviceName, booking.status,
      booking.customerPhone].some((value) => matchesSearch(value, search)));
  return <><PageHeading eyebrow="MARKETPLACE" title="Bookings" description="Recent bookings across WashLink. Booking changes remain customer/provider controlled." action={<div className="heading-actions"><ExportActions rows={filtered} fileName="Bookings" columns={[
    { label: "Booking", value: (row) => row.id }, { label: "Customer", value: (row) => row.customerName || "" },
    { label: "Provider", value: (row) => row.providerName || "" }, { label: "Service", value: (row) => row.serviceName || "" },
    { label: "Created", value: (row) => shortDate(row.createdAt) }, { label: "Status", value: (row) => row.status || "" },
    { label: "Total (UGX)", value: (row) => Number(row.total || 0) },
  ]} /><button className="secondary" onClick={data.reload}>Refresh</button></div>} />
    <div className="table-controls"><label className="search-control"><span>Search bookings</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Customer, provider, service, phone…" /></label>
      <label className="search-control status-select"><span>Filter by status</span><select value={status} onChange={(event) => setStatus(event.target.value)}>
        <option value="ALL">All statuses</option>{Object.keys(STATUS_FLOW).flatMap((key) => [key, ...STATUS_FLOW[key]])
          .filter((value, index, all) => all.indexOf(value) === index).concat("CANCELLED")
          .map((value) => <option key={value} value={value}>{statusLabel(value)}</option>)}
      </select></label></div>
    <section className="panel">{data.loading ? <Loading /> : data.error ? <ErrorState message={data.error} /> : <BookingTable bookings={filtered} admin />}</section>
  </>;
}

function AdminProviders({ setBusy, setNotice }) {
  const [search, setSearch] = useState("");
  const [approvalFilter, setApprovalFilter] = useState("ALL");
  const providers = useCollection(async () => (await getDocs(query(collection(db, "providers"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const filteredProviders = providers.data.filter((provider) => [
    provider.businessName, provider.ownerName, provider.phone, provider.address,
    provider.isApproved !== true ? "pending" : "approved",
  ].some((value) => matchesSearch(value, search))
    && (approvalFilter === "ALL" || (approvalFilter === "PENDING"
      ? provider.isApproved !== true : provider.isApproved === true)));
  async function approve(provider, value) {
    setBusy(true);
    try {
      await updateDoc(doc(db, "providers", provider.id), { isApproved: value });
      setNotice(value ? "Provider approved." : "Provider approval removed.");
      await providers.reload();
    } catch (error) { setNotice(error.message || "Could not update provider approval."); }
    finally { setBusy(false); }
  }
  return <><PageHeading eyebrow="ADMINISTRATION" title="Providers" description="Review business profiles and manage marketplace approval." action={<div className="heading-actions"><ExportActions rows={filteredProviders} fileName="Providers" columns={[
    { label: "Business", value: (row) => row.businessName || "" }, { label: "Owner", value: (row) => row.ownerName || "" },
    { label: "Phone", value: (row) => row.phone || "" }, { label: "Address", value: (row) => row.address || "" },
    { label: "Approval", value: (row) => row.isApproved === true ? "Approved" : "Pending" },
  ]} /><button className="secondary" onClick={providers.reload}>Refresh</button></div>} />
    <div className="table-controls"><label className="search-control"><span>Search providers</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Business, owner, phone, address…" /></label>
      <label className="search-control status-select"><span>Approval status</span><select value={approvalFilter} onChange={(event) => setApprovalFilter(event.target.value)}>
        <option value="ALL">All providers</option><option value="PENDING">Pending</option><option value="APPROVED">Approved</option>
      </select></label></div>
    <section className="panel">{providers.loading ? <Loading /> : providers.error ? <ErrorState message={providers.error} /> :
      providers.data.length === 0 ? <Empty text="No provider profiles found." /> : filteredProviders.length === 0 ? <Empty text="No providers match your search." /> : <div className="records">
        {filteredProviders.map((provider) => <div className="record-row" key={provider.id}>
          <div className="record-main"><strong>{provider.businessName || "Laundry provider"}</strong><span>{provider.ownerName || "Owner unavailable"} · {provider.phone || "No phone"}</span><span>{provider.address || "Address not set"}</span></div>
          <span className={provider.isApproved === true ? "badge delivered" : "badge pending"}>{provider.isApproved === true ? "Approved" : "Pending approval"}</span>
          <button className={provider.isApproved === true ? "secondary small" : "primary small"} onClick={() => approve(provider, provider.isApproved !== true)}>{provider.isApproved === true ? "Unapprove" : "Approve"}</button>
        </div>)}
      </div>}</section>
  </>;
}

function AdminUsers({ setBusy, setNotice }) {
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [accountFilter, setAccountFilter] = useState("ALL");
  const users = useCollection(async () => (await getDocs(query(collection(db, "users"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const filteredUsers = users.data.filter((user) => [
    user.name, user.email, user.phone, user.role, user.isSuspended ? "suspended" : "active",
  ].some((value) => matchesSearch(value, search))
    && (roleFilter === "ALL" || user.role === roleFilter)
    && (accountFilter === "ALL" || (accountFilter === "SUSPENDED"
      ? user.isSuspended === true : user.isSuspended !== true)));
  async function suspend(user, suspended) {
    setBusy(true);
    try {
      await updateDoc(doc(db, "users", user.id), { isSuspended: suspended });
      setNotice(suspended ? "Account suspended." : "Account restored.");
      await users.reload();
    } catch (error) { setNotice(error.message || "Could not update account."); }
    finally { setBusy(false); }
  }
  return <><PageHeading eyebrow="ADMINISTRATION" title="User accounts" description="View platform accounts and suspend or restore non-admin access." action={<div className="heading-actions"><ExportActions rows={filteredUsers} fileName="Users" columns={[
    { label: "Name", value: (row) => row.name || "" }, { label: "Email", value: (row) => row.email || "" },
    { label: "Phone", value: (row) => row.phone || "" }, { label: "Role", value: (row) => row.role || "" },
    { label: "Status", value: (row) => row.isSuspended ? "Suspended" : "Active" },
  ]} /><button className="secondary" onClick={users.reload}>Refresh</button></div>} />
    <div className="table-controls"><label className="search-control"><span>Search users</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name, email, phone…" /></label>
      <label className="search-control status-select"><span>Role</span><select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}>
        <option value="ALL">All roles</option><option value="customer">Customer</option><option value="provider">Provider</option><option value="admin">Admin</option>
      </select></label>
      <label className="search-control status-select"><span>Account status</span><select value={accountFilter} onChange={(event) => setAccountFilter(event.target.value)}>
        <option value="ALL">All accounts</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option>
      </select></label></div>
    <section className="panel">{users.loading ? <Loading /> : users.error ? <ErrorState message={users.error} /> : filteredUsers.length === 0 ? <Empty text="No user accounts match your search." /> : <div className="records">
      {filteredUsers.map((user) => <div className="record-row" key={user.id}><div className="record-main"><strong>{user.name || "Unnamed account"}</strong><span>{user.email || "No email"} · {user.role || "Unknown role"}</span></div>
        <span className={user.isSuspended ? "badge rejected" : "badge delivered"}>{user.isSuspended ? "Suspended" : "Active"}</span>
        {user.role !== "admin" && <button className="secondary small" onClick={() => suspend(user, !user.isSuspended)}>{user.isSuspended ? "Restore" : "Suspend"}</button>}
      </div>)}
    </div>}</section>
  </>;
}

function ProviderOverview({ uid, setPage }) {
  const bookings = useProviderBookings(uid);
  const provider = useProviderProfile(uid);
  const data = bookings.data;
  const now = Date.now();
  const today = new Date(now);
  today.setHours(0, 0, 0, 0);
  const todayStart = today.getTime();
  const weekAgo = now - 7 * 24 * 60 * 60 * 1000;
  const todayCount = data.filter((booking) => timestampMillis(booking.createdAt) >= todayStart
    && timestampMillis(booking.createdAt) <= now).length;
  const pending = data.filter((booking) => booking.status === "BOOKED").length;
  const weeklyRevenue = data.reduce((sum, booking) => {
    if (booking.status !== "DELIVERED" || timestampMillis(booking.updatedAt) < weekAgo) return sum;
    return sum + verifiedRevenue(booking);
  }, 0);
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
    {bookings.loading || provider.loading ? <Loading /> : bookings.error || provider.error
      ? <ErrorState message={bookings.error || provider.error} /> : <>
      {!approved
        ? <div className="notice warn" role="status">Business review pending. Your business will appear to customers after administrator approval.</div>
        : <div className={isOpen ? "notice success" : "notice warn"} role="status">
          <span>{isOpen ? "Accepting new bookings" : "Bookings paused"}
            {bookingModes.length > 0 ? ` · ${bookingModes.join(" · ")}` : ""}
          </span>
          <button className="text-button" onClick={() => setPage("profile")}>Manage availability</button>
        </div>}
      <div className="stats-grid">
        <StatCard label="Orders today" value={todayCount} note="Bookings created today" />
        <StatCard label="Needs attention" value={pending} note="New requests waiting for a response" accent="accent-amber" />
        <StatCard label="Collected this week" value={money(weeklyRevenue)}
          note="Delivered cash + paid online orders" accent="accent-green" />
        <StatCard label="Customer rating" value={hasRating ? rating.toFixed(1) : "—"}
          note={hasRating ? `${reviewCount} customer ${reviewCount === 1 ? "review" : "reviews"}` : "No reviews yet"} />
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
    {provider.loading && <div className="notice" role="status">Checking business approval before enabling order updates…</div>}
    {provider.error && <div className="notice warn" role="alert">Could not verify business approval. Order status changes and rider assignments are disabled. {provider.error}</div>}
    {riders.error && <div className="notice warn" role="alert">Could not load active riders for assignment. {riders.error}</div>}
    {!provider.loading && !provider.error && !canManageOrders
      && <div className="notice warn" role="status">Your profile is awaiting administrator approval. Order status changes and rider assignments will be available after approval.</div>}
    <section className="panel">{bookings.loading ? <Loading /> : bookings.error ? <ErrorState message={bookings.error} /> :
      filtered.length === 0 ? <Empty text="No orders match this filter." /> : <div className="order-list">
        {filtered.map((booking) => <OrderCard key={booking.id} booking={booking} riders={ridersForAssignment}
          onStatus={updateStatus} onAssign={assignRider} canManageOrders={canManageOrders} />)}
      </div>}</section>
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
        <label>Price per kg (UGX)<input name="pricePerKg" required type="number" min="1" step="1" placeholder="5000" /></label>
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
      {provider.loading ? <Loading /> : provider.error ? <ErrorState message={provider.error} /> : services.length === 0 ? <Empty text="No services yet. Add the services and prices customers can book." /> :
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
      {provider.loading ? <Loading /> : provider.error ? <ErrorState message={provider.error} /> : <>
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
          <label>Business name<input name="businessName" required defaultValue={profile.businessName || ""} /></label>
          <label>Owner name<input name="ownerName" defaultValue={profile.ownerName || ""} /></label>
          <label>Business phone<input name="phone" type="tel" required defaultValue={profile.phone || ""} /></label>
          <label>Business address<input name="address" required defaultValue={profile.address || ""} /></label>
          <label className="wide">Description<textarea name="description" rows="3" defaultValue={profile.description || ""} /></label>
          <label>Operating hours<input name="operatingHours" placeholder="Mon–Sat, 8am–6pm" defaultValue={profile.operatingHours || ""} /></label>
          <label className="availability-check"><input name="isOpen" type="checkbox" defaultChecked={profile.isOpen !== false} /> Accept new bookings</label>
          <fieldset className="booking-mode-options wide">
            <legend>Booking options</legend>
            <p>Choose how customers can bring laundry to your business.</p>
            <label className="availability-check"><input type="checkbox" checked={supportsPickupDelivery}
              onChange={(event) => setSupportsPickupDelivery(event.target.checked)} /> Pickup and delivery</label>
            <label className="availability-check"><input type="checkbox" checked={supportsDropoff}
              onChange={(event) => setSupportsDropoff(event.target.checked)} /> Customer drop-off</label>
          </fieldset>
          <button className="primary">Save profile</button>
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
                  <span>{message.text}</span><small>{shortDateTime(message.createdAt)}</small>
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
  const [bankCode, setBankCode] = useState("");
  const [branchCode, setBranchCode] = useState("");
  const [optionsError, setOptionsError] = useState("");

  useEffect(() => {
    if (!functions) return;
    httpsCallable(functions, "getUgandaPayoutBanks")()
      .then((result) => setBanks(Array.isArray(result.data) ? result.data : []))
      .catch((error) => setOptionsError(error.message || "Could not load Uganda banks."));
  }, []);
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
      {saved.loading ? <Loading /> : saved.error ? <ErrorState message={saved.error} /> : <>
        <div className="notice">Only your signed-in account can read these payout details. Cash-on-delivery orders are collected directly by your business.</div>
        {current.accountNumber && <p className="payout-summary">Current destination: <strong>{current.beneficiaryName}</strong> · {current.destinationType} ending {current.accountNumber.slice(-4)}</p>}
        {optionsError && <div className="form-error">{optionsError}</div>}
        <form key={JSON.stringify([
          current.destinationType, current.beneficiaryName, current.accountNumber,
          current.accountBank, current.destinationBranchCode,
        ])} className="profile-form payout-form" onSubmit={save}>
          <label className="wide">Destination type<select required value={destinationType} onChange={(event) => setDestinationType(event.target.value)}>
            <option value="">Choose destination</option><option value="bank">Uganda bank account</option><option value="airtel">Airtel Money</option><option value="mtn">MTN Mobile Money</option>
          </select></label>
          {destinationType === "bank" && <>
            <label>Bank<select required value={bankCode} onChange={(event) => { setBankCode(event.target.value); setBranchCode(""); }}>
              <option value="">Choose bank</option>{banks.map((bank) => <option key={bank.code} value={bank.code}>{bank.name}</option>)}
            </select></label>
            {selectedBank?.hasBranches && <label>Branch<select required value={branchCode} disabled={branchesLoading}
              onChange={(event) => setBranchCode(event.target.value)}>
              <option value="">{branchesLoading ? "Loading branches…" : "Choose branch"}</option>
              {branches.map((branch) => <option key={branch.code} value={branch.code}>{branch.name}</option>)}
            </select></label>}
          </>}
          <label>Beneficiary full name<input name="beneficiaryName" required maxLength="100" defaultValue={current.beneficiaryName || ""} /></label>
          <label>{destinationType === "bank" ? "Bank account number" : "Uganda phone number (2567XXXXXXXX)"}
            <input name="accountNumber" required minLength="5" maxLength="32" defaultValue={current.accountNumber || ""} />
          </label>
          <button className="primary" disabled={destinationType === "bank" && (!bankCode || (selectedBank?.hasBranches && !branchCode))}>Save payout destination</button>
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
  return <article className="order-card">
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
  const bookings = useProviderBookings(uid);
  const cutoff = range === 0 ? 0 : Date.now() - range * 24 * 60 * 60 * 1000;
  const filtered = bookings.data.filter((b) => timestampMillis(b.createdAt) >= cutoff);
  const complete = filtered.filter((b) => b.status === "DELIVERED");
  const cancelled = filtered.filter((b) => ["CANCELLED", "REJECTED"].includes(b.status)).length;
  const revenue = filtered.reduce((sum, b) => sum + verifiedRevenue(b), 0);
  const cashRevenue = filtered.reduce((sum, booking) => sum
    + (booking.status === "DELIVERED" && booking.paymentProvider?.toLowerCase() === "cash"
      ? Number(booking.total || 0) : 0), 0);
  const onlineRevenue = filtered.reduce((sum, booking) => sum
    + (booking.status === "DELIVERED" && booking.paymentProvider?.toLowerCase() === "flutterwave"
      && booking.paymentStatus?.toUpperCase() === "PAID"
      ? Number(booking.subtotal || 0) : 0), 0);
  return <>
    <PageHeading eyebrow="BUSINESS PERFORMANCE" title="Reports" description="Booking and collected-revenue summary for selected periods." action={<div className="heading-actions"><ExportActions rows={filtered} fileName="Bookings report" columns={[
      { label: "Booking", value: (row) => row.id }, { label: "Customer", value: (row) => row.customerName || "" },
      { label: "Service", value: (row) => row.serviceName || "" }, { label: "Created", value: (row) => shortDate(row.createdAt) },
      { label: "Status", value: (row) => row.status || "" }, { label: "Payment status", value: (row) => row.paymentStatus || "" },
      { label: "Total (UGX)", value: (row) => Number(row.total || 0) },
    ]} /><button className="secondary" onClick={bookings.reload}>Refresh</button><div className="range-switch" role="group" aria-label="Report date range">{[[7, "7 days"], [30, "30 days"], [0, "All time"]].map(([days, text]) => <button key={days} className={range === days ? "selected" : ""}
      aria-pressed={range === days} onClick={() => setRange(days)}>{text}</button>)}</div></div>} />
    {bookings.loading ? <Loading /> : bookings.error ? <ErrorState message={bookings.error} /> : <>
      <div className="stats-grid"><StatCard label="Bookings" value={filtered.length} note={range ? `Created in the last ${range} days` : "All time"} /><StatCard label="Delivered" value={complete.length} note="Orders marked delivered" accent="accent-blue" /><StatCard label="Awaiting action" value={filtered.filter((b) => b.status === "BOOKED").length} note="New requests" accent="accent-amber" /><StatCard label="Cancelled / rejected" value={cancelled} note="Closed without delivery" /><StatCard label="Collected revenue" value={money(revenue)} note="Cash totals + paid online subtotal" accent="accent-green" /></div>
      <section className="panel"><div className="panel-heading"><div><h2>Revenue by payment method</h2><p>Only delivered cash and paid Flutterwave orders count.</p></div></div>
        <div className="report-rows">
          <div className="report-row"><span>Cash</span><strong>{money(cashRevenue)}</strong></div>
          <div className="report-row"><span>Verified online payment</span><strong>{money(onlineRevenue)}</strong></div>
        </div>
      </section>
    </>}
  </>;
}

function ProviderRiders({ uid, setNotice }) {
  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState("ALL");
  const riders = useProviderRiders(uid);
  const filteredRiders = riders.data.filter((rider) => [rider.name, rider.phone,
    rider.isActive ? "active" : "inactive"].some((value) => matchesSearch(value, search))
    && (activeFilter === "ALL" || (activeFilter === "ACTIVE" ? rider.isActive : !rider.isActive)));
  async function add(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") || "").trim();
    const phone = String(form.get("phone") || "").trim();
    if (name.length < 2 || name.length > 80 || phone.length < 7 || phone.length > 30) {
      setNotice("Enter a rider name (2–80 characters) and phone number (7–30 characters).");
      return;
    }
    try {
      await addDoc(collection(db, "providers", uid, "riders"), { name, phone, isActive: true, createdAt: Date.now() });
      event.currentTarget.reset();
      setNotice("Rider added.");
      await riders.reload();
    } catch (error) { setNotice(error.message || "Could not add rider."); }
  }
  async function toggle(rider) {
    try {
      await updateDoc(doc(db, "providers", uid, "riders", rider.id), { isActive: !rider.isActive });
      setNotice(rider.isActive ? "Rider deactivated." : "Rider activated.");
      await riders.reload();
    } catch (error) { setNotice(error.message || "Could not update rider."); }
  }
  return <>
    <PageHeading eyebrow="DELIVERY TEAM" title="Riders" description="Keep delivery contacts up to date and choose who is available for assignments." />
    <section className="panel rider-form-panel"><div className="panel-heading"><div><h2>Add a rider</h2><p>Rider information is only visible to your business and assigned customers.</p></div></div>
      <form className="rider-form" onSubmit={add}><label>Full name<input name="name" required minLength="2" maxLength="80" placeholder="Rider name" /></label><label>Phone number<input name="phone" required minLength="7" maxLength="30" type="tel" placeholder="+256…" /></label><button className="primary">Add rider</button></form>
    </section>
    <section className="panel"><div className="panel-heading"><div><h2>Your riders</h2><p>{riders.data.filter((r) => r.isActive).length} active</p></div>
      <div className="heading-actions"><ExportActions rows={filteredRiders} fileName="Riders" columns={[
        { label: "Name", value: (row) => row.name || "" }, { label: "Phone", value: (row) => row.phone || "" },
        { label: "Status", value: (row) => row.isActive ? "Active" : "Inactive" },
      ]} /><button className="secondary" onClick={riders.reload}>Refresh</button></div></div>
      <div className="table-controls"><label className="search-control"><span>Search riders</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name, phone…" /></label>
        <label className="search-control status-select"><span>Rider status</span><select value={activeFilter} onChange={(event) => setActiveFilter(event.target.value)}>
          <option value="ALL">All riders</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
        </select></label></div>
      {riders.loading ? <Loading /> : riders.error ? <ErrorState message={riders.error} /> : riders.data.length === 0 ? <Empty text="No riders yet. Add your first rider above." /> :
        filteredRiders.length === 0 ? <Empty text="No riders match your search." /> :
          <div className="records">{filteredRiders.map((rider) => <div className="record-row" key={rider.id}><div className="record-main"><strong>{rider.name}</strong><span>{rider.phone}</span></div><span className={rider.isActive ? "badge delivered" : "badge rejected"}>{rider.isActive ? "Active" : "Inactive"}</span><button className="secondary small" onClick={() => toggle(rider)}>{rider.isActive ? "Deactivate" : "Activate"}</button></div>)}</div>}
    </section>
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
  if (booking.paymentProvider?.toLowerCase() === "flutterwave"
      && booking.paymentStatus?.toUpperCase() === "PAID") return Number(booking.subtotal || 0);
  return 0;
}

function Loading() { return <div className="loading-row"><div className="spinner" /> Loading…</div>; }
function Empty({ text }) { return <div className="empty-state"><div className="empty-mark">—</div>{text}</div>; }
function ErrorState({ message }) { return <div className="form-error">{message}</div>; }

createRoot(document.getElementById("root")).render(<React.StrictMode><App /></React.StrictMode>);
