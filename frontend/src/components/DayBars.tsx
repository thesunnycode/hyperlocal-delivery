import { useState } from 'react';
import type { DayPoint } from '../types/api';

/**
 * One series of daily counts. Deliberately ONE series per instance.
 *
 * The obvious design is a single stacked chart with delivered green and failed
 * red. The dataviz validator refuses it: those two hues sit at ΔE 4.2 under
 * deuteranopia, so as adjacent fills they are the same colour to roughly one
 * reader in twelve, and `--pairs all` returns the same number whatever the
 * series order. 4.2 is below the 6 floor at which direct labels and gaps can
 * license a close pair, so no amount of labelling rescues it.
 *
 * Two single-series plots solve it without touching the product's status
 * colours: one series needs no legend, has no adjacent pair, and invites no
 * cross-series colour comparison.
 *
 * Each plot is scaled to its OWN maximum — 60 deliveries and 4 failures cannot
 * share a y-axis without the smaller one disappearing. That makes heights
 * non-comparable BETWEEN plots, so the peak is printed on each and the caller
 * gives the failure plot a shorter height, to stop it reading as an equal
 * quantity.
 */
export default function DayBars({
  days, field, colour, label, height = 96
}: {
  days: DayPoint[];
  field: 'delivered' | 'failed' | 'progress' | 'total';
  colour: string;
  label: string;
  height?: number;
}) {
  const [hover, setHover] = useState<number | null>(null);

  const W = 880;
  const H = height;
  const PAD = 4;
  const AXIS = 18;
  // 32 leaves room for the axis band AND the peak label. Scaling to (H - AXIS)
  // put the label at a negative y for the tallest bar, clipping it out of the
  // viewBox entirely.
  const PLOT = H - 32;

  const vals = days.map((d) => d[field]);
  const max = Math.max(...vals, 1);
  const total = vals.reduce((a, b) => a + b, 0);
  const bw = days.length ? (W - PAD * 2) / days.length : 0;
  /* A sparse range — a new business with one day of data in a 90-day window —
     gave that single bar the full 872px of plot, which reads as a filled
     rectangle rather than a bar. Cap the drawn width and centre it in its
     slot; the slot spacing still carries the time axis. */
  const drawn = Math.max(1, Math.min(bw - 2, 56));
  const inset = (bw - drawn) / 2;
  const peak = vals.indexOf(max);

  return (
    <>
      {/* The scale is stated on the plot itself — "0-24" beside the series
          name — so a reader can see what a bar's height is worth without a
          paragraph underneath telling them the plots are scaled
          independently. That paragraph is gone. */}
      <div className="rp-series" style={{ ['--c' as string]: colour }}>
        <i />{label}
        <span>· total {total}</span>
        <span className="rp-scale">scale 0&ndash;{max}</span>
      </div>

      <div
        className="rp-plot"
        /* The tooltip was mouse-only: the value for a given day existed on
           screen but was unreachable by keyboard, and the SVG's aria-label
           carries the total and the peak but not the series. Arrow keys walk
           the bars and the hidden table below is the screen-reader path. */
        tabIndex={0}
        role="application"
        aria-label={`${label} per day. Use the arrow keys to step through days.`}
        onFocus={() => setHover((h) => h ?? 0)}
        onBlur={() => setHover(null)}
        onKeyDown={(e) => {
          if (!days.length) return;
          const step = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
          if (step) {
            e.preventDefault();
            setHover((h) => Math.min(days.length - 1, Math.max(0, (h ?? 0) + step)));
          } else if (e.key === 'Home') {
            e.preventDefault(); setHover(0);
          } else if (e.key === 'End') {
            e.preventDefault(); setHover(days.length - 1);
          } else if (e.key === 'Escape') {
            setHover(null);
          }
        }}
        onMouseLeave={() => setHover(null)}
        onMouseMove={(e) => {
          const r = e.currentTarget.getBoundingClientRect();
          if (!days.length) return;
          const i = Math.min(days.length - 1,
            Math.max(0, Math.floor(((e.clientX - r.left) / r.width) * days.length)));
          setHover(i);
        }}
      >
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img"
          aria-label={`${label} per day. Total ${total}, peak ${max} in one day.`}>
          {/* Solid hairline, never dashed — dashing reads as a threshold. */}
          <line x1="0" y1={H - AXIS} x2={W} y2={H - AXIS} stroke="var(--rp-grid)" strokeWidth="1" />
          {days.map((d, i) => {
            /* A zero day draws NOTHING. The 2px floor was meant to keep a
               small non-zero value visible, but it also gave every empty day
               a 2px stub — so a 90-day range holding 30 days of data drew
               sixty stubs in a row that read as a dashed line, i.e. as data.
               Empty is empty; the gap is the information. */
            if (!d[field]) return null;
            const h = Math.max(2, (d[field] / max) * PLOT);
            return (
              <rect
                key={d.date}
                /* Centred in its slot, leaving at least a 2px surface gap
                   between bars. A border around each mark would be the
                   anti-pattern. */
                x={PAD + i * bw + inset}
                y={H - AXIS - h}
                width={drawn}
                height={h}
                rx="3"
                fill={colour}
                opacity={hover === null || hover === i ? 1 : 0.55}
              />
            );
          })}
          {/* Selective label: the peak only. A number on every bar is chaos. */}
          {days.length > 0 && (
            <text
              x={PAD + peak * bw + bw / 2}
              y={H - AXIS - (max / max) * PLOT - 7}
              textAnchor="middle"
              fontFamily="var(--rp-ui)" fontSize="10.5" fontWeight="600" fill="var(--rp-ink)"
            >
              {max}
            </text>
          )}
        </svg>

        {hover !== null && days[hover] && (
          <div className="rp-tip" style={{ left: `${((hover + 0.5) / days.length) * 100}%`, top: `${H - AXIS}px` }}>
            <b>{days[hover]![field]} {label.toLowerCase()}</b>
            <em>{days[hover]!.date} · {days[hover]!.total} total</em>
          </div>
        )}
      </div>

      {/* Announces the focused day to a screen reader as the arrow keys move,
          so the keyboard path is not silent. */}
      <span className="sr-only" aria-live="polite">
        {hover !== null && days[hover]
          ? `${days[hover]!.date}: ${days[hover]![field]} ${label.toLowerCase()}, ${days[hover]!.total} total`
          : ''}
      </span>

      {/* The chart's own aria-label gives the total and the peak. This is the
          rest of it: every plotted value, in source order, for anyone who
          cannot read a bar. Visually hidden, not display:none — it has to stay
          in the accessibility tree. */}
      {/* The wrapper carries .sr-only, not the table: a bare `.sr-only` on a
          <table> loses to the scope's own `.rp table { width: 100% }`, which
          is one specificity point higher, and the "hidden" table then laid
          out at full width and scrolled the page sideways. */}
      <div className="sr-only">
        <table>
          <caption>{label} per day</caption>
          <thead>
            <tr><th scope="col">Date</th><th scope="col">{label}</th></tr>
          </thead>
          <tbody>
            {days.map((d) => (
              <tr key={d.date}><th scope="row">{d.date}</th><td>{d[field]}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
