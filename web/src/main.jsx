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
  orderBy,
  query,
  updateDoc,
  where,
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
  ? new Date(Number(value)).toLocaleDateString()
  : "—";

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

  if (!firebaseConfigured) return <Setup />;
  if (authLoading) return <div className="center-page"><div className="spinner" /></div>;
  if (!session || !role) {
    return <Login notice={notice} onNotice={setNotice} />;
  }

  const links = role === "admin"
    ? [["overview", "Overview"], ["bookings", "Bookings"], ["providers", "Providers"], ["users", "Users"]]
    : [["overview", "Overview"], ["orders", "Orders"], ["reports", "Reports"], ["riders", "Riders"]];

  return (
    <div className="workspace">
      <aside className="sidebar">
        <Brand light />
        <div className="role-chip">{role === "admin" ? "Administrator" : "Laundry provider"}</div>
        <nav>
          {links.map(([key, label]) => (
            <button key={key} className={page === key ? "nav-link active" : "nav-link"}
              onClick={() => { setPage(key); setNotice(""); }}>
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
          <div className="mobile-brand">WashLink <span>Operations</span></div>
          <div className="topbar-spacer" />
          <span className="live-dot">Signed in</span>
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
        </div>
      </main>
      {busy && <div className="busy-overlay"><div className="spinner" /><span>Saving changes…</span></div>}
    </div>
  );
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
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0)), []);
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
  const data = useCollection(async () => (await getDocs(query(collection(db, "bookings"), orderBy("createdAt", "desc"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0)), []);
  return <><PageHeading eyebrow="MARKETPLACE" title="Bookings" description="Recent bookings across WashLink. Booking changes remain customer/provider controlled." action={<button className="secondary" onClick={data.reload}>Refresh</button>} />
    <section className="panel">{data.loading ? <Loading /> : data.error ? <ErrorState message={data.error} /> : <BookingTable bookings={data.data} admin />}</section>
  </>;
}

function AdminProviders({ setBusy, setNotice }) {
  const providers = useCollection(async () => (await getDocs(query(collection(db, "providers"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  async function approve(provider, value) {
    setBusy(true);
    try {
      await updateDoc(doc(db, "providers", provider.id), { isApproved: value });
      setNotice(value ? "Provider approved." : "Provider approval removed.");
      await providers.reload();
    } catch (error) { setNotice(error.message || "Could not update provider approval."); }
    finally { setBusy(false); }
  }
  return <><PageHeading eyebrow="ADMINISTRATION" title="Providers" description="Review business profiles and manage marketplace approval." action={<button className="secondary" onClick={providers.reload}>Refresh</button>} />
    <section className="panel">{providers.loading ? <Loading /> : providers.error ? <ErrorState message={providers.error} /> :
      providers.data.length === 0 ? <Empty text="No provider profiles found." /> : <div className="records">
        {providers.data.map((provider) => <div className="record-row" key={provider.id}>
          <div className="record-main"><strong>{provider.businessName || "Laundry provider"}</strong><span>{provider.ownerName || "Owner unavailable"} · {provider.phone || "No phone"}</span><span>{provider.address || "Address not set"}</span></div>
          <span className={provider.isApproved === false ? "badge pending" : "badge delivered"}>{provider.isApproved === false ? "Pending approval" : "Approved"}</span>
          <button className={provider.isApproved === false ? "primary small" : "secondary small"} onClick={() => approve(provider, provider.isApproved === false)}>{provider.isApproved === false ? "Approve" : "Unapprove"}</button>
        </div>)}
      </div>}</section>
  </>;
}

function AdminUsers({ setBusy, setNotice }) {
  const users = useCollection(async () => (await getDocs(query(collection(db, "users"), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), []);
  async function suspend(user, suspended) {
    setBusy(true);
    try {
      await updateDoc(doc(db, "users", user.id), { isSuspended: suspended });
      setNotice(suspended ? "Account suspended." : "Account restored.");
      await users.reload();
    } catch (error) { setNotice(error.message || "Could not update account."); }
    finally { setBusy(false); }
  }
  return <><PageHeading eyebrow="ADMINISTRATION" title="User accounts" description="View platform accounts and suspend or restore non-admin access." action={<button className="secondary" onClick={users.reload}>Refresh</button>} />
    <section className="panel">{users.loading ? <Loading /> : users.error ? <ErrorState message={users.error} /> : <div className="records">
      {users.data.map((user) => <div className="record-row" key={user.id}><div className="record-main"><strong>{user.name || "Unnamed account"}</strong><span>{user.email || "No email"} · {user.role || "Unknown role"}</span></div>
        <span className={user.isSuspended ? "badge rejected" : "badge delivered"}>{user.isSuspended ? "Suspended" : "Active"}</span>
        {user.role !== "admin" && <button className="secondary small" onClick={() => suspend(user, !user.isSuspended)}>{user.isSuspended ? "Restore" : "Suspend"}</button>}
      </div>)}
    </div>}</section>
  </>;
}

function ProviderOverview({ uid }) {
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), where("providerId", "==", uid), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0)), [uid]);
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
  const bookings = useCollection(async () => (await getDocs(query(collection(db, "bookings"), where("providerId", "==", uid), limit(500))))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })).sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0)), [uid]);
  const filtered = filter === "ALL" ? bookings.data : bookings.data.filter((booking) => booking.status === filter);
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
    <PageHeading eyebrow="ORDER MANAGEMENT" title="Orders" description="Review customer details and move each order through its valid next status." action={<button className="secondary" onClick={bookings.reload}>Refresh</button>} />
    <div className="filter-row">{["ALL", "BOOKED", "ACCEPTED", "PICKED_UP", "WASHING", "DRYING", "READY", "OUT_FOR_DELIVERY", "DELIVERED"].map((status) =>
      <button key={status} className={filter === status ? "filter active" : "filter"} onClick={() => setFilter(status)}>{status.replaceAll("_", " ")}</button>)}</div>
    <section className="panel">{bookings.loading ? <Loading /> : bookings.error ? <ErrorState message={bookings.error} /> :
      filtered.length === 0 ? <Empty text="No orders match this filter." /> : <div className="order-list">
        {filtered.map((booking) => <OrderCard key={booking.id} booking={booking} riders={ridersForAssignment}
          onStatus={updateStatus} onAssign={assignRider} />)}
      </div>}</section>
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
  const filtered = bookings.data.filter((b) => Number(b.createdAt || 0) >= cutoff);
  const complete = filtered.filter((b) => b.status === "DELIVERED");
  const revenue = filtered.reduce((sum, b) => sum + verifiedRevenue(b), 0);
  return <>
    <PageHeading eyebrow="BUSINESS PERFORMANCE" title="Reports" description="Booking and collected-revenue summary for selected periods." action={<div className="range-switch">{[[7, "7 days"], [30, "30 days"], [0, "All time"]].map(([days, text]) => <button key={days} className={range === days ? "selected" : ""} onClick={() => setRange(days)}>{text}</button>)}</div>} />
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
  const riders = useCollection(async () => (await getDocs(collection(db, "providers", uid, "riders")))
    .docs.map((entry) => ({ id: entry.id, ...entry.data() })), [uid]);
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
    <section className="panel"><div className="panel-heading"><div><h2>Your riders</h2><p>{riders.data.filter((r) => r.isActive).length} active</p></div><button className="secondary" onClick={riders.reload}>Refresh</button></div>
      {riders.loading ? <Loading /> : riders.error ? <ErrorState message={riders.error} /> : riders.data.length === 0 ? <Empty text="No riders yet. Add your first rider above." /> :
        <div className="records">{riders.data.map((rider) => <div className="record-row" key={rider.id}><div className="record-main"><strong>{rider.name}</strong><span>{rider.phone}</span></div><span className={rider.isActive ? "badge delivered" : "badge rejected"}>{rider.isActive ? "Active" : "Inactive"}</span><button className="secondary small" onClick={() => toggle(rider)}>{rider.isActive ? "Deactivate" : "Activate"}</button></div>)}</div>}
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
