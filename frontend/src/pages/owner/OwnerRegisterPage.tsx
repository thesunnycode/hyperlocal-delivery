import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Filter, Smartphone, Download, ArrowUp, ArrowDown } from 'lucide-react';
import { listShipments } from '../../api/shipmentsApi';
import { listAgents } from '../../api/agentsApi';
import { exportCsv } from '../../api/reportsApi';
import { useToast } from '../../lib/ToastContext.tsx';
import { useFatalError } from '../../lib/FatalErrorContext.tsx';
import { formatDateTime, shortToken, cx } from '../../utils/format';
import { STATUS_META } from '../../utils/statusMachine';
import type { AgentSummary, ShipmentStatus, ShipmentSummary } from '../../types/api';

const STATUS_OPTIONS: ['all' | ShipmentStatus, string][] = [
  ['all', 'All statuses'], ['assigned', 'Assigned'], ['picked_up', 'Picked up'],
  ['in_transit', 'In transit'], ['out_for_delivery', 'Out for delivery'],
  ['delivered', 'Delivered'], ['failed', 'Failed'], ['returned', 'Returned']
];

/**
 * OD2 — the full filterable register, desktop-dense.
 *
 * Refero: Fingerprint 0e472ed6 and Mercury 69c62f45 — a compact toolbar over
 * full-width rows with a STICKY header, so the column names survive a long
 * scroll. Search is client-side over the rows already fetched; the API has no
 * text-search parameter and this screen adds no backend call.
 *
 * Mobile browses the same data as rows on the Shipments tab, which is why the
 * sidebar hides this tab under 760px.
 *
 * This is now the ONLY register. /admin/register was a second one — same
 * seven columns over the same shipments, with a narrower filter set, a
 * different pill style and a read-only inspect drawer that showed less than
 * the queue's own detail pane does. Its CSV export was the one thing it had
 * that this page did not, so that moved here and the page went.
 *
 * ── Redesign ────────────────────────────────────────────────────────────
 * The sort affordance was already complete — direction arrows, an `on` state,
 * a muted hint arrow on unsorted columns and real `aria-sort` — so the audit
 * finding that asked for it was reading the mockup, not this file.
 *
 * What was actually wrong here was presentation living in the component:
 * inline `style=` props on cells, a seven-element array of magic pixel widths
 * for the skeleton, and a `<style>` element rendered *inside the component*,
 * which ships a duplicate copy of its rules into the DOM on every mount and
 * sits outside the cascade so nothing can override it. All three moved to
 * `scopes.css`.
 *
 * Also added: a count beside "Clear filters". It appeared with no indication
 * of how many filters were on, so an owner looking at a short register could
 * not tell whether they were seeing a narrow slice or an empty business.
 */

/** Columns the table can be ordered by. `null` keeps the server's order. */
type SortKey = 'customerName' | 'status' | 'agentName' | 'scheduledAt' | 'createdAt';
type Sort = { key: SortKey; dir: 'asc' | 'desc' } | null;

const COLUMNS: { key: SortKey | null; label: string; cell?: string }[] = [
  { key: 'customerName', label: 'Customer' },
  { key: null, label: 'Address' },
  { key: 'status', label: 'Status' },
  { key: 'agentName', label: 'Rider' },
  { key: null, label: 'Token' },
  { key: 'scheduledAt', label: 'Scheduled' },
  { key: 'createdAt', label: 'Created' }
];

/** Skeleton cell classes, in column order. */
const SK = ['ow-sk-c1', 'ow-sk-c2', 'ow-sk-c3', 'ow-sk-c4', 'ow-sk-c5', 'ow-sk-c6', 'ow-sk-c7'];

export default function OwnerRegisterPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { reportError } = useFatalError();
  const [rows, setRows] = useState<ShipmentSummary[] | null>(null);
  const [agents, setAgents] = useState<AgentSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState<'all' | ShipmentStatus>('all');
  const [agentFilter, setAgentFilter] = useState('all');
  const [q, setQ] = useState('');
  /* `GET /shipments` has taken from/to all along and nothing was sending
     them, so a register of any age could only be narrowed by status, agent
     and a client-side text match. Both ends are optional and independent. */
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [sort, setSort] = useState<Sort>(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => { listAgents({}).then(setAgents).catch(() => {}); }, []);
  useEffect(() => {
    setRows(null);
    listShipments({
      status: statusFilter === 'all' ? undefined : statusFilter,
      agentId: agentFilter === 'all' ? undefined : agentFilter,
      from: from || undefined,
      to: to || undefined
    }).then(setRows).catch(reportError);
  }, [statusFilter, agentFilter, from, to, reportError]);

  const visible = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!rows) return [];
    const matched = !needle ? rows : rows.filter((r) => (
      r.customerName.toLowerCase().includes(needle)
      || r.address.toLowerCase().includes(needle)
      || r.token.toLowerCase().includes(needle)
    ));
    if (!sort) return matched;
    const { key, dir } = sort;
    const sign = dir === 'asc' ? 1 : -1;
    return matched.slice().sort((a, b) => {
      // Status sorts by its position in the delivery flow, not alphabetically:
      // "Assigned, Delivered, Failed, In transit" is not an ordering anyone
      // reading a register wants.
      if (key === 'status') {
        return sign * ((STATUS_META[a.status]?.step ?? 0) - (STATUS_META[b.status]?.step ?? 0));
      }
      const av = a[key] ?? '';
      const bv = b[key] ?? '';
      // Unscheduled rows sort last whichever way the column points, rather
      // than clustering at whichever end an empty string happens to fall.
      if (!av && bv) return 1;
      if (av && !bv) return -1;
      if (key === 'scheduledAt' || key === 'createdAt') {
        return sign * (new Date(String(av)).getTime() - new Date(String(bv)).getTime());
      }
      return sign * String(av).localeCompare(String(bv));
    });
  }, [rows, q, sort]);

  const toggleSort = (key: SortKey) => setSort((s) =>
    s?.key !== key ? { key, dir: 'asc' }
      : s.dir === 'asc' ? { key, dir: 'desc' }
        : null);

  /* Counted, not just flagged. Search and the date pair each count once —
     a half-open range is one narrowing, not two. */
  const activeCount = [
    statusFilter !== 'all',
    agentFilter !== 'all',
    q.trim() !== '',
    !!from || !!to
  ].filter(Boolean).length;
  const filtered = activeCount > 0;

  const clear = () => {
    setStatusFilter('all'); setAgentFilter('all'); setQ('');
    setFrom(''); setTo(''); setSort(null);
  };

  const doExport = async () => {
    if (exporting) return;
    setExporting(true);
    const ok = await exportCsv('register', {
      status: statusFilter === 'all' ? undefined : statusFilter,
      agentId: agentFilter === 'all' ? undefined : agentFilter,
      from: from || undefined,
      to: to || undefined
    }).catch(() => false);
    setExporting(false);
    toast(ok ? 'CSV downloaded.' : 'Export failed — try again.', ok ? 'default' : 'accent');
  };

  return (
    <>
      <div className="ow-head">
        <div>
          <h1>Register</h1>
          <div className="sub">Every shipment, filterable and sortable. Select a row to open it.</div>
        </div>
        <button type="button" className="ow-btn" onClick={doExport} disabled={exporting}>
          <Download size={14} strokeWidth={2.1} /> {exporting ? 'Exporting…' : 'Export CSV'}
        </button>
      </div>

      <div className="ow-bar">
        <input className="ow-search" value={q} onChange={(e) => setQ(e.target.value)}
          aria-label="Search customer, address or token"
          placeholder="Search customer, address or token" />
        <select className="ow-chip ow-chip-select" value={statusFilter}
          aria-label="Status" onChange={(e) => setStatusFilter(e.target.value as 'all' | ShipmentStatus)}>
          {STATUS_OPTIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        <select className="ow-chip ow-chip-select" value={agentFilter}
          aria-label="Rider" onChange={(e) => setAgentFilter(e.target.value)}>
          <option value="all">All riders</option>
          {agents.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
        </select>
        <span className="ow-dates">
          <label htmlFor="reg-from">From</label>
          <input id="reg-from" type="date" value={from} max={to || undefined}
            onChange={(e) => setFrom(e.target.value)} />
          <label htmlFor="reg-to">to</label>
          <input id="reg-to" type="date" value={to} min={from || undefined}
            onChange={(e) => setTo(e.target.value)} />
        </span>
        {filtered && (
          <>
            <span className="ow-fcount" aria-hidden="true">{activeCount}</span>
            <button type="button" className="ow-linky" onClick={clear}>
              Clear {activeCount === 1 ? 'filter' : `all ${activeCount} filters`}
            </button>
          </>
        )}
        <span className="ow-spacer" />
        {/* R6: ow-row-count replaces inline fontFamily/fontSize/color */}
        <span className="ow-row-count">
          {rows === null ? '—' : `${visible.length} ${visible.length === 1 ? 'row' : 'rows'}`}
        </span>
      </div>

      <div className="ow-tablewrap ow-desk">
        <table>
          <caption className="sr-only">
            Every shipment. Selecting a row opens it in the shipments queue.
          </caption>
          <thead>
            <tr>
              {COLUMNS.map(({ key, label }) => (
                <th key={label} scope="col"
                  aria-sort={!key || sort?.key !== key
                    ? undefined
                    : sort.dir === 'asc' ? 'ascending' : 'descending'}>
                  {key ? (
                    <button type="button" className={cx('ow-sort', sort?.key === key && 'on')}
                      onClick={() => toggleSort(key)}>
                      {label}
                      {sort?.key === key
                        ? (sort.dir === 'asc'
                          ? <ArrowUp size={12} strokeWidth={2.4} aria-hidden="true" />
                          : <ArrowDown size={12} strokeWidth={2.4} aria-hidden="true" />)
                        : <ArrowDown size={12} strokeWidth={2.4} className="hint" aria-hidden="true" />}
                    </button>
                  ) : label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows === null
              ? [1, 2, 3, 4, 5, 6, 7, 8].map((r) => (
                <tr key={r}>
                  {SK.map((sk) => (
                    <td key={sk}><span className={cx('ow-sk', sk)} /></td>
                  ))}
                </tr>
              ))
              : visible.map((r) => (
                <tr key={r.id} className="rw" role="button" tabIndex={0}
                  aria-label={`Open shipment for ${r.customerName}`}
                  onClick={() => navigate(`/owner/shipments/${r.id}`)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      navigate(`/owner/shipments/${r.id}`);
                    }
                  }}>
                  <td className="c-name">{r.customerName}</td>
                  <td className="c-addr">{r.address}</td>
                  <td><span className={`ow-badge b-${r.status}`}>{STATUS_META[r.status]?.label ?? r.status}</span></td>
                  <td>{r.agentName || '—'}</td>
                  {/* D2 — measured, the full UUID was the second-widest column on the
                      screen (276px against 108px for Customer) and nobody reads it.
                      The short form identifies a row; the full value is one click
                      away in the record itself. */}
                  <td className="mono" title={r.token}>{shortToken(r.token)}</td>
                  <td className="mono">{formatDateTime(r.scheduledAt)}</td>
                  <td className="mono">{formatDateTime(r.createdAt)}</td>
                </tr>
              ))}
          </tbody>
        </table>

        {rows !== null && visible.length === 0 && (
          <div className="ow-tablebody">
            <div className="ow-state">
              <div className="ic"><Filter size={24} strokeWidth={1.9} /></div>
              <h2>Nothing matches</h2>
              <p>
                {filtered
                  ? `No shipment fits ${activeCount === 1 ? 'that filter' : `those ${activeCount} filters`} together. Your other shipments are still here.`
                  : 'Nothing has been shipped yet, so the register is empty.'}
              </p>
              {filtered && (
                <div className="acts">
                  <button type="button" className="ow-btn" onClick={clear}>
                    Clear {activeCount === 1 ? 'filter' : `all ${activeCount} filters`}
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      <div className="ow-pane ow-mob">
        <div className="ow-state">
          <div className="ic"><Smartphone size={24} strokeWidth={1.9} /></div>
          <h2>The register is desktop-only</h2>
          <p>Seven columns cannot be read on a phone. Browse and filter the same shipments from the Shipments tab instead.</p>
          <div className="acts">
            <button type="button" className="ow-btn pri" onClick={() => navigate('/owner/shipments')}>
              Go to Shipments
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
