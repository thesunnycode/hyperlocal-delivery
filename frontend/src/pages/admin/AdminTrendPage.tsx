import { useEffect, useState } from 'react';
import { Check, LineChart, Calendar } from 'lucide-react';
import { getTrend } from '../../api/reportsApi';
import { useFatalError } from '../../lib/FatalErrorContext.tsx';
import DayBars from '../../components/DayBars.tsx';
import { formatDate } from '../../utils/format';
import type { DayPoint, TrendReport } from '../../types/api';

/**
 * AD2 — shape above, figures below, so the same data reads both ways.
 *
 * This page used to plot all four series on ONE multi-series line chart. It
 * cannot: delivered (#15803d) and failed (#b91c1c) sit ΔE 4.2 apart under
 * deuteranopia — below the 6 floor at which labels can license a close pair —
 * so on a shared plot they are one colour to roughly one reader in twelve.
 * Same measurement, same fix as Overview: one plot per series. The toggles
 * still work; they now add and remove plots instead of lines.
 */

type SeriesKey = 'total' | 'delivered' | 'failed' | 'progress';

const SERIES: [SeriesKey, string, string, number][] = [
  ['total', 'Total', 'var(--rp-neutral)', 92],
  ['delivered', 'Delivered', 'var(--rp-delivered)', 82],
  ['failed', 'Failed', 'var(--rp-failed)', 56],
  ['progress', 'In progress', 'var(--rp-brand)', 56]
];

// The same three windows Overview offers, so the two screens agree on what a
// range is.
const RANGES: [number, string][] = [[7, '7 days'], [30, '30 days'], [90, '90 days']];

/** Rows of the per-day table shown before "Show all". Three was arbitrary. */
const PREVIEW_ROWS = 10;

/**
 * Fill a sparse series out to the whole requested window.
 *
 * `/reports/trend` answers with the days that have shipments, so a quiet week
 * inside a 90-day range simply is not in the array — and a bar chart given 30
 * points for a 90-day range spreads them edge to edge and reports a busy
 * quarter. Missing days come back as explicit zeros, anchored to the newest
 * date present so the axis ends where the data does.
 */
function padRange(days: DayPoint[], range: number): DayPoint[] {
  if (days.length >= range) return days;

  const byDate = new Map(days.map((d) => [d.date, d]));
  const last = days.length
    ? new Date(`${days[days.length - 1]!.date}T00:00:00Z`)
    : new Date();
  if (Number.isNaN(last.getTime())) return days;

  const out: DayPoint[] = [];
  for (let i = range - 1; i >= 0; i -= 1) {
    const d = new Date(last);
    d.setUTCDate(d.getUTCDate() - i);
    const key = d.toISOString().slice(0, 10);
    out.push(byDate.get(key) ?? { date: key, total: 0, delivered: 0, failed: 0, progress: 0 });
  }
  return out;
}

export default function AdminTrendPage() {
  const { reportError } = useFatalError();
  const [days, setDays] = useState(90);
  const [data, setData] = useState<TrendReport | null>(null);
  const [on, setOn] = useState<Record<SeriesKey, boolean>>({
    total: true, delivered: true, failed: true, progress: false
  });
  const [showAll, setShowAll] = useState(false);

  useEffect(() => {
    setData(null);
    getTrend({ days }).then(setData).catch(reportError);
  }, [days, reportError]);

  const toolbar = (
    <>
      <div className="rp-head">
        <div>
          <h1>Trend</h1>
          <div className="sub">Shipment volume per day · last {days} days</div>
        </div>
      </div>
      <div className="rp-bar">
        <span className="rp-barlbl">Range</span>
        <div role="group" aria-label="Date range" className="rp-chipgroup">
          {RANGES.map(([v, l]) => (
            <button key={v} type="button" className="rp-chip"
              aria-pressed={days === v} onClick={() => setDays(v)}>{l}</button>
          ))}
        </div>
        <span className="rp-barlbl sp">Series</span>
        <div role="group" aria-label="Series shown" className="rp-chipgroup">
          {SERIES.map(([id, label]) => (
            <button key={id} type="button" className="rp-chip rp-chip-ic" aria-pressed={on[id]}
              onClick={() => setOn((s) => ({ ...s, [id]: !s[id] }))}>
              {on[id] ? <Check size={12} strokeWidth={3} aria-hidden="true" /> : null}
              <span>{label}</span>
            </button>
          ))}
        </div>
      </div>
    </>
  );

  if (!data) {
    return (
      <>
        {toolbar}
        <div className="rp-wrap">
          <div className="rp-card"><div className="rp-multi">
            <span className="rp-sk rp-sk-trend-title-1" />
            <span className="rp-sk rp-sk-trend-bars-1" />
            <span className="rp-sk rp-sk-trend-title-2" />
            <span className="rp-sk rp-sk-trend-bars-2" />
          </div></div>
        </div>
      </>
    );
  }

  /* The API returns only days that have shipments. Handed straight to the
     plot, 30 returned days stretched across the full width of a 90-day range
     and drew a solid quarter of activity. Padding the gaps with zeros makes
     the x-axis mean what it says; if the API already returns the whole range
     this is a no-op. */
  const points = padRange(data.days || [], days);
  const rows = data.rows || [];
  const shown = showAll ? rows : rows.slice(0, PREVIEW_ROWS);
  const withData = points.filter((p) => p.total > 0).length;
  const enabled = SERIES.filter(([id]) => on[id]);

  if (withData === 0) {
    return (
      <>
        {toolbar}
        <div className="rp-wrap">
          <div className="rp-state">
            <div className="ic"><LineChart size={24} strokeWidth={1.9} /></div>
            <h2>No days to plot</h2>
            <p>Nothing was shipped in the last {days} days, so there is no line to draw.</p>
            {days < 90 && (
              <div className="acts">
                <button type="button" className="rp-btn" onClick={() => setDays(90)}>
                  <Calendar size={14} strokeWidth={2.1} /> Widen to 90 days
                </button>
              </div>
            )}
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      {toolbar}
      <div className="rp-wrap">
        <div className="rp-card">
          <div className="rp-card-h">
            <h2>Per day</h2>
            {/* E1 — one day of data in a 90-day window drew a single bar in a
                wide empty plot, which reads as broken rather than as "new".
                Saying how much of the range actually has data turns it into a
                fact about the business instead of a doubt about the chart. */}
            <span className="note">
              {withData === points.length
                ? `${points.length} days`
                : `${withData} of ${points.length} days have shipments`}
            </span>
          </div>
          <div className="rp-multi">
            {enabled.length === 0 ? (
              <p className="rp-foot rp-foot-loose">
                Every series is switched off. Turn one back on above to draw a plot.
              </p>
            ) : (
              <>
                {enabled.map(([id, label, colour, height]) => (
                  <DayBars key={id} days={points} field={id} colour={colour}
                    label={label} height={height} />
                ))}
                <div className="rp-axis">
                  <span>{points[0]!.date}</span>
                  <span>{points[points.length - 1]!.date}</span>
                </div>
              </>
            )}
          </div>
        </div>

        <div className="rp-card">
          <div className="rp-card-h">
            <h2>Per-day figures</h2>
            <span className="note">{rows.length} {rows.length === 1 ? 'row' : 'rows'}</span>
          </div>
          <div className="rp-tablewrap">
            <table>
              <caption className="sr-only">Shipments per day for the selected range.</caption>
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col" className="r">Total</th>
                  <th scope="col" className="r">Delivered</th>
                  <th scope="col" className="r">Failed</th>
                  <th scope="col" className="r">In progress</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((r) => (
                  <tr key={r.date}>
                    <td className="name">{formatDate(r.date)}</td>
                    <td className="r">{r.total}</td>
                    <td className="r rp-muted-cell">{r.delivered}</td>
                    <td className="r" style={{ fontWeight: 600, color: r.failed > 1 ? 'var(--rp-failed)' : 'var(--rp-ink)' }}>{r.failed}</td>
                    <td className="r rp-muted-cell">{r.progress}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {rows.length > 3 && (
            <div className="rp-showall">
              <button type="button" className="rp-btn" onClick={() => setShowAll((s) => !s)}>
                {showAll ? `Show ${PREVIEW_ROWS} most recent` : `Show all ${rows.length} days`}
              </button>
            </div>
          )}
        </div>

      </div>
    </>
  );
}
