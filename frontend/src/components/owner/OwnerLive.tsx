import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import {
  AlertTriangle,
  ArrowRight,
  CalendarDays,
  Check,
  ChevronRight,
  Copy,
  Download,
  Eye,
  EyeOff,
  Loader2,
  LogOut,
  Mail,
  MoreHorizontal,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  SlidersHorizontal,
  Trash2,
  UserRoundCheck,
  UserRoundX,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Workspace, PageHeader, PanelHeading, Empty, Brand } from "@/components/DeliveryApp";
import riderImage from "@/assets/delivery-rider.jpg";
import riderLoop from "@/assets/delivery-rider-loop.mp4.asset.json";
import { ApiError, clearSession, getRole, getToken, setSession } from "@/lib/hl/apiClient";
import { login } from "@/lib/hl/authApi";
import { me, updateMe } from "@/lib/hl/authApi";
import {
  cancelShipment,
  createShipment,
  deleteShipment,
  getShipment,
  listShipments,
  reassignShipment,
} from "@/lib/hl/shipmentsApi";
import {
  createAgent,
  deactivateAgent,
  inviteAgent,
  listAgents,
  reactivateAgent,
  updateAgent,
} from "@/lib/hl/agentsApi";
import {
  exportCsv,
  getAgentPerformance,
  getOverview,
  getRegister,
  getTrend,
} from "@/lib/hl/reportsApi";
import {
  STATUSES,
  STATUS_META,
  canCancel,
  canReassignAgent,
  canRestoreToAssigned,
} from "@/lib/hl/statusMachine";
import { formatDateTime, initials, localDateTimeToUtcNaive } from "@/lib/hl/format";
import { Avatar } from "@/components/ux";
import type {
  AgentInvite,
  AgentPerformanceRow,
  AgentSummary,
  DayPoint,
  OverviewReport,
  RegisterRow,
  Shipment,
  ShipmentStatus,
  ShipmentSummary,
  User,
} from "@/lib/hl/types";

/* ---------- shared helpers ---------- */
export function errText(e: unknown) {
  return e instanceof Error ? e.message : "Something went wrong. Please try again.";
}

export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]) {
  const navigate = useNavigate();
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true;
    setLoading(true);
    setError(null);
    fn()
      .then((d) => {
        if (live) setData(d);
      })
      .catch((e) => {
        if (!live) return;
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
          clearSession();
          navigate({ to: "/login" });
          return;
        }
        setError(errText(e));
      })
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { data, error, loading, reload: useCallback(() => setTick((t) => t + 1), []) };
}

function OwnerGate({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [ok, setOk] = useState(false);
  useEffect(() => {
    if (!getToken()) {
      navigate({ to: "/login" });
      return;
    }
    if (getRole() === "AGENT") {
      navigate({ to: "/agent/assignments" });
      return;
    }
    setOk(true);
  }, [navigate]);
  if (!ok)
    return (
      <div className="live-loading">
        <Loader2 className="spin" /> Checking your session…
      </div>
    );
  return <Workspace>{children}</Workspace>;
}

export function LiveStatus({ value }: { value: ShipmentStatus }) {
  const label = STATUS_META[value]?.label ?? value;
  return (
    <span className={`status status-${label.toLowerCase().replaceAll(" ", "-")}`}>{label}</span>
  );
}

export function Banner({ error, retry }: { error: string; retry?: (() => void) | undefined }) {
  return (
    <div className="live-banner" role="alert">
      <AlertTriangle size={18} />
      <span>{error}</span>
      {retry && (
        <Button variant="outline" size="sm" onClick={retry}>
          <RefreshCw /> Retry
        </Button>
      )}
    </div>
  );
}
export function Skeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="skeleton-list" aria-busy="true">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="skeleton-row" />
      ))}
    </div>
  );
}

export function Modal({
  label,
  eyebrow,
  onClose,
  children,
  wide,
}: {
  label: string;
  eyebrow: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  useEffect(() => {
    const k = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  }, [onClose]);
  // Without this, the page behind a fixed-position sheet stays scrollable —
  // a wheel/trackpad scroll over the dimmed backdrop moves the list behind
  // it, which reads as the sheet itself jumping between renders.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section
        className={`dialog ${wide ? "dialog-wide" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="dialog-top">
          <span className="eyebrow">{eyebrow}</span>
          <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close">
            <X />
          </Button>
        </div>
        {children}
      </section>
    </div>
  );
}

export function Confirm({
  title,
  body,
  action,
  danger,
  onConfirm,
  onClose,
}: {
  title: string;
  body: string;
  action: string;
  danger?: boolean;
  onConfirm: () => Promise<void>;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  return (
    <Modal label={title} eyebrow="PLEASE CONFIRM" onClose={onClose}>
      <h2>{title}</h2>
      <p className="dialog-sub">{body}</p>
      {err && <Banner error={err} />}
      <div className="action-pair">
        <Button variant="outline" onClick={onClose}>
          Keep as is
        </Button>
        <Button
          variant={danger ? "destructive" : "coral"}
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setErr(null);
            try {
              await onConfirm();
              onClose();
            } catch (e) {
              setErr(errText(e));
              setBusy(false);
            }
          }}
        >
          {busy && <Loader2 className="spin" />}
          {action}
        </Button>
      </div>
    </Modal>
  );
}

export function Toast({ text, onDone }: { text: string; onDone: () => void }) {
  useEffect(() => {
    const t = setTimeout(onDone, 3200);
    return () => clearTimeout(t);
  }, [onDone]);
  return (
    <div className="live-toast" role="status">
      <Check size={17} />
      {text}
    </div>
  );
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
function trackingUrl(token: string) {
  return `${window.location.origin}/track/${token}`;
}

/* ---------- sign in ---------- */
export function LiveLogin() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    if (getToken())
      navigate({ to: getRole() === "AGENT" ? "/agent/assignments" : "/owner/shipments" });
  }, [navigate]);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      const res = await login(email.trim(), password);
      setSession({ token: res.token || res.accessToken, role: res.role, user: res.user });
      navigate({ to: res.role === "AGENT" ? "/agent/assignments" : "/owner/shipments" });
    } catch (e2) {
      setErr(
        e2 instanceof ApiError && e2.status === 401
          ? "That email and password don’t match. Check both and try again."
          : errText(e2),
      );
      setBusy(false);
    }
  }
  return (
    <div className="auth-layout">
      <aside className="auth-aside">
        <Brand dark />
        <div className="auth-aside-content">
          <div className="auth-artwork" aria-hidden="true">
            <video autoPlay loop muted playsInline>
              <source src="/delivery-rider-loop.webm" type="video/webm" />
              <source src={riderLoop.url} type="video/mp4" />
            </video>
          </div>
          <div className="auth-pitch">
            <span className="eyebrow">HYPERLOCAL DELIVERY</span>
            <h2>Every delivery, handled beautifully.</h2>
            <p>From the first pickup to the final doorstep, keep the whole day in view.</p>
          </div>
        </div>
        <div className="auth-aside-foot">A clearer way to move locally.</div>
      </aside>
      <main className="auth-main">
        <div className="auth-mobile-brand">
          <Brand />
        </div>
        <div className="auth-artwork auth-artwork-mobile" aria-hidden="true">
          <video autoPlay loop muted playsInline>
            <source src="/delivery-rider-loop.webm" type="video/webm" />
            <source src={riderLoop.url} type="video/mp4" />
          </video>
        </div>
        <div className="auth-form-area">
          <p className="eyebrow">GOOD TO SEE YOU</p>
          <h1>Welcome back</h1>
          <p className="auth-intro">
            Owners and riders sign in here — we’ll open the right workspace for you.
          </p>
          <form className="form-stack" onSubmit={submit}>
            <label>
              Email
              <input
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@business.com"
              />
            </label>
            <label>
              Password
              <span className="password-field">
                <input
                  type={show ? "text" : "password"}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShow(!show)}
                  aria-label={show ? "Hide password" : "Show password"}
                >
                  {show ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </span>
            </label>
            <div className="auth-row">
              <Link to="/forgot-password" className="text-link">
                Forgot password?
              </Link>
            </div>
            {err && <Banner error={err} />}
            <Button variant="coral" type="submit" disabled={busy}>
              {busy ? <Loader2 className="spin" /> : null}Sign in <ArrowRight />
            </Button>
          </form>
          <p className="auth-switch">
            New business?{" "}
            <Link to="/register" className="text-link">
              Create an account
            </Link>
          </p>
        </div>
      </main>
    </div>
  );
}

/* ---------- shipments ---------- */
export function LiveShipments() {
  return (
    <OwnerGate>
      <ShipmentsBody />
    </OwnerGate>
  );
}
function ShipmentsBody() {
  const { data, error, loading, reload } = useLoad(() => listShipments(), []);
  const agents = useLoad(() => listAgents({ active: true }), []);
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<ShipmentStatus | "all">("all");
  const [openId, setOpenId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const rows = data ?? [];
  const visible = useMemo(
    () =>
      rows.filter(
        (s) =>
          (filter === "all" || s.status === filter) &&
          `${s.token} ${s.customerName} ${s.address} ${s.agentName ?? ""}`
            .toLowerCase()
            .includes(q.toLowerCase()),
      ),
    [rows, q, filter],
  );
  const count = (...st: ShipmentStatus[]) => rows.filter((s) => st.includes(s.status)).length;
  return (
    <main className="content">
      <PageHeader
        eyebrow="OPERATIONS"
        title="Shipments"
        description="Every delivery in your business, from assignment to doorstep."
        action={
          <div className="header-actions">
            <Button variant="outline" size="icon" onClick={reload} aria-label="Refresh">
              <RefreshCw className={loading ? "spin" : ""} />
            </Button>
            <Button variant="coral" size="hero" onClick={() => setCreating(true)}>
              <Plus /> New shipment
            </Button>
          </div>
        }
      />
      <div className="stats">
        <div className="stat">
          <small>ON THE WAY</small>
          <strong>{count("picked_up", "in_transit", "out_for_delivery")}</strong>
          <span>Picked up to out for delivery</span>
        </div>
        <div className="stat stat-teal">
          <small>DELIVERED</small>
          <strong>{count("delivered")}</strong>
          <span>Completed</span>
        </div>
        <div className="stat">
          <small>ASSIGNED</small>
          <strong>{count("assigned")}</strong>
          <span>Waiting for pickup</span>
        </div>
        <div className="stat stat-coral">
          <small>NEEDS YOU</small>
          <strong>{count("failed")}</strong>
          <span>Failed attempts to reassign</span>
        </div>
      </div>
      <div className="toolbar">
        <label className="searchbox">
          <Search size={18} />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search customer, address, rider or token…"
            aria-label="Search shipments"
          />
        </label>
        <div className="filter-scroll" role="group" aria-label="Filter by status">
          <Button
            variant={filter === "all" ? "selected" : "filter"}
            onClick={() => setFilter("all")}
          >
            All <span className="chip-count">{rows.length}</span>
          </Button>
          {STATUSES.map((s) => (
            <Button
              key={s}
              variant={filter === s ? "selected" : "filter"}
              onClick={() => setFilter(s)}
            >
              {STATUS_META[s].label} <span className="chip-count">{count(s)}</span>
            </Button>
          ))}
        </div>
      </div>
      {error && <Banner error={error} retry={reload} />}
      {!loading && data && agents.data && (agents.data.length === 0 || rows.length === 0) && (
        <Onboarding
          hasRider={agents.data.length > 0}
          hasShipment={rows.length > 0}
          onCreate={() => setCreating(true)}
        />
      )}
      <section className="list-section">
        <PanelHeading
          title="Shipment queue"
          aside={
            <span className="mono muted">
              {visible.length} of {rows.length}
            </span>
          }
        />
        {loading && !data ? (
          <Skeleton />
        ) : (
          <div className="shipment-list">
            {visible.length ? (
              visible.map((s) => <LiveRow key={s.id} s={s} onOpen={() => setOpenId(s.id)} />)
            ) : (
              <Empty
                title={rows.length ? "No matching shipments" : "No shipments yet"}
                body={
                  rows.length
                    ? "Try a different search or status."
                    : "Create your first shipment — it’s assigned to your least-busy active rider automatically."
                }
              />
            )}
          </div>
        )}
      </section>
      {creating && (
        <CreateShipment
          noRiders={!!agents.data && agents.data.length === 0}
          onClose={() => setCreating(false)}
          onCreated={(s) => {
            setCreating(false);
            setToast(`Created and assigned to ${s.agentName ?? "a rider"}`);
            reload();
            setOpenId(s.id);
          }}
        />
      )}
      {openId !== null && (
        <ShipmentPanel
          id={openId}
          agents={agents.data ?? []}
          onClose={() => setOpenId(null)}
          onChanged={(msg) => {
            setToast(msg);
            reload();
          }}
          onDeleted={() => {
            setOpenId(null);
            setToast("Shipment deleted");
            reload();
          }}
        />
      )}
      {toast && <Toast text={toast} onDone={() => setToast(null)} />}
    </main>
  );
}
function Onboarding({
  hasRider,
  hasShipment,
  onCreate,
}: {
  hasRider: boolean;
  hasShipment: boolean;
  onCreate: () => void;
}) {
  return (
    <section className="onboard">
      <p className="eyebrow">GET STARTED · {Number(hasRider) + Number(hasShipment)} OF 2 DONE</p>
      <h2>Two steps to your first delivery</h2>
      <ol className="onboard-steps">
        <li className={hasRider ? "done" : ""}>
          <span className="onboard-num">{hasRider ? <Check size={16} /> : 1}</span>
          <div>
            <strong>Add your first rider</strong>
            <p>They get a setup link to choose a password.</p>
          </div>
          {!hasRider && (
            <Button variant="coral" size="sm" asChild>
              <Link to="/owner/agents">
                Add rider <ArrowRight />
              </Link>
            </Button>
          )}
        </li>
        <li className={hasShipment ? "done" : ""}>
          <span className="onboard-num">{hasShipment ? <Check size={16} /> : 2}</span>
          <div>
            <strong>Create your first delivery</strong>
            <p>It’s assigned to your least-busy rider automatically.</p>
          </div>
          {!hasShipment && (
            <Button
              variant={hasRider ? "coral" : "outline"}
              size="sm"
              disabled={!hasRider}
              onClick={onCreate}
            >
              New shipment <ArrowRight />
            </Button>
          )}
        </li>
      </ol>
    </section>
  );
}
function LiveRow({ s, onOpen }: { s: ShipmentSummary; onOpen: () => void }) {
  return (
    <button className="shipment-row" onClick={onOpen}>
      <div className="row-top">
        <span className="mono muted">#{s.token.slice(0, 8)}</span>
        <LiveStatus value={s.status} />
        <span className="row-time">{s.scheduledAt ? formatDateTime(s.scheduledAt) : ""}</span>
      </div>
      <div className="row-main">
        <div>
          <h3>{s.customerName}</h3>
          <p>{s.address}</p>
          <p className="row-rider">
            <Avatar name={s.agentName} size="sm" />
            {s.agentName ?? "No rider"}
          </p>
        </div>
        <ChevronRight size={18} />
      </div>
    </button>
  );
}

function CreateShipment({
  onClose,
  onCreated,
  noRiders,
}: {
  onClose: () => void;
  onCreated: (s: Shipment) => void;
  noRiders: boolean;
}) {
  const today = new Date();
  const z = (n: number) => String(n).padStart(2, "0");
  const [f, setF] = useState({
    customerName: "",
    customerPhone: "",
    address: "",
    date: `${today.getFullYear()}-${z(today.getMonth() + 1)}-${z(today.getDate())}`,
    time: `${z(Math.min(today.getHours() + 1, 23))}:00`,
  });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });
  async function submit(e: FormEvent) {
    e.preventDefault();
    const scheduledAt = localDateTimeToUtcNaive(f.date, f.time);
    if (!scheduledAt) {
      setErr("Choose a valid delivery date and time.");
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      onCreated(
        await createShipment({
          customerName: f.customerName.trim(),
          customerPhone: f.customerPhone.trim(),
          address: f.address.trim(),
          scheduledAt,
        }),
      );
    } catch (e2) {
      setErr(errText(e2));
      setBusy(false);
    }
  }
  return (
    <Modal label="New shipment" eyebrow="NEW SHIPMENT" onClose={onClose}>
      <h2>New shipment</h2>
      <p className="dialog-sub">We’ll assign it to your least-busy active rider.</p>
      {noRiders && (
        <Banner error="You have no active riders yet. Add a rider first so this shipment can be assigned." />
      )}
      <form className="form-stack" onSubmit={submit}>
        <label>
          Customer name
          <input
            required
            value={f.customerName}
            onChange={set("customerName")}
            placeholder="e.g. Priya Sharma"
          />
        </label>
        <label>
          Customer phone
          <input
            required
            type="tel"
            inputMode="tel"
            value={f.customerPhone}
            onChange={set("customerPhone")}
            placeholder="10-digit mobile"
          />
        </label>
        <label>
          Delivery address
          <textarea
            required
            rows={2}
            value={f.address}
            onChange={set("address")}
            placeholder="House, street, area, landmark"
          />
        </label>
        <div className="field-pair">
          <label>
            Date
            <input required type="date" value={f.date} onChange={set("date")} />
          </label>
          <label>
            Time
            <input required type="time" value={f.time} onChange={set("time")} />
          </label>
        </div>
        {err && <Banner error={err} />}
        <Button variant="coral" type="submit" disabled={busy}>
          {busy && <Loader2 className="spin" />}Create and assign <ArrowRight />
        </Button>
      </form>
    </Modal>
  );
}

function ShipmentPanel({
  id,
  agents,
  onClose,
  onChanged,
  onDeleted,
}: {
  id: number;
  agents: AgentSummary[];
  onClose: () => void;
  onChanged: (m: string) => void;
  onDeleted: () => void;
}) {
  const { data: s, error, loading, reload } = useLoad(() => getShipment(id), [id]);
  const [agentId, setAgentId] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [actErr, setActErr] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<"cancel" | "delete" | null>(null);
  const [copied, setCopied] = useState(false);
  async function reassign() {
    if (!s) return;
    setBusy(true);
    setActErr(null);
    try {
      const r = await reassignShipment(s.id, { agentId: agentId || null, note: note || null });
      onChanged(
        canRestoreToAssigned(s.status)
          ? `Back to Assigned with ${r.agentName ?? "a rider"}`
          : `Reassigned to ${r.agentName ?? "a rider"}`,
      );
      setNote("");
      setAgentId("");
      reload();
    } catch (e) {
      setActErr(errText(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      label="Shipment details"
      eyebrow={s ? `SHIPMENT · ${s.token.slice(0, 8).toUpperCase()}` : "SHIPMENT"}
      onClose={onClose}
      wide
    >
      {loading && !s ? (
        <Skeleton rows={3} />
      ) : error ? (
        <Banner error={error} retry={reload} />
      ) : (
        s && (
          <>
            <div className="panel-title">
              <div>
                <h2>{s.customerName}</h2>
                <p className="dialog-sub">{s.address}</p>
              </div>
              <LiveStatus value={s.status} />
            </div>
            <div className="detail-grid">
              <div>
                <small>RIDER</small>
                <strong className="row-rider">
                  {s.agentName && <Avatar name={s.agentName} size="sm" />}
                  {s.agentName ?? "Not assigned"}
                </strong>
              </div>
              <div>
                <small>CUSTOMER PHONE</small>
                <strong>
                  <a href={`tel:${s.customerPhone}`}>{s.customerPhone}</a>
                </strong>
              </div>
              <div>
                <small>SCHEDULED</small>
                <strong>{formatDateTime(s.scheduledAt)}</strong>
              </div>
              <div>
                <small>{s.deliveredAt ? "DELIVERED" : "CREATED"}</small>
                <strong>{formatDateTime(s.deliveredAt ?? s.createdAt)}</strong>
              </div>
            </div>
            <div className="track-share">
              <div>
                <small>CUSTOMER TRACKING LINK</small>
                <code>{trackingUrl(s.token)}</code>
              </div>
              <div className="action-pair">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={async () => {
                    setCopied(await copy(trackingUrl(s.token)));
                    setTimeout(() => setCopied(false), 2000);
                  }}
                >
                  {copied ? <Check /> : <Copy />}
                  {copied ? "Copied" : "Copy"}
                </Button>
                <Button variant="outline" size="sm" asChild>
                  <Link to="/track/$token" params={{ token: s.token }}>
                    Open
                  </Link>
                </Button>
              </div>
            </div>
            {canReassignAgent(s.status) && (
              <div className="owner-action">
                <h3>
                  {canRestoreToAssigned(s.status) ? "Send out for another attempt" : "Change rider"}
                </h3>
                <p className="form-help">
                  {canRestoreToAssigned(s.status)
                    ? "This returns the shipment to Assigned with the rider you pick."
                    : "Leave on automatic to pick your least-busy active rider."}
                </p>
                <div className="field-pair">
                  <label>
                    Rider
                    <Select
                      value={agentId || "auto"}
                      onValueChange={(v) => setAgentId(v === "auto" ? "" : v)}
                    >
                      <SelectTrigger aria-label="Rider">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="auto">Automatic (least busy)</SelectItem>
                        {agents
                          .filter((a) => a.active)
                          .map((a) => (
                            <SelectItem key={a.id} value={String(a.id)}>
                              {a.name} · {a.openCount} open
                            </SelectItem>
                          ))}
                      </SelectContent>
                    </Select>
                  </label>
                  <label>
                    Note (optional)
                    <input
                      value={note}
                      onChange={(e) => setNote(e.target.value)}
                      placeholder="Reason for the change"
                    />
                  </label>
                </div>
                {actErr && <Banner error={actErr} />}
                <div className="action-pair">
                  <Button variant="coral" disabled={busy} onClick={reassign}>
                    {busy && <Loader2 className="spin" />}
                    {canRestoreToAssigned(s.status) ? "Reassign for another attempt" : "Reassign"}
                  </Button>
                  {canCancel(s.status) && (
                    <Button variant="outline" onClick={() => setConfirm("cancel")}>
                      <X /> Cancel shipment
                    </Button>
                  )}
                </div>
              </div>
            )}
            {s.status === "cancelled" && (
              <div className="owner-action">
                <h3>Remove this shipment</h3>
                <p className="form-help">Cancelled shipments can be permanently deleted.</p>
                <Button variant="destructive" onClick={() => setConfirm("delete")}>
                  <Trash2 /> Delete permanently
                </Button>
              </div>
            )}
            {s.attempts.length > 0 && (
              <>
                <PanelHeading title="Delivery attempts" />
                <ul className="attempt-list">
                  {s.attempts.map((a) => (
                    <li key={a.id}>
                      <strong>
                        Attempt {a.attemptNumber ?? a.no} · {a.failureReason ?? a.reason}
                      </strong>
                      <span>
                        {a.agentName ?? ""} · {formatDateTime(a.attemptedAt ?? a.stamp)}
                      </span>
                      {a.note && <p>{a.note}</p>}
                    </li>
                  ))}
                </ul>
              </>
            )}
            <PanelHeading title="Timeline" />
            <ol className="live-timeline">
              {[...s.events].reverse().map((ev, i) => (
                <li key={i}>
                  <span className="tl-dot" />
                  <div>
                    <strong>{ev.label ?? STATUS_META[ev.toStatus ?? ev.status]?.label}</strong>
                    <small>
                      {formatDateTime(ev.createdAt ?? ev.stamp)}
                      {ev.changedBy ? ` · ${ev.changedBy}` : ""}
                    </small>
                    {ev.notes && <p>{ev.notes}</p>}
                  </div>
                </li>
              ))}
            </ol>
          </>
        )
      )}
      {confirm === "cancel" && s && (
        <Confirm
          title="Cancel this shipment?"
          body="The rider will no longer see it and the customer’s tracking page will show it as cancelled. This can’t be undone."
          action="Cancel shipment"
          danger
          onClose={() => setConfirm(null)}
          onConfirm={async () => {
            await cancelShipment(s.id, note || null);
            onChanged("Shipment cancelled");
            reload();
          }}
        />
      )}
      {confirm === "delete" && s && (
        <Confirm
          title="Delete permanently?"
          body="This removes the shipment and its history for good."
          action="Delete"
          danger
          onClose={() => setConfirm(null)}
          onConfirm={async () => {
            await deleteShipment(s.id);
            onDeleted();
          }}
        />
      )}
    </Modal>
  );
}

/* ---------- riders ---------- */
export function LiveRiders() {
  return (
    <OwnerGate>
      <RidersBody />
    </OwnerGate>
  );
}
function RidersBody() {
  const { data, error, loading, reload } = useLoad(() => listAgents(), []);
  const [q, setQ] = useState("");
  const [show, setShow] = useState<"active" | "inactive" | "all">("active");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<AgentSummary | null>(null);
  const [invite, setInvite] = useState<{ name: string; invite: AgentInvite } | null>(null);
  const [menu, setMenu] = useState<number | null>(null);
  const [confirm, setConfirm] = useState<AgentSummary | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [rowErr, setRowErr] = useState<string | null>(null);
  const rows = (data ?? []).filter(
    (a) =>
      (show === "all" || (show === "active") === a.active) &&
      `${a.name} ${a.email} ${a.phone ?? ""}`.toLowerCase().includes(q.toLowerCase()),
  );
  async function sendInvite(a: { id: number; name: string }) {
    setRowErr(null);
    try {
      setInvite({ name: a.name, invite: await inviteAgent(a.id) });
    } catch (e) {
      setRowErr(errText(e));
    }
  }
  return (
    <main className="content">
      <PageHeader
        eyebrow="TEAM"
        title="Riders"
        description="Add riders, send setup links and manage who receives new shipments."
        action={
          <Button variant="coral" size="hero" onClick={() => setAdding(true)}>
            <Plus /> Add rider
          </Button>
        }
      />
      <div className="toolbar">
        <label className="searchbox">
          <Search size={18} />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search name, email or phone…"
            aria-label="Search riders"
          />
        </label>
        <div className="filter-scroll" role="group" aria-label="Rider status">
          {(["active", "inactive", "all"] as const).map((v) => (
            <Button key={v} variant={show === v ? "selected" : "filter"} onClick={() => setShow(v)}>
              {v.charAt(0).toUpperCase() + v.slice(1)}
            </Button>
          ))}
        </div>
      </div>
      {(error || rowErr) && (
        <Banner error={(error || rowErr)!} retry={error ? reload : undefined} />
      )}
      {loading && !data ? (
        <Skeleton />
      ) : rows.length ? (
        <div className="rider-grid">
          {rows.map((a) => (
            <article key={a.id} className={`rider-card ${a.active ? "" : "rider-off"}`}>
              <div className="rider-card-top menu-anchor">
                <Avatar name={a.name} />
                <div>
                  <h3>{a.name}</h3>
                  <p>{a.email}</p>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`More actions for ${a.name}`}
                  onClick={() => setMenu(menu === a.id ? null : a.id)}
                >
                  <MoreHorizontal />
                </Button>
                {menu === a.id && (
                  <div className="pop-menu" role="menu" onMouseLeave={() => setMenu(null)}>
                    <button
                      role="menuitem"
                      onClick={() => {
                        setMenu(null);
                        setEditing(a);
                      }}
                    >
                      <Pencil size={15} /> Edit details
                    </button>
                    {a.active ? (
                      <button
                        role="menuitem"
                        className="danger"
                        onClick={() => {
                          setMenu(null);
                          setConfirm(a);
                        }}
                      >
                        <UserRoundX size={15} /> Deactivate
                      </button>
                    ) : (
                      <button
                        role="menuitem"
                        onClick={async () => {
                          setMenu(null);
                          try {
                            await reactivateAgent(a.id);
                            setToast(`${a.name} is active again`);
                            reload();
                          } catch (e) {
                            setRowErr(errText(e));
                          }
                        }}
                      >
                        <UserRoundCheck size={15} /> Reactivate
                      </button>
                    )}
                  </div>
                )}
              </div>
              <div className="rider-meta">
                <span className={`pill ${a.activated ? "pill-teal" : "pill-coral"}`}>
                  {a.activated ? "Set up" : "Setup pending"}
                </span>
                <span className="pill">{a.active ? "Active" : "Inactive"}</span>
                <span className="mono muted">{a.openCount} open</span>
              </div>
              {a.phone && (
                <p className="rider-phone">
                  <a href={`tel:${a.phone}`}>{a.phone}</a>
                </p>
              )}
              {!a.activated && a.active && (
                <Button variant="outline" size="sm" onClick={() => sendInvite(a)}>
                  <Mail /> Send setup link
                </Button>
              )}
            </article>
          ))}
        </div>
      ) : (
        <Empty
          title={data?.length ? "No riders match" : "No riders yet"}
          body={
            data?.length
              ? "Try another search or status."
              : "Add your first rider to start assigning shipments."
          }
        />
      )}
      {adding && (
        <RiderForm
          onClose={() => setAdding(false)}
          onSaved={async (a) => {
            setAdding(false);
            reload();
            await sendInvite(a);
          }}
        />
      )}
      {editing && (
        <RiderForm
          rider={editing}
          onClose={() => setEditing(null)}
          onSaved={(a) => {
            setEditing(null);
            setToast(`${a.name} updated`);
            reload();
          }}
        />
      )}
      {invite && (
        <InviteSheet name={invite.name} invite={invite.invite} onClose={() => setInvite(null)} />
      )}
      {confirm && (
        <Confirm
          title={`Deactivate ${confirm.name}?`}
          body={
            confirm.openCount
              ? `${confirm.name} has ${confirm.openCount} open shipment(s). Reassign them first — a rider with open shipments can’t be deactivated.`
              : "They won’t receive new shipments and can’t sign in until reactivated."
          }
          action="Deactivate"
          danger
          onClose={() => setConfirm(null)}
          onConfirm={async () => {
            await deactivateAgent(confirm.id);
            setToast(`${confirm.name} deactivated`);
            reload();
          }}
        />
      )}
      {toast && <Toast text={toast} onDone={() => setToast(null)} />}
    </main>
  );
}
function RiderForm({
  rider,
  onClose,
  onSaved,
}: {
  rider?: AgentSummary;
  onClose: () => void;
  onSaved: (a: { id: number; name: string }) => void;
}) {
  const [name, setName] = useState(rider?.name ?? "");
  const [email, setEmail] = useState(rider?.email ?? "");
  const [phone, setPhone] = useState(rider?.phone ?? "");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      const a = rider
        ? await updateAgent(rider.id, { name: name.trim(), phone: phone.trim() })
        : await createAgent({ name: name.trim(), email: email.trim(), phone: phone.trim() });
      onSaved(a);
    } catch (e2) {
      setErr(errText(e2));
      setBusy(false);
    }
  }
  return (
    <Modal
      label={rider ? "Edit rider" : "Add rider"}
      eyebrow={rider ? "EDIT RIDER" : "NEW RIDER"}
      onClose={onClose}
    >
      <h2>{rider ? `Edit ${rider.name}` : "Add a rider"}</h2>
      <p className="dialog-sub">
        {rider
          ? "Email can’t be changed."
          : "Next, you’ll get a setup link to share so they can choose a password."}
      </p>
      <form className="form-stack" onSubmit={submit}>
        <label>
          Full name
          <input required value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label>
          Email
          <input
            required
            type="email"
            disabled={!!rider}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label>
          Phone
          <input required type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
        </label>
        {err && <Banner error={err} />}
        <Button variant="coral" type="submit" disabled={busy}>
          {busy && <Loader2 className="spin" />}
          {rider ? "Save changes" : "Add rider"} <ArrowRight />
        </Button>
      </form>
    </Modal>
  );
}
function InviteSheet({
  name,
  invite,
  onClose,
}: {
  name: string;
  invite: AgentInvite;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);
  return (
    <Modal label="Setup link" eyebrow="SETUP LINK" onClose={onClose}>
      <h2>Share with {name}</h2>
      <p className="dialog-sub">
        {invite.emailed
          ? "We also emailed this link to them."
          : "Send this link by WhatsApp or SMS."}{" "}
        It expires {formatDateTime(invite.expiresAt)}.
      </p>
      <div className="invite-link">
        <code>{invite.inviteUrl}</code>
        <Button variant="coral" onClick={async () => setCopied(await copy(invite.inviteUrl))}>
          {copied ? <Check /> : <Copy />}
          {copied ? "Copied" : "Copy link"}
        </Button>
      </div>
      <Button variant="outline" asChild>
        <a
          href={`https://wa.me/?text=${encodeURIComponent(`Set up your Hyperlocal rider account: ${invite.inviteUrl}`)}`}
          target="_blank"
          rel="noreferrer"
        >
          Share on WhatsApp
        </a>
      </Button>
    </Modal>
  );
}

/* ---------- register ---------- */
export function LiveRegister() {
  return (
    <OwnerGate>
      <RegisterBody />
    </OwnerGate>
  );
}
function RegisterBody() {
  const [status, setStatus] = useState("");
  const [agentId, setAgentId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [q, setQ] = useState("");
  const agents = useLoad(() => listAgents(), []);
  const params = {
    status: status || undefined,
    agentId: agentId || undefined,
    from: from || undefined,
    to: to || undefined,
  };
  const { data, error, loading, reload } = useLoad(
    () => getRegister(params as Parameters<typeof getRegister>[0]),
    [status, agentId, from, to],
  );
  const [exporting, setExporting] = useState(false);
  const [expErr, setExpErr] = useState<string | null>(null);
  const ymd = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  const now = new Date();
  const today = ymd(now);
  const quick = [
    { id: "today", label: "Today", f: today },
    { id: "7", label: "Last 7 days", f: ymd(new Date(now.getTime() - 6 * 864e5)) },
    { id: "month", label: "This month", f: ymd(new Date(now.getFullYear(), now.getMonth(), 1)) },
  ];
  const rows = (data ?? []).filter((r: RegisterRow) =>
    `${r.token} ${r.customerName} ${r.address} ${r.agentName ?? ""}`
      .toLowerCase()
      .includes(q.toLowerCase()),
  );
  return (
    <main className="content">
      <PageHeader
        eyebrow="RECORDS"
        title="Register"
        description="A complete record of shipments, filterable and exportable."
        action={
          <Button
            variant="outline"
            disabled={exporting}
            onClick={async () => {
              setExporting(true);
              setExpErr(null);
              const ok = await exportCsv("register", params);
              if (!ok) setExpErr("The export couldn’t be downloaded. Try again.");
              setExporting(false);
            }}
          >
            {exporting ? <Loader2 className="spin" /> : <Download />} Export CSV
          </Button>
        }
      />
      <div className="filter-scroll quick-dates" role="group" aria-label="Quick date range">
        <Button
          variant={!from && !to ? "selected" : "filter"}
          onClick={() => {
            setFrom("");
            setTo("");
          }}
        >
          All time
        </Button>
        {quick.map((c) => (
          <Button
            key={c.id}
            variant={from === c.f && to === today ? "selected" : "filter"}
            onClick={() => {
              setFrom(c.f);
              setTo(today);
            }}
          >
            {c.label}
          </Button>
        ))}
      </div>
      <div className="filter-panel">
        <label className="searchbox">
          <Search size={18} />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search…"
            aria-label="Search register"
          />
        </label>
        <Select value={status || "all"} onValueChange={(v) => setStatus(v === "all" ? "" : v)}>
          <SelectTrigger className="w-auto h-[46px] gap-2 rounded-lg" aria-label="Status">
            <SlidersHorizontal size={17} className="opacity-70" />
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            <SelectItem value="delivered,failed">Closed (delivered or failed)</SelectItem>
            {STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {STATUS_META[s].label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={agentId || "all"} onValueChange={(v) => setAgentId(v === "all" ? "" : v)}>
          <SelectTrigger className="w-auto h-[46px] gap-2 rounded-lg" aria-label="Rider">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All riders</SelectItem>
            {(agents.data ?? []).map((a) => (
              <SelectItem key={a.id} value={String(a.id)}>
                {a.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <div className="date-range" role="group" aria-label="Date range">
          <label className="date-field">
            From
            <input
              type="date"
              value={from}
              max={to || undefined}
              onChange={(e) => setFrom(e.target.value)}
            />
          </label>
          <label className="date-field">
            To
            <input
              type="date"
              value={to}
              min={from || undefined}
              onChange={(e) => setTo(e.target.value)}
            />
          </label>
        </div>
        {(status || agentId || from || to) && (
          <Button
            variant="ghost"
            onClick={() => {
              setStatus("");
              setAgentId("");
              setFrom("");
              setTo("");
            }}
          >
            Clear
          </Button>
        )}
      </div>
      {(error || expErr) && (
        <Banner error={(error || expErr)!} retry={error ? reload : undefined} />
      )}
      {data &&
        (() => {
          const del = rows.filter((r) => r.status === "delivered").length,
            fail = rows.filter((r) => r.status === "failed" || r.status === "returned").length,
            closed = del + fail;
          return (
            <div className="stats register-totals">
              <div className="stat">
                <small>SHIPMENTS</small>
                <strong>{rows.length}</strong>
                <span>In this view</span>
              </div>
              <div className="stat stat-teal">
                <small>DELIVERED</small>
                <strong>{del}</strong>
                <span>Completed</span>
              </div>
              <div className="stat stat-coral">
                <small>FAILED / RETURNED</small>
                <strong>{fail}</strong>
                <span>Didn’t reach the customer</span>
              </div>
              <div className="stat">
                <small>SUCCESS RATE</small>
                <strong>{closed ? Math.round((del / closed) * 100) : 0}%</strong>
                <span>Of closed deliveries</span>
              </div>
            </div>
          );
        })()}
      <PanelHeading
        title="Shipment register"
        aside={<span className="mono muted">{rows.length} records</span>}
      />
      {loading && !data ? (
        <Skeleton />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>TOKEN</th>
                <th>CUSTOMER</th>
                <th>ADDRESS</th>
                <th>RIDER</th>
                <th>SCHEDULED</th>
                <th>DELIVERED</th>
                <th>STATUS</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.token}>
                  <td className="mono">{r.token.slice(0, 8)}</td>
                  <td className="strong">{r.customerName}</td>
                  <td className="wrap">{r.address}</td>
                  <td>
                    {r.agentName ? (
                      <span className="row-rider">
                        <Avatar name={r.agentName} size="sm" />
                        {r.agentName}
                      </span>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td>{formatDateTime(r.scheduledAt)}</td>
                  <td>{r.deliveredAt ? formatDateTime(r.deliveredAt) : "—"}</td>
                  <td>
                    <LiveStatus value={r.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!rows.length && <Empty title="No records" body="Change the filters or date range." />}
        </div>
      )}
    </main>
  );
}

/* ---------- reports ---------- */
const RANGES = [
  ["7", "Last 7 days"],
  ["30", "Last 30 days"],
  ["90", "Last 90 days"],
] as const;
export function LiveReports({ mode }: { mode: "overview" | "trend" | "performance" }) {
  return (
    <OwnerGate>
      <ReportsBody mode={mode} />
    </OwnerGate>
  );
}
function ReportsBody({ mode }: { mode: "overview" | "trend" | "performance" }) {
  const [range, setRange] = useState("30");
  const tabs = [
    { to: "/owner/reports/overview", label: "Overview", m: "overview" },
    { to: "/owner/reports/trend", label: "Delivery trend", m: "trend" },
    { to: "/owner/reports/agents", label: "Rider performance", m: "performance" },
  ] as const;
  return (
    <main className="content">
      <PageHeader
        eyebrow="INSIGHTS · READ ONLY"
        title="Reports"
        description="How deliveries are going across your business."
        action={
          <Select value={range} onValueChange={setRange}>
            <SelectTrigger className="w-auto h-[46px] gap-2 rounded-lg" aria-label="Report period">
              <CalendarDays size={17} className="opacity-70" />
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {RANGES.map(([v, l]) => (
                <SelectItem key={v} value={v}>
                  {l}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        }
      />
      <nav className="tabs" aria-label="Report views">
        {tabs.map((t) => (
          <Link key={t.to} to={t.to} className={mode === t.m ? "active" : ""}>
            {t.label}
          </Link>
        ))}
      </nav>
      {mode === "overview" ? (
        <Overview range={range} />
      ) : mode === "trend" ? (
        <Trend days={range} />
      ) : (
        <Performance range={range} />
      )}
    </main>
  );
}
function Bars({ days, label }: { days: DayPoint[]; label: string }) {
  const max = Math.max(1, ...days.map((d) => d.total));
  if (!days.length)
    return <Empty title="No activity yet" body="Deliveries in this period will appear here." />;
  return (
    <div className="bar-chart live-bars" aria-label={label}>
      {days.map((d) => (
        <div
          key={d.date}
          className="bar-col"
          title={`${d.date}: ${d.delivered} delivered, ${d.failed} failed of ${d.total}`}
        >
          <div className="bar-track">
            <span style={{ height: `${(d.total / max) * 100}%` }}>
              <i style={{ height: d.total ? `${(d.delivered / d.total) * 100}%` : 0 }} />
            </span>
          </div>
          <small>{new Date(d.date).getDate()}</small>
        </div>
      ))}
    </div>
  );
}
function Overview({ range }: { range: string }) {
  const { data, error, loading, reload } = useLoad<OverviewReport>(
    () => getOverview({ range }),
    [range],
  );
  if (error) return <Banner error={error} retry={reload} />;
  if (loading && !data) return <Skeleton />;
  if (!data) return null;
  return (
    <>
      <div className="stats report-stats">
        <div className="stat">
          <small>TOTAL</small>
          <strong>{data.total}</strong>
          <span>Shipments</span>
        </div>
        <div className="stat stat-teal">
          <small>DELIVERED</small>
          <strong>{data.delivered}</strong>
          <span>{data.deliveredPct}% of total</span>
        </div>
        <div className="stat">
          <small>IN PROGRESS</small>
          <strong>{data.inProgress}</strong>
          <span>{data.inProgressPct}%</span>
        </div>
        <div className="stat stat-coral">
          <small>FAILED</small>
          <strong>{data.failed}</strong>
          <span>
            {data.failedPct}% · {data.returned} returned
          </span>
        </div>
      </div>
      <div className="stats">
        <div className="stat">
          <small>ON-TIME RATE</small>
          <strong>{data.onTimeRate}%</strong>
          <span>Delivered by schedule</span>
        </div>
        <div className="stat">
          <small>FIRST ATTEMPT</small>
          <strong>{data.firstAttemptRate}%</strong>
          <span>Delivered first try</span>
        </div>
        <div className="stat">
          <small>AVG DELIVERY</small>
          <strong>{data.avgDeliveryHours}h</strong>
          <span>Assignment to doorstep</span>
        </div>
      </div>
      <div className="report-two">
        <section>
          <PanelHeading
            title="Daily activity"
            aside={<span className="mono muted">teal = delivered</span>}
          />
          <Bars days={data.days} label="Daily shipments" />
        </section>
        <section>
          <PanelHeading title="Top riders" />
          {data.agentBars.length ? (
            <div className="hbar-list">
              {data.agentBars.map((b) => (
                <div key={b.name}>
                  <span>{b.name}</span>
                  <div className="hbar">
                    <i style={{ width: `${b.pct}%` }} />
                  </div>
                  <strong>{b.delivered}</strong>
                </div>
              ))}
            </div>
          ) : (
            <p className="muted">No deliveries in this range.</p>
          )}
          <PanelHeading title="Why attempts failed" />
          {data.reasons.length ? (
            <div className="hbar-list">
              {data.reasons.map((r) => (
                <div key={r.label}>
                  <span>{r.label}</span>
                  <div className="hbar hbar-coral">
                    <i style={{ width: `${r.pct}%` }} />
                  </div>
                  <strong>{r.count}</strong>
                </div>
              ))}
            </div>
          ) : (
            <p className="muted">No failures in this range.</p>
          )}
        </section>
      </div>
    </>
  );
}
function Trend({ days }: { days: string }) {
  const { data, error, loading, reload } = useLoad(() => getTrend({ days }), [days]);
  if (error) return <Banner error={error} retry={reload} />;
  if (loading && !data) return <Skeleton />;
  const rows = data?.rows ?? data?.days ?? [];
  return (
    <>
      <PanelHeading
        title="Delivery trend"
        aside={
          <span className="mono muted">
            {rows.length} of {days} days with data
          </span>
        }
      />
      <div className="chart-large">
        <Bars days={rows} label="Delivery trend" />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>DATE</th>
              <th>TOTAL</th>
              <th>DELIVERED</th>
              <th>FAILED</th>
              <th>IN PROGRESS</th>
            </tr>
          </thead>
          <tbody>
            {[...rows].reverse().map((d) => (
              <tr key={d.date}>
                <td className="mono">{d.date}</td>
                <td>{d.total}</td>
                <td>{d.delivered}</td>
                <td>{d.failed}</td>
                <td>{d.progress}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
function Performance({ range }: { range: string }) {
  const { data, error, loading, reload } = useLoad<AgentPerformanceRow[]>(
    () => getAgentPerformance({ range }),
    [range],
  );
  const [exporting, setExporting] = useState(false);
  if (error) return <Banner error={error} retry={reload} />;
  if (loading && !data) return <Skeleton />;
  return (
    <>
      <PanelHeading
        title="Rider performance"
        aside={
          <Button
            variant="outline"
            size="sm"
            disabled={exporting}
            onClick={async () => {
              setExporting(true);
              await exportCsv("agent-performance", { range });
              setExporting(false);
            }}
          >
            <Download /> Export CSV
          </Button>
        }
      />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>RIDER</th>
              <th>ASSIGNED</th>
              <th>DELIVERED</th>
              <th>FAILED</th>
              <th>RETURNED</th>
              <th>OPEN</th>
              <th>AVG HOURS</th>
              <th>PER DAY</th>
            </tr>
          </thead>
          <tbody>
            {(data ?? []).map((r) => (
              <tr key={r.id}>
                <td className="strong">
                  {r.name}
                  {!r.active && <span className="pill">Inactive</span>}
                </td>
                <td>{r.assigned}</td>
                <td>{r.delivered}</td>
                <td>{r.failed}</td>
                <td>{r.returned}</td>
                <td>{r.open}</td>
                <td>{r.avgHours == null ? "—" : `${Math.round(Number(r.avgHours) * 10) / 10}h`}</td>
                <td>
                  {r.perDay == null ? "—" : (Math.round(Number(r.perDay) * 10) / 10).toFixed(1)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!data?.length && (
          <Empty
            title="No riders yet"
            body="Performance appears once riders complete deliveries."
          />
        )}
      </div>
    </>
  );
}

/* ---------- account ---------- */
export function LiveAccount() {
  return (
    <OwnerGate>
      <AccountBody />
    </OwnerGate>
  );
}
function AccountBody() {
  const navigate = useNavigate();
  const { data, error, loading, reload } = useLoad<User>(() => me(), []);
  const [f, setF] = useState({ fullName: "", phone: "", businessName: "", businessPhone: "" });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  useEffect(() => {
    if (data)
      setF({
        fullName: data.name ?? "",
        phone: data.phone ?? "",
        businessName: data.businessName ?? "",
        businessPhone: data.businessPhone ?? "",
      });
  }, [data]);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await updateMe(f);
      setToast("Account saved");
      reload();
    } catch (e2) {
      setErr(errText(e2));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="content">
      <PageHeader
        eyebrow="WORKSPACE"
        title="Account"
        description="Your details and your business profile."
      />
      {error && <Banner error={error} retry={reload} />}
      {loading && !data ? (
        <Skeleton rows={3} />
      ) : (
        data && (
          <section className="profile-section">
            <div className="profile-identity">
              <span className="profile-avatar">{initials(data.name)}</span>
              <div>
                <h2>{data.name}</h2>
                <p>{data.email} · Owner</p>
              </div>
            </div>
            <form className="form-stack" onSubmit={save}>
              <div className="field-pair">
                <label>
                  Your name
                  <input required value={f.fullName} onChange={set("fullName")} />
                </label>
                <label>
                  Your phone
                  <input type="tel" value={f.phone} onChange={set("phone")} />
                </label>
              </div>
              <div className="field-pair">
                <label>
                  Business name
                  <input required value={f.businessName} onChange={set("businessName")} />
                </label>
                <label>
                  Business phone
                  <input
                    type="tel"
                    value={f.businessPhone}
                    onChange={set("businessPhone")}
                    placeholder="+91 ..."
                  />
                </label>
              </div>
              <p className="form-help">
                Your business name and phone appear on customer tracking pages. With a business
                phone saved, customers get <strong>Call</strong> and <strong>WhatsApp</strong>{" "}
                buttons to reach you. Customers contact you at this number — your sign-in email (
                {data.email}) is never shown.
              </p>
              {err && <Banner error={err} />}
              <div className="action-pair">
                <Button variant="coral" type="submit" disabled={busy}>
                  {busy && <Loader2 className="spin" />}Save changes
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    clearSession();
                    navigate({ to: "/login" });
                  }}
                >
                  <LogOut /> Sign out
                </Button>
              </div>
            </form>
          </section>
        )
      )}
      {toast && <Toast text={toast} onDone={() => setToast(null)} />}
    </main>
  );
}
