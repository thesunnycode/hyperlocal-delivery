import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BarChart3, Calendar, ArrowRight } from 'lucide-react';
import { getOverview } from '../../api/reportsApi';
import { useFatalError } from '../../lib/FatalErrorContext.tsx';
import DayBars from '../../components/DayBars.tsx';
import type { OverviewReport } from '../../types/api';

/**
 * AD1 — read-only. Nothing here writes.
 *
 * Structure follows the shape every analytics console in the Refero set uses
 * (Dub, n8n, Shopify, Fourthwall, Square): filter toolbar, KPI strip, the
 * chart, then breakdown cards. Dub's breakdown card is the specific borrow —
 * a proportional tinted bar behind the label with the count on the right,
 * which needs no axes and stays readable at card size.
 *
 * Delivered and failed are TWO charts rather than one stack. See DayBars for
 * the measurement that forced it: the two status hues are ΔE 4.2 apart under
 * deuteranopia, so stacked they are one colour to about one reader in twelve.
 */

type OverviewState = OverviewReport | { empty: true };

const RANGES: [string, string][] = [['7', '7 days'], ['30', '30 days'], ['90', '90 days']];

export default function AdminOverviewPage() {
  const navigate = useNavigate();
  const { reportError } = useFatalError();
  const [range, setRange] = useState('30');
  const [data, setData] = useState<OverviewState | null>(null);

  useEffect(() => {
    setData(null);
    getOverview({ range }).then(setData).catch((e: unknown) => {
      const err = (e ?? {}) as { status?: number; body?: { empty?: boolean } | null };
      if (err.status === 404 || err.body?.empty) setData({ empty: true });
      else reportError(e);
    });
  }, [range, reportError]);

  const toolbar = (
    <>
      <div className="rp-head">
        <div>
          <h1>Overview</h1>
          <div className="sub">Read-only · last {range} days</div>
        </div>
        <button type="button" className="rp-btn" onClick={() => navigate('/owner/register')}>
          Open register <ArrowRight size={14} strokeWidth={2.3} />
        </button>
      </div>
      <div className="rp-bar" role="group" aria-label="Date range">
        {RANGES.map(([v, l]) => (
          <button key={v} type="button" className="rp-chip"
            aria-pressed={range === v} onClick={() => setRange(v)}>{l}</button>
        ))}
      </div>
    </>
  );

  if (!data) {
    return (
      <>
        {toolbar}
        <div className="rp-wrap">
          <dl className="rp-kpis" aria-hidden="true">
            {['Delivered', 'On time', 'Failed', 'First attempt', 'Avg delivery'].map((l) => (
              <div className="rp-kpi" key={l}>
                <dt>{l}</dt>
                <dd><span className="rp-sk rp-sk-kpi" /></dd>
              </div>
            ))}
          </dl>
          <div className="rp-card"><div className="rp-multi">
            <span className="rp-sk rp-sk-title-1" />
            <span className="rp-sk rp-sk-bars-1" />
            <span className="rp-sk rp-sk-title-2" />
            <span className="rp-sk rp-sk-bars-2" />
          </div></div>
        </div>
      </>
    );
  }

  if ('empty' in data) {
    return (
      <>
        {toolbar}
        <div className="rp-wrap">
          <div className="rp-state">
            <div className="ic"><BarChart3 size={24} strokeWidth={1.9} /></div>
            <h2>Nothing to report yet</h2>
            <p>No shipments closed in the last {range} days, so there are no rates to compute.</p>
            <div className="acts">
              <button type="button" className="rp-btn" onClick={() => setRange('90')}>
                <Calendar size={14} strokeWidth={2.1} /> Widen to 90 days
              </button>
            </div>
          </div>
        </div>
      </>
    );
  }

  const days = data.days || [];
  const agentBars = data.agentBars || [];
  const reasons = data.reasons || [];
  const failures = reasons.reduce((a, r) => a + (r.count || 0), 0);

  /* Each KPI's story is one number, so these are stat tiles. An eight-hue
     chart for a single figure is the commonest way a chart misses its point. */
  const kpis: [string, string, string][] = [
    ['Delivered', String(data.delivered ?? 0), ''],
    ['On time', String(data.onTimeRate ?? 0), '%'],
    ['Failed', String(data.failed ?? 0), ''],
    ['First attempt', String(data.firstAttemptRate ?? 0), '%'],
    ['Avg delivery', String(data.avgDeliveryHours ?? 0), 'h']
  ];

  return (
    <>
      {toolbar}
      <div className="rp-wrap">
        <dl className="rp-kpis">
          {kpis.map(([label, value, unit]) => (
            <div className="rp-kpi" key={label}>
              <dt>{label}</dt>
              <dd>{value}{unit && <small>{unit}</small>}</dd>
            </div>
          ))}
        </dl>

        <div className="rp-card">
          <div className="rp-card-h">
            <h2>Daily outcome</h2>
            {days.length > 0 && days.length < Number(range) && (
              <span className="note">{days.length} of {range} days have data</span>
            )}
            <button type="button" className="rp-btn" onClick={() => navigate('/owner/reports/trend')}>
              Full trend <ArrowRight size={13} strokeWidth={2.3} />
            </button>
          </div>
          <div className="rp-multi">
            <DayBars days={days} field="delivered" colour="var(--rp-delivered)" label="Delivered" height={96} />
            {/* Deliberately shorter. The two plots are scaled to their own
                maxima, so equal heights would imply equal quantities. */}
            <DayBars days={days} field="failed" colour="var(--rp-failed)" label="Failed" height={58} />
            {days.length > 0 && (
              <div className="rp-axis">
                <span>{days[0]!.date}</span><span>{days[days.length - 1]!.date}</span>
              </div>
            )}
          </div>
        </div>

        <div className="rp-grid2">
          <div className="rp-card">
            <div className="rp-card-h">
              <h2>Delivered by rider</h2>
              <span className="note">Last {range} days</span>
            </div>
            <div className="rp-brk">
              {agentBars.length === 0
                ? <p className="rp-foot rp-foot-tight">No deliveries in this range.</p>
                : agentBars.map((a) => (
                  <div className="rp-brow" key={a.name}>
                    <span className="fill" style={{ width: `${a.pct || 0}%` }} />
                    <span className="lbl">{a.name}</span>
                    <span><span className="n">{a.delivered}</span><span className="pct">{a.pct}%</span></span>
                  </div>
                ))}
            </div>
          </div>

          <div className="rp-card">
            <div className="rp-card-h">
              <h2>Why deliveries failed</h2>
              <span className="note">{failures} {failures === 1 ? 'failure' : 'failures'}</span>
            </div>
            <div className="rp-brk">
              {reasons.length === 0
                ? <p className="rp-foot rp-foot-tight">No failures in this range.</p>
                : reasons.map((r) => (
                  <div className="rp-brow" key={r.label}>
                    <span className="fill" style={{ width: `${r.pct || 0}%` }} />
                    <span className="lbl">{r.label}</span>
                    <span><span className="n">{r.count}</span><span className="pct">{r.pct}%</span></span>
                  </div>
                ))}
            </div>
          </div>
        </div>

      </div>
    </>
  );
}
