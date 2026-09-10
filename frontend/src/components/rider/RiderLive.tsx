import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import {
  ArrowLeft,
  Check,
  ChevronRight,
  Loader2,
  LogOut,
  MapPin,
  Phone,
  RefreshCw,
  Undo2,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { AgentFrame, PageHeader, Empty } from "@/components/DeliveryApp";
import {
  Banner,
  Confirm,
  LiveStatus,
  Modal,
  Skeleton,
  Toast,
  errText,
  useLoad,
} from "@/components/owner/OwnerLive";
import { clearSession, getRole, getToken } from "@/lib/hl/apiClient";
import { me, updateMe } from "@/lib/hl/authApi";
import {
  confirmPickup,
  getShipment,
  listMyShipments,
  logFailedAttempt,
  markDelivered,
  markOutForDelivery,
  markReturned,
  startTransit,
} from "@/lib/hl/shipmentsApi";
import { AGENT_NEXT, FAILURE_REASONS, FLOW, STATUS_META, isTerminal } from "@/lib/hl/statusMachine";
import { formatDateTime, formatTime, initials, mapsHref, telHref } from "@/lib/hl/format";
import { SwipeAction, playChime } from "@/components/ux";
import type { FailureReason, Shipment, ShipmentSummary, User } from "@/lib/hl/types";

function RiderGate({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [ok, setOk] = useState(false);
  useEffect(() => {
    if (!getToken()) {
      navigate({ to: "/login" });
      return;
    }
    if (getRole() === "OWNER") {
      navigate({ to: "/owner/shipments" });
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
  return <AgentFrame>{children}</AgentFrame>;
}

/* ---------- assignments ---------- */
const FILTERS: { id: string; label: string; match: (s: ShipmentSummary) => boolean }[] = [
  { id: "open", label: "To do", match: (s) => !isTerminal(s.status) && s.status !== "failed" },
  { id: "all", label: "All", match: () => true },
  { id: "failed", label: "Failed", match: (s) => s.status === "failed" },
  { id: "done", label: "Done", match: (s) => isTerminal(s.status) },
];

export function LiveAssignments() {
  return (
    <RiderGate>
      <AssignmentsBody />
    </RiderGate>
  );
}
function AssignmentsBody() {
  const { data, error, loading, reload } = useLoad(async () => {
    const [open, done, returned] = await Promise.all([
      listMyShipments(),
      listMyShipments({ status: "delivered" }),
      listMyShipments({ status: "returned" }).catch(() => []),
    ]);
    const m = new Map<number, ShipmentSummary>();
    [...(open ?? []), ...(done ?? []), ...(returned ?? [])].forEach((x) => m.set(x.id, x));
    return [...m.values()];
  }, []);
  const [filter, setFilter] = useState("open");
  useEffect(() => {
    const t = setInterval(reload, 30_000);
    return () => clearInterval(t);
  }, [reload]);
  const ships = data ?? [];
  const seen = useRef<Set<number> | null>(null);
  const [fresh, setFresh] = useState<number | null>(null);
  const [nudge, setNudge] = useState<string | null>(null);
  useEffect(() => {
    if (!data) return;
    const open = data.filter((x) => !isTerminal(x.status) && x.status !== "failed");
    if (seen.current) {
      const added = open.filter((x) => !seen.current!.has(x.id));
      if (added.length) {
        setFresh(added[0]!.id);
        setNudge(
          added.length > 1
            ? `${added.length} new deliveries assigned`
            : `New delivery for ${added[0]!.customerName}`,
        );
        playChime();
        setTimeout(() => setFresh(null), 6000);
      }
    }
    seen.current = new Set(open.map((x) => x.id));
  }, [data]);
  const active = FILTERS.find((f) => f.id === filter) ?? FILTERS[0]!;
  const visible = useMemo(
    () =>
      ships
        .filter(active.match)
        .slice()
        .sort((a, b) => (a.scheduledAt ?? "").localeCompare(b.scheduledAt ?? "")),
    [ships, active],
  );
  const next = ships
    .filter((s) => !isTerminal(s.status) && s.status !== "failed")
    .sort((a, b) => (a.scheduledAt ?? "").localeCompare(b.scheduledAt ?? ""))[0];
  return (
    <main className="agent-content">
      <PageHeader
        eyebrow="RIDER WORKSPACE"
        title="Your deliveries"
        description="Tap a delivery to move it to the next step."
        action={
          <Button variant="outline" size="icon" onClick={reload} aria-label="Refresh">
            <RefreshCw className={loading ? "spin" : ""} />
          </Button>
        }
      />
      <div className="stats rider-stats">
        <div className="stat stat-teal">
          <small>TO DO</small>
          <strong>{ships.filter(FILTERS[0]!.match).length}</strong>
          <span>Open deliveries</span>
        </div>
        <div className="stat">
          <small>DELIVERED · 7D</small>
          <strong>
            {
              ships.filter(
                (s) =>
                  s.status === "delivered" &&
                  s.deliveredAt &&
                  Date.now() - new Date(s.deliveredAt).getTime() <= 7 * 864e5,
              ).length
            }
          </strong>
          <span>Last 7 days</span>
        </div>
        <div className="stat stat-coral">
          <small>FAILED</small>
          <strong>{ships.filter((s) => s.status === "failed").length}</strong>
          <span>Waiting on owner</span>
        </div>
      </div>
      {next && (
        <Link
          to="/agent/shipments/$id"
          params={{ id: String(next.id) }}
          className={`next-up ${fresh ? "next-up-fresh" : ""}`}
        >
          <small>UP NEXT · {next.scheduledAt ? formatTime(next.scheduledAt) : ""}</small>
          <strong>{next.customerName}</strong>
          <span>{next.address}</span>
          <em>
            {AGENT_NEXT[next.status]?.cta ?? "Open"} <ChevronRight size={16} />
          </em>
        </Link>
      )}
      <div className="filter-scroll" role="group" aria-label="Filter deliveries">
        {FILTERS.map((f) => (
          <Button
            key={f.id}
            variant={filter === f.id ? "selected" : "filter"}
            onClick={() => setFilter(f.id)}
          >
            {f.label} <span className="chip-count">{ships.filter(f.match).length}</span>
          </Button>
        ))}
      </div>
      {error && <Banner error={error} retry={reload} />}
      {nudge && <Toast text={nudge} onDone={() => setNudge(null)} />}
      {loading && !data ? (
        <Skeleton />
      ) : visible.length ? (
        <div className="shipment-list">
          {visible.map((s) => (
            <Link
              key={s.id}
              to="/agent/shipments/$id"
              params={{ id: String(s.id) }}
              className={`shipment-row ${fresh === s.id ? "row-fresh" : ""}`}
            >
              <div className="row-top">
                <span className="mono muted">#{s.token.slice(0, 8)}</span>
                <LiveStatus value={s.status} />
                <span className="row-time">
                  {s.scheduledAt ? formatDateTime(s.scheduledAt) : ""}
                </span>
              </div>
              <div className="row-main">
                <div>
                  <h3>{s.customerName}</h3>
                  <p>{s.address}</p>
                </div>
                <ChevronRight size={18} />
              </div>
            </Link>
          ))}
        </div>
      ) : (
        <Empty
          title={filter === "open" ? "All caught up" : "Nothing here"}
          body={
            filter === "open"
              ? "New deliveries appear here as soon as your business assigns them."
              : "Try another filter."
          }
        />
      )}
    </main>
  );
}

/* ---------- delivery detail ---------- */
type Action = {
  title: string;
  body: string;
  action: string;
  danger?: boolean;
  run: () => Promise<Shipment>;
};
export function LiveAgentDetail() {
  return (
    <RiderGate>
      <DetailBody />
    </RiderGate>
  );
}
function DetailBody() {
  const { id } = useParams({ from: "/agent/shipments/$id" });
  const { data: loaded, error, loading, reload } = useLoad(() => getShipment(id), [id]);
  const [s, setS] = useState<Shipment | null>(null);
  useEffect(() => {
    if (loaded) setS(loaded);
  }, [loaded]);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [actErr, setActErr] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<Action | null>(null);
  const [failing, setFailing] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  if (error)
    return (
      <main className="agent-content">
        <BackLink />
        <Banner error={error} retry={reload} />
      </main>
    );
  if (loading && !s)
    return (
      <main className="agent-content">
        <BackLink />
        <Skeleton rows={3} />
      </main>
    );
  if (!s) return null;
  const step = AGENT_NEXT[s.status];
  const current = STATUS_META[s.status]?.step ?? 1;
  const branched = s.status === "failed" || s.status === "returned" || s.status === "cancelled";
  const terminalNote =
    s.status === "failed"
      ? "Attempt logged. Your business will reassign it for another try."
      : s.status === "returned"
        ? "Returned to the business. Nothing more to do."
        : s.status === "cancelled"
          ? "Cancelled by the business. No action needed."
          : s.status === "delivered"
            ? "Delivered. Great job!"
            : null;

  async function run(fn: () => Promise<Shipment>, msg: string) {
    setBusy(true);
    setActErr(null);
    try {
      const r = await fn();
      setS(r);
      setNote("");
      setToast(msg);
    } catch (e) {
      setActErr(errText(e));
    } finally {
      setBusy(false);
    }
  }
  function forward(): void {
    if (!s || !step) return;
    const n = note || null;
    if (s.status === "assigned") {
      void run(() => confirmPickup(s.id, n), "Pickup confirmed");
      return;
    }
    if (s.status === "picked_up") {
      void run(() => startTransit(s.id, n), "On the way");
      return;
    }
    if (s.status === "in_transit") {
      void run(() => markOutForDelivery(s.id, n), "Out for delivery");
      return;
    }
    // The slide gesture itself is the confirmation for the irreversible step.
    if (s.status === "out_for_delivery") void run(() => markDelivered(s.id, n), "Delivered");
  }

  return (
    <main className="agent-content agent-detail-live">
      <BackLink />
      <section className="delivery-hero">
        <div className="panel-title">
          <div>
            <p className="eyebrow">#{s.token.slice(0, 8).toUpperCase()}</p>
            <h1>{s.customerName}</h1>
          </div>
          <LiveStatus value={s.status} />
        </div>
        <ol
          className={`journey-strip ${branched ? "journey-branched" : ""}`}
          aria-label="Delivery progress"
        >
          {FLOW.map((f) => {
            const n = STATUS_META[f].step;
            return (
              <li
                key={f}
                className={n < current || (n === current && !branched) ? "done" : ""}
                aria-current={f === s.status ? "step" : undefined}
              >
                <span />
                <small>{STATUS_META[f].label}</small>
              </li>
            );
          })}
        </ol>
      </section>
      <section className="contact-cards">
        <a href={mapsHref(s.address)} target="_blank" rel="noreferrer" className="contact-card">
          <MapPin />
          <div>
            <small>ADDRESS · OPEN MAPS</small>
            <strong>{s.address}</strong>
          </div>
        </a>
        <a href={telHref(s.customerPhone)} className="contact-card">
          <Phone />
          <div>
            <small>CALL CUSTOMER</small>
            <strong>{s.customerPhone}</strong>
          </div>
        </a>
        <div className="contact-card">
          <div>
            <small>SCHEDULED</small>
            <strong>{formatDateTime(s.scheduledAt)}</strong>
          </div>
        </div>
      </section>
      {terminalNote && (
        <div className={`terminal-note ${s.status === "delivered" ? "ok" : ""}`}>
          {s.status === "delivered" ? <Check /> : null}
          {terminalNote}
        </div>
      )}
      {s.attempts.length > 0 && (
        <section>
          <h2 className="mini-heading">Delivery attempts</h2>
          <ul className="attempt-list">
            {s.attempts.map((a) => (
              <li key={a.id}>
                <strong>
                  Attempt {a.attemptNumber ?? a.no} · {a.failureReason ?? a.reason}
                </strong>
                <span>{formatDateTime(a.attemptedAt ?? a.stamp)}</span>
                {a.note && <p>{a.note}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}
      <section>
        <h2 className="mini-heading">History</h2>
        <ol className="live-timeline">
          {[...s.events].reverse().map((ev, i) => (
            <li key={i}>
              <span className="tl-dot" />
              <div>
                <strong>{ev.label ?? STATUS_META[ev.toStatus ?? ev.status]?.label}</strong>
                <small>{formatDateTime(ev.createdAt ?? ev.stamp)}</small>
                {ev.notes && <p>{ev.notes}</p>}
              </div>
            </li>
          ))}
        </ol>
      </section>
      {step && (
        <div className="agent-dock">
          <p className="eyebrow">{step.kind}</p>
          <label className="dock-note">
            Note for your business (optional)
            <input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="e.g. Left with security"
            />
          </label>
          {actErr && <Banner error={actErr} />}
          {s.status === "out_for_delivery" ? (
            <SwipeAction label={step.cta} busy={busy} onConfirm={forward} />
          ) : (
            <Button variant="coral" className="dock-go" disabled={busy} onClick={forward}>
              {busy ? <Loader2 className="spin" /> : <ChevronRight size={20} />}
              <span>{step.cta}</span>
            </Button>
          )}
          {step.fork && (
            <div className="action-pair">
              <Button variant="outline" onClick={() => setFailing(true)}>
                <X /> Couldn’t deliver
              </Button>
              <Button
                variant="outline"
                onClick={() =>
                  setConfirm({
                    title: "Return to the business?",
                    body: "Use this when the parcel is going back to the shop. This closes the delivery.",
                    action: "Mark returned",
                    danger: true,
                    run: () => markReturned(s.id, note || null),
                  })
                }
              >
                <Undo2 /> Return parcel
              </Button>
            </div>
          )}
        </div>
      )}
      {confirm && (
        <Confirm
          title={confirm.title}
          body={confirm.body}
          action={confirm.action}
          danger={!!confirm.danger}
          onClose={() => setConfirm(null)}
          onConfirm={async () => {
            const r = await confirm.run();
            setS(r);
            setNote("");
            setToast(STATUS_META[r.status].label);
          }}
        />
      )}
      {failing && (
        <FailSheet
          id={s.id}
          onClose={() => setFailing(false)}
          onDone={(r) => {
            setFailing(false);
            setS(r);
            setToast("Attempt logged");
          }}
        />
      )}
      {toast && <Toast text={toast} onDone={() => setToast(null)} />}
    </main>
  );
}
function BackLink() {
  return (
    <Link to="/agent/assignments" className="back-link">
      <ArrowLeft size={17} /> All deliveries
    </Link>
  );
}
function FailSheet({
  id,
  onClose,
  onDone,
}: {
  id: number;
  onClose: () => void;
  onDone: (s: Shipment) => void;
}) {
  const [reason, setReason] = useState<FailureReason | "">("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!reason) {
      setErr("Pick a reason.");
      return;
    }
    if (reason === "Other" && !notes.trim()) {
      setErr("Add a short note for “Other”.");
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      onDone(await logFailedAttempt(id, { reason, notes: notes.trim() || null }));
    } catch (e2) {
      setErr(errText(e2));
      setBusy(false);
    }
  }
  return (
    <Modal label="Log failed attempt" eyebrow="FAILED ATTEMPT" onClose={onClose}>
      <h2>What happened?</h2>
      <p className="dialog-sub">The status changes only after you log a reason.</p>
      <form className="form-stack" onSubmit={submit}>
        <div className="reason-grid" role="radiogroup" aria-label="Reason">
          {FAILURE_REASONS.map((r) => (
            <button
              type="button"
              key={r}
              role="radio"
              aria-checked={reason === r}
              className={`reason-chip ${reason === r ? "on" : ""}`}
              onClick={() => setReason(r)}
            >
              {r}
            </button>
          ))}
        </div>
        <label>
          Notes {reason === "Other" ? "" : "(optional)"}
          <textarea
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Anything your business should know"
          />
        </label>
        {err && <Banner error={err} />}
        <Button variant="coral" type="submit" disabled={busy}>
          {busy && <Loader2 className="spin" />}Log failed attempt
        </Button>
      </form>
    </Modal>
  );
}

/* ---------- account ---------- */
export function LiveAgentAccount() {
  return (
    <RiderGate>
      <AgentAccountBody />
    </RiderGate>
  );
}
function AgentAccountBody() {
  const navigate = useNavigate();
  const { data, error, loading, reload } = useLoad<User>(() => me(), []);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  useEffect(() => {
    if (data) {
      setName(data.name ?? "");
      setPhone(data.phone ?? "");
    }
  }, [data]);
  return (
    <main className="agent-content">
      <PageHeader eyebrow="RIDER WORKSPACE" title="Account" description="Your profile." />
      {error && <Banner error={error} retry={reload} />}
      {loading && !data ? (
        <Skeleton rows={2} />
      ) : (
        data && (
          <section className="profile-section">
            <div className="profile-identity">
              <span className="profile-avatar">{initials(data.name)}</span>
              <div>
                <h2>{data.name}</h2>
                <p>
                  {data.email} · Rider at {data.businessName}
                </p>
              </div>
            </div>
            <form
              className="form-stack"
              onSubmit={async (e) => {
                e.preventDefault();
                setBusy(true);
                setErr(null);
                try {
                  await updateMe({ fullName: name.trim(), phone: phone.trim() });
                  setToast("Saved");
                  reload();
                } catch (e2) {
                  setErr(errText(e2));
                } finally {
                  setBusy(false);
                }
              }}
            >
              <label>
                Your name
                <input required value={name} onChange={(e) => setName(e.target.value)} />
              </label>
              <label>
                Phone
                <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
              </label>
              {err && <Banner error={err} />}
              <div className="action-pair">
                <Button variant="coral" type="submit" disabled={busy}>
                  {busy && <Loader2 className="spin" />}Save
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
