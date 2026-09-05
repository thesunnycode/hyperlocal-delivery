import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, Lock, Users, ArrowRight } from 'lucide-react';
import { getAgentPerformance, exportCsv } from '../../api/reportsApi';
import { useToast } from '../../lib/ToastContext.tsx';
import { useFatalError } from '../../lib/FatalErrorContext.tsx';
import { cx, initials } from '../../utils/format';
import type { AgentPerformanceRow } from '../../types/api';

/** Every column header is clickable, so the sort key is one of their ids. */
type SortKey =
  | 'name'
  | 'assigned'
  | 'delivered'
  | 'failed'
  | 'returned'
  | 'open'
  | 'avgHours'
  | 'perDay'
  | 'failRate';

const COLS: [SortKey, string][] = [
  ['name', 'Rider'],
  ['assigned', 'Assigned'],
  ['delivered', 'Delivered'],
  ['failed', 'Failed'],
  /* Counts alone cannot rank anyone: the agent with the most failures was
     also the agent with the most deliveries, and nothing on this table said
     whether that was good or bad. */
  ['failRate', 'Fail rate'],
  ['returned', 'Returned'],
  ['open', 'Open'],
  ['avgHours', 'Avg time (h)'],
  ['perDay', 'Per day']
];

/** Over this many hours, the inline bar turns red. */
const SLOW_HOURS = 4;
/** Full width of the inline average-time bar. */
const BAR_SCALE_HOURS = 6;

/** Share of finished attempts that failed. Null when nothing has finished. */
function failRate(a: AgentPerformanceRow): number | null {
  const done = (a.delivered ?? 0) + (a.failed ?? 0);
  return done === 0 ? null : (a.failed ?? 0) / done;
}

/**
 * AD3 — a ranking, so it is a table and not a chart: eight measures across a
 * handful of agents is a job for rows, and the one thing worth seeing as
 * shape (average time) gets an inline bar beside its own figure.
 *
 * Read-only, like the rest of this section. "Open" hands the agent to the
 * operations app, where edit and deactivate live.
 */
export default function AdminAgentPerformancePage() {
  const toast = useToast();
  const { reportError } = useFatalError();
  const [rows, setRows] = useState<AgentPerformanceRow[] | null>(null);
  const [sort, setSort] = useState<SortKey>('delivered');

  useEffect(() => {
    getAgentPerformance({}).then(setRows).catch(reportError);
  }, [reportError]);

  const doExport = async () => {
    const ok = await exportCsv('agent-performance').catch(() => false);
    if (ok) toast('CSV downloaded.', 'default');
    else toast('Export failed — try again.', 'accent');
  };

  const toolbar = (
    <>
      <div className="rp-head">
        <div>
          <h1>Rider performance</h1>
          <div className="sub">Read-only · sort by any column</div>
        </div>
        <button type="button" className="rp-btn" onClick={doExport}>
          <Download size={14} strokeWidth={2.1} /> Export CSV
        </button>
      </div>
    </>
  );

  if (!rows) {
    return (
      <>
        {toolbar}
        <div className="rp-wrap">
          <div className="rp-card"><div className="rp-sk-row-pad">
            {[1, 2, 3, 4, 5].map((r) => (
              <span key={r} className="rp-sk rp-sk-row" />
            ))}
          </div></div>
        </div>
      </>
    );
  }

  if (rows.length === 0) {
    return (
      <>
        {toolbar}
        <div className="rp-wrap">
          <div className="rp-state">
            <div className="ic"><Users size={24} strokeWidth={1.9} /></div>
            <h2>No riders to compare</h2>
            <p>Performance is computed from delivery riders on your account. Add one in the operations app and their figures appear here.</p>
            <div className="acts">
              <Link className="rp-btn" to="/owner/agents">
                Manage riders <ArrowRight size={13} strokeWidth={2.3} />
              </Link>
            </div>
          </div>
        </div>
      </>
    );
  }

  const sorted = rows.slice().sort((a, b) => {
    if (sort === 'name') return a.name.localeCompare(b.name);
    if (sort === 'failRate') return (failRate(b) ?? -1) - (failRate(a) ?? -1);
    return (b[sort] ?? 0) - (a[sort] ?? 0);
  });

  return (
    <>
      {toolbar}
      <div className="rp-wrap">
        <div className="rp-card">
          <div className="rp-card-h">
            <h2>All riders</h2>
            <span className="note">{rows.length} {rows.length === 1 ? 'rider' : 'riders'}</span>
          </div>
          <div className="rp-tablewrap">
            <table>
              <caption className="sr-only">
                Delivery riders and their outcomes. Every column heading sorts the table.
              </caption>
              <thead>
                <tr>
                  {COLS.map(([id, label], i) => (
                    <th key={id} scope="col" className={i === 0 ? undefined : 'r'}
                      aria-sort={sort !== id ? 'none' : id === 'name' ? 'ascending' : 'descending'}>
                      <button type="button" className="rp-sort" onClick={() => setSort(id)}
                        aria-label={`Sort by ${label}`}>
                        {label}<span className="ar" aria-hidden="true">▾</span>
                      </button>
                    </th>
                  ))}
                  <th scope="col" className="r"><span className="sr-only">Open in operations app</span></th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((a) => (
                  <tr key={a.id}>
                    <td>
                      <span className="rp-who2">
                        <span className="rp-av" aria-hidden="true">{initials(a.name)}</span>
                        <span><b>{a.name}</b><em>{a.active ? 'Active' : 'Inactive'}</em></span>
                      </span>
                    </td>
                    {/* E2 — nine numeric columns at one weight gave the eye
                        nothing to hold on to. Tinting the column being sorted
                        by marks the one the reader just chose to care about. */}
                    <td className={cx('r', sort === 'assigned' && 'on', 'rp-muted-cell')}>{a.assigned}</td>
                    <td className={cx('r', sort === 'delivered' && 'on', 'rp-bold-cell')}>{a.delivered}</td>
                    <td className={cx('r', sort === 'failed' && 'on', 'rp-bold-cell')} style={{ color: a.failed > 5 ? 'var(--rp-failed)' : 'var(--rp-ink)' }}>{a.failed}</td>
                    <td className={cx('r', sort === 'failRate' && 'on')}>
                      {failRate(a) === null
                        ? <span className="rp-muted-cell">—</span>
                        : <span style={{
                          fontWeight: 600,
                          color: failRate(a)! > 0.1 ? 'var(--rp-failed)' : 'var(--rp-ink)'
                        }}>{(failRate(a)! * 100).toFixed(1)}%</span>}
                    </td>
                    <td className={cx('r', sort === 'returned' && 'on', 'rp-muted-cell')}>{a.returned}</td>
                    <td className={cx('r', sort === 'open' && 'on')}>{a.open}</td>
                    {/* An agent who has delivered nothing has no average. The
                        table used to print the server's 0 as "6.2h / 2.6 per
                        day" for a rider hired yesterday. */}
                    <td className={cx('r', sort === 'avgHours' && 'on')}>
                      {(a.delivered ?? 0) === 0
                        ? <span className="rp-muted-cell">—</span>
                        : (
                          <span className="rp-mini">
                            <span>{(a.avgHours ?? 0).toFixed(1)}</span>
                            <span className="tr">
                              <i
                                title={(a.avgHours ?? 0) > SLOW_HOURS
                                  ? `Over ${SLOW_HOURS}h average`
                                  : `Under ${SLOW_HOURS}h average`}
                                style={{
                                  width: `${Math.min(((a.avgHours ?? 0) / BAR_SCALE_HOURS) * 100, 100)}%`,
                                  background: (a.avgHours ?? 0) > SLOW_HOURS ? 'var(--rp-failed)' : 'var(--rp-neutral)'
                                }} />
                            </span>
                          </span>
                        )}
                    </td>
                    <td className={cx('r', sort === 'perDay' && 'on')}>
                      {(a.delivered ?? 0) === 0
                        ? <span className="rp-muted-cell">—</span>
                        : (a.perDay ?? 0).toFixed(1)}
                    </td>
                    <td className="r">
                      <Link className="rp-link" to="/owner/agents"
                        aria-label={`Open ${a.name} in the operations app`}>Open →</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="rp-lock rp-lock-tight">
            <Lock size={14} strokeWidth={2.1} />
            {/* The red bars had no legend: every agent read as a warning and
                nothing said what the colour meant or what the bar was full
                of. Both thresholds are named here. */}
            <span>
              Averages exclude shipments still in progress, and a rider with no completed
              delivery shows &ldquo;&mdash;&rdquo; rather than a zero. The time bar fills at{' '}
              {BAR_SCALE_HOURS}h and turns red above {SLOW_HOURS}h; a fail rate over 10% is
              red too. Editing and deactivating a rider happen in the operations app —
              this section never writes.
            </span>
          </p>
        </div>
      </div>
    </>
  );
}
