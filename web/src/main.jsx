import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import {
  addDoc,
  collection,
  doc,
  getDoc,
  getDocs,
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
import { auth, db, firebaseConfigured, functions } from "./firebase";
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

const money = (value) => new Intl.NumberFormat("en-UG", {
  style: "currency",
  currency: "UGX",
  maximumFractionDigits: 0,
}).format(Number(value || 0));

const shortDate = (value) => value
  ? new Date(timestampMillis(value)).toLocaleDateString()
  : "—";

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
  const [session, setSession] = useState(null);
  const [role, setRole] = useState("");
  const [authLoading, setAuthLoading] = useState(firebaseConfigured);
  const [page, setPage] = useState("overview");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    if (!auth) return undefined;
    return onAuthStateChanged(auth, async (user) => {
      setSession(user);
      setRole("");
      setAuthLoading(false);
      if (!user) return;
      try {
        if (!user.emailVerified) {
          await signOut(auth);
          setNotice("Verify your email address before using the operations workspace.");
          return;
        }
        const profile = await getDoc(doc(db, "users", user.uid));
        const accountRole = profile.exists() ? profile.data().role : "";
        if (!profile.exists() || profile.data().isSuspended === true
            || !["admin", "provider"].includes(accountRole)) {
          await signOut(auth);
          setNotice("This workspace is only available to active provider and admin accounts.");
          return;
        }
        setRole(accountRole);
        setPage("overview");
      } catch (error) {
        setNotice(error.message || "Could not verify your account role.");
        await signOut(auth);
      }
    });
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
        <nav>
          {links.map(([key, label]) => (
            <button key={key} className={page === key ? "nav-link active" : "nav-link"}
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
          <span className="live-dot">Signed in</span>
          {role === "provider" && <ProviderNotifications uid={session.uid} setNotice={setNotice} />}
          <button className="avatar" title={session.email}>{(session.email || "W")[0].toUpperCase()}</button>
        </header>
        <div className="content">
          {notice && <div className="notice" role="status"><span>{notice}</span><button onClick={() => setNotice("")}>×</button></div>}
          {page === "overview" && role === "admin" && <AdminOverview setPage={setPage} />}
          {page === "bookings" && role === "admin" && <AdminBookings />}
          {page === "providers" && role === "admin" && <AdminProviders setBusy={setBusy} setNotice={setNotice} />}
          {page === "users" && role === "admin" && <AdminUsers setBusy={setBusy} setNotice={setNotice} />}
          {page === "overview" && role === "provider" && <ProviderOverview uid={session.uid} />}
          {page === "orders" && role === "provider" && <ProviderOrders uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "reports" && role === "provider" && <ProviderReports uid={session.uid} />}
          {page === "riders" && role === "provider" && <ProviderRiders uid={session.uid} setNotice={setNotice} />}
          {page === "services" && role === "provider" && <ProviderServices uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "profile" && role === "provider" && <ProviderProfile uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "payouts" && role === "provider" && <ProviderPayouts uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
          {page === "messages" && role === "provider" && <ProviderMessages uid={session.uid} setNotice={setNotice} setBusy={setBusy} />}
        </div>
      </main>
      {busy && <div className="busy-overlay"><div className="spinner" /><span>Saving changes…</span></div>}
    </div>
  );
}

function ProviderNotifications({ uid, setNotice }) {
  const [open, setOpen] = useState(false);
  const [state, setState] = useState({ data: [], loading: true, error: "" });

  useEffect(() => onSnapshot(query(
    collection(db, "users", uid, "notifications"), limit(50),
  ), (snapshot) => setState({
    data: snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() }))
      .sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)),
    loading: false,
    error: "",
  }), (error) => setState({
    data: [], loading: false, error: error.message || "Could not load notifications.",
  })), [uid]);

  const unread = state.data.filter((notification) => notification.read !== true).length;
  async function markRead(notification) {
    try {
      await updateDoc(doc(db, "users", uid, "notifications", notification.id), { read: true });
    } catch (error) {
      setNotice(error.message || "Could not update notification.");
    }
  }

  return <div className="notification-menu">
    <button className="notification-toggle" aria-label={`Notifications${unread ? `, ${unread} unread` : ""}`}
      aria-expanded={open} onClick={() => setOpen((value) => !value)}>
      <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
      {unread > 0 && <span className="notification-count">{unread > 99 ? "99+" : unread}</span>}
    </button>
    {open && <section className="notification-popover" aria-label="Notifications">
      <div className="notification-heading"><strong>Notifications</strong><span>{unread} unread</span></div>
      {state.loading ? <Loading /> : state.error ? <ErrorState message={state.error} /> :
        state.data.length === 0 ? <Empty text="No notifications yet." /> :
          <div className="notification-list">{state.data.map((notification) => <button
            key={notification.id} className={notification.read === true ? "notification-item read" : "notification-item"}
            onClick={() => notification.read === true ? undefined : markRead(notification)}>
            <strong>{notification.title || "Update"}</strong>
            <span>{notification.body || "You have a new update."}</span>
            <small>{shortDate(notification.createdAt)}</small>
          </button>)}</div>}
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
  const reload = async () => {
    setState((old) => ({ ...old, loading: true, error: "" }));
    try {
      const data = await load();
      setState({ data, loading: false, error: "" });
    } catch (error) {
      setState({ data: [], loading: false, error: error.message || "Could not load data." });
    }
  };
  useEffect(() => { reload(); }, dependencies);
  return { ...state, reload };
}

function AdminOverview({ setPage }) {
  const users = useCollection(async () => (await getDocs(query(collection(db, "users"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const providers = useCollection(async () => (await getDocs(query(collection(db, "providers"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), orderBy("createdAt", "desc"), limit(300))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)), []);
  const inProgress = bookings.data.filter((b) => !["DELIVERED", "CANCELLED", "REJECTED"].includes(b.status));
  const waitingProviders = providers.data.filter((p) => p.isApproved === false);
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
          .map((value) => <option key={value} value={value}>{value.replaceAll("_", " ")}</option>)}
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
    provider.isApproved === false ? "pending" : "approved",
  ].some((value) => matchesSearch(value, search))
    && (approvalFilter === "ALL" || (approvalFilter === "PENDING"
      ? provider.isApproved === false : provider.isApproved !== false)));
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
    { label: "Approval", value: (row) => row.isApproved === false ? "Pending" : "Approved" },
  ]} /><button className="secondary" onClick={providers.reload}>Refresh</button></div>} />
    <div className="table-controls"><label className="search-control"><span>Search providers</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Business, owner, phone, address…" /></label>
      <label className="search-control status-select"><span>Approval status</span><select value={approvalFilter} onChange={(event) => setApprovalFilter(event.target.value)}>
        <option value="ALL">All providers</option><option value="PENDING">Pending</option><option value="APPROVED">Approved</option>
      </select></label></div>
    <section className="panel">{providers.loading ? <Loading /> : providers.error ? <ErrorState message={providers.error} /> :
      providers.data.length === 0 ? <Empty text="No provider profiles found." /> : filteredProviders.length === 0 ? <Empty text="No providers match your search." /> : <div className="records">
        {filteredProviders.map((provider) => <div className="record-row" key={provider.id}>
          <div className="record-main"><strong>{provider.businessName || "Laundry provider"}</strong><span>{provider.ownerName || "Owner unavailable"} · {provider.phone || "No phone"}</span><span>{provider.address || "Address not set"}</span></div>
          <span className={provider.isApproved === false ? "badge pending" : "badge delivered"}>{provider.isApproved === false ? "Pending approval" : "Approved"}</span>
          <button className={provider.isApproved === false ? "primary small" : "secondary small"} onClick={() => approve(provider, provider.isApproved === false)}>{provider.isApproved === false ? "Approve" : "Unapprove"}</button>
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

function ProviderOverview({ uid }) {
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), where("providerId", "==", uid), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)), [uid]);
  const provider = useCollection(async () => {
    const snapshot = await getDoc(doc(db, "providers", uid));
    return snapshot.exists() ? [snapshot.data()] : [];
  }, [uid]);
  const data = bookings.data;
  const pending = data.filter((b) => b.status === "BOOKED").length;
  const delivered = data.filter((b) => b.status === "DELIVERED").length;
  const revenue = data.reduce((sum, b) => sum + verifiedRevenue(b), 0);
  const profile = provider.data[0];
  return <>
    <PageHeading eyebrow="PROVIDER WORKSPACE" title={profile?.businessName || "Business overview"} description={profile?.isApproved === false ? "Your profile is awaiting administrator approval." : "Manage your laundry orders and day-to-day operations."} />
    {bookings.loading || provider.loading ? <Loading /> : bookings.error ? <ErrorState message={bookings.error} /> : <>
      <div className="stats-grid">
        <StatCard label="Bookings" value={data.length} note="All available bookings" />
        <StatCard label="Needs attention" value={pending} note="New requests waiting for a response" accent="accent-amber" />
        <StatCard label="Delivered" value={delivered} note="Completed orders" accent="accent-blue" />
        <StatCard label="Collected revenue" value={money(revenue)} note="Delivered cash + paid online bookings" accent="accent-green" />
      </div>
      <section className="panel"><div className="panel-heading"><div><h2>Recent orders</h2><p>Latest customer bookings</p></div></div><BookingTable bookings={data.slice(0, 8)} /></section>
    </>}
  </>;
}

function ProviderOrders({ uid, setNotice, setBusy }) {
  const [filter, setFilter] = useState("ALL");
  const [search, setSearch] = useState("");
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), where("providerId", "==", uid), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt)), [uid]);
  const filtered = bookings.data.filter((booking) => (filter === "ALL" || booking.status === filter)
    && [booking.id, booking.customerName, booking.customerPhone, booking.serviceName, booking.status,
      booking.address, booking.assignedRiderName].some((value) => matchesSearch(value, search)));
  async function updateStatus(booking, status) {
    setBusy(true);
    setNotice("");
    try {
      await httpsCallable(functions, "updateBookingStatus")({ bookingId: booking.id, status });
      setNotice(`Order #${booking.id} updated to ${status.toLowerCase().replaceAll("_", " ")}.`);
      await bookings.reload();
    } catch (error) { setNotice(error.message || "Could not update order. The Functions deployment may be required."); }
    finally { setBusy(false); }
  }
  async function assignRider(booking, riderId) {
    if (!riderId) {
      setNotice("Choose a rider before assigning the order.");
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
  const ridersForAssignment = useCollection(async () => (await getDocs(query(collection(db, "providers", uid, "riders"), where("isActive", "==", true))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), [uid]).data;
  return <>
    <PageHeading eyebrow="ORDER MANAGEMENT" title="Orders" description="Review customer details and move each order through its valid next status." action={<div className="heading-actions"><ExportActions rows={filtered} fileName="Orders" columns={[
      { label: "Booking", value: (row) => row.id }, { label: "Customer", value: (row) => row.customerName || "" },
      { label: "Phone", value: (row) => row.customerPhone || "" }, { label: "Service", value: (row) => row.serviceName || "" },
      { label: "Created", value: (row) => shortDate(row.createdAt) }, { label: "Status", value: (row) => row.status || "" },
      { label: "Total (UGX)", value: (row) => Number(row.total || 0) },
    ]} /><button className="secondary" onClick={bookings.reload}>Refresh</button></div>} />
    <label className="search-control"><span>Search orders</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Customer, service, phone, address…" /></label>
    <div className="filter-row">{["ALL", "BOOKED", "ACCEPTED", "PICKED_UP", "WASHING", "DRYING", "READY", "OUT_FOR_DELIVERY", "DELIVERED"].map((status) =>
      <button key={status} className={filter === status ? "filter active" : "filter"} onClick={() => setFilter(status)}>{status.replaceAll("_", " ")}</button>)}</div>
    <section className="panel">{bookings.loading ? <Loading /> : bookings.error ? <ErrorState message={bookings.error} /> :
      filtered.length === 0 ? <Empty text="No orders match this filter." /> : <div className="order-list">
        {filtered.map((booking) => <OrderCard key={booking.id} booking={booking} riders={ridersForAssignment}
          onStatus={updateStatus} onAssign={assignRider} />)}
      </div>}</section>
  </>;
}

function ProviderServices({ uid, setNotice, setBusy }) {
  const provider = useCollection(async () => {
    const snapshot = await getDoc(doc(db, "providers", uid));
    return snapshot.exists() ? (snapshot.data().services || []) : [];
  }, [uid]);
  const [editing, setEditing] = useState(-1);
  const [serviceChoice, setServiceChoice] = useState(KNOWN_SERVICES[0]);
  const [customName, setCustomName] = useState("");
  const [search, setSearch] = useState("");
  const [availability, setAvailability] = useState("ALL");
  const services = provider.data;
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

function ProviderProfile({ uid, setNotice, setBusy }) {
  const provider = useCollection(async () => {
    const snapshot = await getDoc(doc(db, "providers", uid));
    return snapshot.exists() ? [snapshot.data()] : [];
  }, [uid]);
  const profile = provider.data[0] || {};
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
    const updates = {
      businessName,
      ownerName: String(values.get("ownerName") || "").trim(),
      phone,
      address,
      description: String(values.get("description") || "").trim(),
      operatingHours: String(values.get("operatingHours") || "").trim(),
      isOpen: values.get("isOpen") === "on",
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
  return <>
    <PageHeading eyebrow="BUSINESS SETTINGS" title="Business profile" description="Keep your public business details and booking availability current across the app and web." />
    <section className="panel profile-panel">
      {provider.loading ? <Loading /> : provider.error ? <ErrorState message={provider.error} /> : <>
        {profile.isApproved === false && <div className="notice warn">Your provider profile is awaiting administrator approval.</div>}
        <div className="profile-summary"><div><span className="detail-label">Profile completeness</span><strong>{[profile.businessName, profile.phone, profile.address, profile.description, profile.operatingHours, services.length > 0].filter(Boolean).length} / 6 details</strong></div><div><span className="detail-label">Service catalog</span><strong>{availableCount} available · {services.length} total</strong></div><div><span className="detail-label">Customer bookings</span><strong>{profile.isOpen === false ? "Paused" : "Accepting bookings"}</strong></div></div>
        <form className="profile-form" onSubmit={save}>
          <label>Business name<input name="businessName" required defaultValue={profile.businessName || ""} /></label>
          <label>Owner name<input name="ownerName" defaultValue={profile.ownerName || ""} /></label>
          <label>Business phone<input name="phone" type="tel" required defaultValue={profile.phone || ""} /></label>
          <label>Business address<input name="address" required defaultValue={profile.address || ""} /></label>
          <label className="wide">Description<textarea name="description" rows="3" defaultValue={profile.description || ""} /></label>
          <label>Operating hours<input name="operatingHours" placeholder="Mon–Sat, 8am–6pm" defaultValue={profile.operatingHours || ""} /></label>
          <label className="availability-check"><input name="isOpen" type="checkbox" defaultChecked={profile.isOpen !== false} /> Accept new bookings</label>
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
    collection(db, "chats"), where("participantIds", "array-contains", uid), limit(200),
  ), (snapshot) => setChats({
    data: snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() }))
      .sort((a, b) => timestampMillis(b.lastMessageAt) - timestampMillis(a.lastMessageAt)),
    loading: false,
    error: "",
  }), (error) => setChats({ data: [], loading: false, error: error.code === "permission-denied"
    ? "Firestore denied access to conversations. Confirm the web app targets the same Firebase project as Android and that the latest participant-only chat rules are deployed."
    : error.message || "Could not load conversations." })),
  [uid, refreshKey]);
  const selected = chats.data.find((chat) => chat.id === selectedId);
  useEffect(() => {
    if (!selectedId) {
      setMessages({ data: [], loading: false, error: "" });
      return undefined;
    }
    setMessages({ data: [], loading: true, error: "" });
    return onSnapshot(query(collection(db, "chats", selectedId, "messages"),
      orderBy("createdAt", "asc"), limit(500)),
    (snapshot) => setMessages({
      data: snapshot.docs.map((entry) => ({ id: entry.id, ...entry.data() })),
      loading: false,
      error: "",
    }), (error) => setMessages({ data: [], loading: false, error: error.code === "permission-denied"
      ? "Firestore denied access to this chat's messages. Confirm participantIds includes your provider UID and the latest participant-only chat rules are deployed."
      : error.message || "Could not load messages." }));
  }, [selectedId]);
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
          </button>)}</div>
          <div className="message-thread">
            {!selected ? <Empty text="Choose a conversation to view messages." /> : <>
              <div className="thread-heading"><strong>{selected.customerName || "Customer"}</strong><span>{selected.customerPhone || ""}</span></div>
              <div className="thread-messages">{messages.loading ? <Loading /> : messages.error ? <ErrorState message={messages.error} /> : messages.data.map((message) =>
                <div key={message.id} className={message.senderId === uid ? "message-bubble mine" : "message-bubble"}>{message.text}</div>)}</div>
              <form className="message-form" onSubmit={send}><input name="message" required maxLength="2000" placeholder="Write a reply…" /><button className="primary">Send</button></form>
            </>}
          </div>
        </div>}
    </section>
  </>;
}

function ProviderPayouts({ uid, setNotice, setBusy }) {
  const saved = useCollection(async () => {
    const snapshot = await getDoc(doc(db, "users", uid, "payoutProfile", "default"));
    return snapshot.exists() ? [snapshot.data()] : [];
  }, [uid]);
  const current = saved.data[0] || {};
  const [destinationType, setDestinationType] = useState("");
  const [banks, setBanks] = useState([]);
  const [branches, setBranches] = useState([]);
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
    const bank = banks.find((entry) => entry.code === bankCode);
    if (!bank?.hasBranches) {
      setBranches([]);
      setBranchCode("");
      return;
    }
    httpsCallable(functions, "getUgandaPayoutBranches")({ bankId: bank.id })
      .then((result) => {
        const values = Array.isArray(result.data) ? result.data : [];
        setBranches(values);
        setBranchCode((previous) => values.some((entry) => entry.code === previous)
          ? previous : "");
      })
      .catch((error) => setOptionsError(error.message || "Could not load bank branches."));
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
      await saved.reload();
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
        <form className="profile-form payout-form" onSubmit={save}>
          <label className="wide">Destination type<select required value={destinationType} onChange={(event) => setDestinationType(event.target.value)}>
            <option value="">Choose destination</option><option value="bank">Uganda bank account</option><option value="airtel">Airtel Money</option><option value="mtn">MTN Mobile Money</option>
          </select></label>
          {destinationType === "bank" && <>
            <label>Bank<select required value={bankCode} onChange={(event) => { setBankCode(event.target.value); setBranchCode(""); }}>
              <option value="">Choose bank</option>{banks.map((bank) => <option key={bank.code} value={bank.code}>{bank.name}</option>)}
            </select></label>
            {selectedBank?.hasBranches && <label>Branch<select required value={branchCode} onChange={(event) => setBranchCode(event.target.value)}>
              <option value="">Choose branch</option>{branches.map((branch) => <option key={branch.code} value={branch.code}>{branch.name}</option>)}
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

function OrderCard({ booking, riders, onStatus, onAssign }) {
  const [expanded, setExpanded] = useState(false);
  const [riderId, setRiderId] = useState(booking.assignedRiderId || "");
  const next = STATUS_FLOW[booking.status] || [];
  return <article className="order-card">
    <div className="order-top"><div><span className="order-number">Order #{booking.id}</span><span className="muted inline"> · {shortDate(booking.createdAt)}</span></div><span className={`badge ${String(booking.status || "").toLowerCase()}`}>{(booking.status || "UNKNOWN").replaceAll("_", " ")}</span></div>
    <div className="order-grid"><div><span className="detail-label">Customer</span><strong>{booking.customerName || "Customer"}</strong><span>{booking.customerPhone || "Phone unavailable"}</span></div><div><span className="detail-label">Service</span><strong>{booking.serviceName || "Laundry service"}</strong><span>{booking.itemCount || 0} items · {booking.serviceType || "—"}</span></div><div><span className="detail-label">Schedule</span><strong>{booking.scheduledDateTime || "Not scheduled"}</strong><span>{booking.address || "Address unavailable"}</span></div><div><span className="detail-label">Payment</span><strong>{money(booking.total)}</strong><span>{booking.paymentStatus || "PENDING"} · {booking.paymentProvider || "—"}</span></div></div>
    {booking.assignedRiderName && <div className="assigned-rider">Rider: {booking.assignedRiderName} · {booking.assignedRiderPhone || "No phone"}</div>}
    {expanded && <div className="order-extra"><span><b>Instructions:</b> {booking.specialInstructions || "None provided"}</span><span><b>Subtotal:</b> {money(booking.subtotal)} · <b>Tax:</b> {money(booking.tax)}</span><span><b>Payment method:</b> {booking.paymentMethod || "Not selected"}</span></div>}
    <div className="order-actions"><button className="text-button" onClick={() => setExpanded(!expanded)}>{expanded ? "Hide details" : "More details"}</button>
      {["ACCEPTED", "PICKED_UP", "WASHING", "DRYING", "READY", "OUT_FOR_DELIVERY"].includes(booking.status) && <>
        <select className="rider-select" aria-label={`Rider for booking ${booking.id}`} value={riderId}
          onChange={(event) => setRiderId(event.target.value)}>
          <option value="">Select rider</option>
          {riders.map((rider) => <option key={rider.id} value={rider.id}>{rider.name} · {rider.phone}</option>)}
        </select>
        <button className="secondary small" disabled={!riderId} onClick={() => onAssign(booking, riderId)}>
          {booking.assignedRiderId ? "Change rider" : "Assign rider"}
        </button>
      </>}
      {next.map((status) => <button key={status} className={status === "REJECTED" ? "danger small" : "primary small"} onClick={() => onStatus(booking, status)}>{status.replaceAll("_", " ")}</button>)}
    </div>
  </article>;
}

function ProviderReports({ uid }) {
  const [range, setRange] = useState(30);
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), where("providerId", "==", uid), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), [uid]);
  const cutoff = range === 0 ? 0 : Date.now() - range * 24 * 60 * 60 * 1000;
  const filtered = bookings.data.filter((b) => timestampMillis(b.createdAt) >= cutoff);
  const complete = filtered.filter((b) => b.status === "DELIVERED");
  const revenue = filtered.reduce((sum, b) => sum + verifiedRevenue(b), 0);
  return <>
    <PageHeading eyebrow="BUSINESS PERFORMANCE" title="Reports" description="Booking and collected-revenue summary for selected periods." action={<div className="heading-actions"><ExportActions rows={filtered} fileName="Bookings report" columns={[
      { label: "Booking", value: (row) => row.id }, { label: "Customer", value: (row) => row.customerName || "" },
      { label: "Service", value: (row) => row.serviceName || "" }, { label: "Created", value: (row) => shortDate(row.createdAt) },
      { label: "Status", value: (row) => row.status || "" }, { label: "Payment status", value: (row) => row.paymentStatus || "" },
      { label: "Total (UGX)", value: (row) => Number(row.total || 0) },
    ]} /><div className="range-switch">{[[7, "7 days"], [30, "30 days"], [0, "All time"]].map(([days, text]) => <button key={days} className={range === days ? "selected" : ""} onClick={() => setRange(days)}>{text}</button>)}</div></div>} />
    {bookings.loading ? <Loading /> : bookings.error ? <ErrorState message={bookings.error} /> : <>
      <div className="stats-grid"><StatCard label="Bookings" value={filtered.length} note={range ? `Created in the last ${range} days` : "All time"} /><StatCard label="Delivered" value={complete.length} note="Orders marked delivered" accent="accent-blue" /><StatCard label="Awaiting action" value={filtered.filter((b) => b.status === "BOOKED").length} note="New requests" accent="accent-amber" /><StatCard label="Collected revenue" value={money(revenue)} note="Cash totals + paid online subtotal" accent="accent-green" /></div>
      <section className="panel"><div className="panel-heading"><div><h2>Revenue by payment method</h2><p>Only delivered cash and paid Flutterwave orders count.</p></div></div>
        <div className="report-rows">{["cash", "flutterwave"].map((method) => {
          const amount = filtered.filter((b) => b.status === "DELIVERED" && b.paymentProvider === method && (method === "cash" || b.paymentStatus === "PAID"))
            .reduce((sum, b) => sum + Number(method === "cash" ? b.total : b.subtotal || 0), 0);
          return <div className="report-row" key={method}><span>{method === "cash" ? "Cash" : "Verified online payment"}</span><strong>{money(amount)}</strong></div>;
        })}</div>
      </section>
    </>}
  </>;
}

function ProviderRiders({ uid, setNotice }) {
  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState("ALL");
  const riders = useCollection(async () => (await getDocs(collection(db, "providers", uid, "riders")))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), [uid]);
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
    {bookings.map((b) => <tr key={b.id}><td className="mono">#{b.id.slice(0, 10)}</td><td>{b.customerName || "Customer"}{admin && <span className="sub-cell">{b.providerName || "Provider"}</span>}</td><td>{b.serviceName || "Laundry service"}</td><td>{shortDate(b.createdAt)}</td><td>{money(b.total)}</td><td><span className={`badge ${String(b.status || "").toLowerCase()}`}>{(b.status || "UNKNOWN").replaceAll("_", " ")}</span></td></tr>)}
  </tbody></table></div>;
}

function verifiedRevenue(booking) {
  if (booking.status !== "DELIVERED") return 0;
  if (booking.paymentProvider === "cash") return Number(booking.total || 0);
  if (booking.paymentProvider === "flutterwave" && booking.paymentStatus === "PAID") return Number(booking.subtotal || 0);
  return 0;
}

function Loading() { return <div className="loading-row"><div className="spinner" /> Loading…</div>; }
function Empty({ text }) { return <div className="empty-state"><div className="empty-mark">—</div>{text}</div>; }
function ErrorState({ message }) { return <div className="form-error">{message}</div>; }

createRoot(document.getElementById("root")).render(<React.StrictMode><App /></React.StrictMode>);
