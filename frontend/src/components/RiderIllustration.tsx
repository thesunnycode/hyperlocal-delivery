import { useId } from 'react';
import type { ShipmentStatus } from '../types/api.ts';

/**
 * The delivery rider. Inline SVG rather than an asset so it ships with the
 * bundle and costs no extra request.
 *
 * Palette, keyframes and the reduced-motion rules live on the `.ri` scope in
 * styles/rider.css, so this draws correctly on any surface — it reads none of
 * its host's tokens.
 *
 * ── What the redesign changed here ────────────────────────────────────────
 * The drawing was structurally sound and flat: every shape was a single fill,
 * so the scooter read as a decal rather than an object, and the rider had one
 * arm and one leg, which made the pose ambiguous at the mobile crop.
 *
 *   · Two-layer shading. Every major mass now has a shadow shape at the same
 *     hue and a highlight where the light lands, so forms read as volume. The
 *     light is up-and-left throughout; nothing is lit from two directions.
 *   · A far-side arm and leg, drawn first and darkened, so the rider sits ON
 *     the scooter instead of beside it.
 *   · The scooter is a scooter. It gained the legshield, floorboard and
 *     step-through gap that distinguish one from a motorcycle — the previous
 *     silhouette was a frame with a seat.
 *   · Wheels gained a hub, a rim lip and a mudguard. The mudguard is outside
 *     the spinning group, which is what stops the spokes reading as a
 *     rotating disc.
 *   · A soft radial ground shadow instead of a flat ellipse, so the scooter
 *     touches the ground rather than floating over a grey pill.
 *   · Headlight, mirror, exhaust and a rear reflector — the small parts that
 *     make a vehicle legible at 120px wide.
 *
 * `crop` picks the viewBox: the mobile hero zooms the rider instead of
 * letterboxing the whole scene into a short banner.
 *
 * `still` draws the same scene without motion, for a delivery that is already
 * finished or has failed.
 *
 * `status` tints the three floating parcels to the shipment's real state. The
 * tracking page knows the status and previously threw it away here, so a
 * delivered order still had an orange out-for-delivery parcel drifting beside
 * the word "Delivered". Omit it and the parcels show the neutral in-flight
 * trio, which is right for the auth panel where there is no shipment.
 */
export default function RiderIllustration({
  crop = 'full',
  still = false,
  status,
  className
}: {
  crop?: 'full' | 'tight';
  still?: boolean;
  status?: ShipmentStatus;
  className?: string;
}) {
  // Rendered twice per page (desktop panel + mobile hero). Hard-coded ids
  // would collide and the second instance would resolve against the first.
  const uid = useId();
  const domeId = `${uid}-dome`;
  const groundId = `${uid}-ground`;

  // One parcel per state the scene should be talking about. `--ri-st-*` are
  // the app's own status hues, so the vocabulary is consistent from the
  // tracking page's badge to the drawing beside it.
  const parcels =
    status === 'delivered'
      ? ['var(--ri-st-delivered)', 'var(--ri-st-delivered)', 'var(--ri-st-picked)']
      : status === 'failed' || status === 'returned'
        ? ['var(--ri-st-failed)', 'var(--ri-st-picked)', 'var(--ri-st-failed)']
        : status === 'assigned' || status === 'picked_up'
          ? ['var(--ri-st-picked)', 'var(--ri-st-picked)', 'var(--ri-st-ofd)']
          : ['var(--ri-st-ofd)', 'var(--ri-st-delivered)', 'var(--ri-st-picked)'];

  return (
    <svg
      viewBox={crop === 'tight' ? '70 60 420 320' : '0 0 540 400'}
      role="img"
      aria-label="A delivery rider on a scooter carrying parcels"
      className={['ri', still && 'still', className].filter(Boolean).join(' ')}
    >
      <defs>
        <clipPath id={domeId}>
          <rect x="238" y="66" width="96" height="62" />
        </clipPath>
        {/* Flat stops, not var(). A CSS custom property inside a gradient stop
            is legal but unreliable across renderers — it fell back to black in
            testing, which put a hard triangle where the headlight beam is. The
            surface-dependent parts are handled with tokens on the SHAPES
            instead, which always resolve. */}
        <radialGradient id={groundId} cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#000" stopOpacity="0.5" />
          <stop offset="60%" stopColor="#000" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#000" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* ── speed streaks ─────────────────────────────────────────────── */}
      <g stroke="var(--ri-streak)" strokeWidth="6" strokeLinecap="round" opacity="var(--ri-streak-o)">
        <g className="ri-streaks">
          <line x1="18" y1="150" x2="92" y2="150" />
          <line x1="-24" y1="198" x2="44" y2="198" />
          <line x1="26" y1="246" x2="76" y2="246" />
          <line x1="64" y1="150" x2="138" y2="150" />
          <line x1="22" y1="198" x2="90" y2="198" />
          <line x1="72" y1="246" x2="122" y2="246" />
        </g>
      </g>

      {/* ── floating parcels ─────────────────────────────────────────────
          A lid band and a tape line, so at 120px wide they still read as
          boxes rather than as coloured squares. */}
      <g className="ri-parcel" style={{ ['--ri-rot' as string]: '-14deg', animationDelay: '0s' }}>
        <rect x="60" y="84" width="30" height="30" rx="5" fill={parcels[0]} />
        <rect x="60" y="84" width="30" height="10" rx="5" fill="#000" opacity=".2" />
        <rect x="73" y="84" width="4" height="30" fill="#000" opacity=".14" />
      </g>
      <g className="ri-parcel" style={{ ['--ri-rot' as string]: '12deg', animationDelay: '-1.1s' }}>
        <rect x="446" y="66" width="24" height="24" rx="4" fill={parcels[1]} />
        <rect x="446" y="66" width="24" height="8" rx="4" fill="#000" opacity=".2" />
      </g>
      <g className="ri-parcel" style={{ ['--ri-rot' as string]: '-8deg', animationDelay: '-2.2s' }}>
        <rect x="482" y="188" width="18" height="18" rx="3" fill={parcels[2]} />
        <rect x="482" y="188" width="18" height="6" rx="3" fill="#000" opacity=".2" />
      </g>

      {/* ── ground contact ────────────────────────────────────────────── */}
      <ellipse className="ri-shadow" cx="282" cy="352" rx="188" ry="20" fill={`url(#${groundId})`} />

      {/* No headlight beam. A flat gradient wedge never reads as light: on
          the dark panel it was an opaque tan triangle stuck to the front of
          the bike, and on a light page it had to be hidden altogether, which
          meant the drawing was inconsistent between its two homes. The lamp
          lens alone carries the idea, and it works on both. */}

      {/* ══ FAR SIDE ══════════════════════════════════════════════════════
          Drawn first and knocked back, so the near side reads as nearer. */}
      <g className="ri-bob" opacity=".5">
        <path d="M252 216 L300 244" stroke="var(--ri-trouser-dk)" strokeWidth="25"
          strokeLinecap="round" fill="none" />
        <path d="M300 244 L306 268" stroke="var(--ri-trouser-dk)" strokeWidth="22"
          strokeLinecap="round" fill="none" />
        <path d="M288 168 L364 166" stroke="var(--ri-jacket-dk)" strokeWidth="18"
          strokeLinecap="round" fill="none" />
      </g>

      {/* ══ WHEELS — planted, never bob ══════════════════════════════════
          Drawn BEFORE the body. In a side view a scooter's deck and legshield
          sit in front of its wheels, and with the wheels painted last they
          erased both — which is why the previous pass rendered as two wheels,
          a seat and nothing joining them.

          Mudguards sit OUTSIDE the spinning group. Inside it they rotate with
          the spokes and the whole wheel reads as a spinning disc. */}
      <path d="M134 264 Q180 234 226 264 L220 276 Q180 250 140 276 Z" fill="var(--ri-scoot-dk)" />
      <g>
        <circle cx="180" cy="300" r="44" fill="var(--ri-tyre)" />
        <circle cx="180" cy="300" r="36" fill="var(--ri-tread)" />
        <circle cx="180" cy="300" r="26" fill="var(--ri-rim)" />
        <g className="ri-spoke ri-spoke-rear">
          <rect x="176" y="275" width="8" height="50" rx="4" fill="var(--ri-tyre)" opacity=".4" />
          <rect x="155" y="296" width="50" height="8" rx="4" fill="var(--ri-tyre)" opacity=".4" />
          <rect x="176" y="275" width="8" height="50" rx="4" fill="var(--ri-tyre)" opacity=".4"
            transform="rotate(45 180 300)" />
          <rect x="155" y="296" width="50" height="8" rx="4" fill="var(--ri-tyre)" opacity=".4"
            transform="rotate(45 180 300)" />
        </g>
        <circle cx="180" cy="300" r="10" fill="var(--ri-tyre)" />
        <circle cx="180" cy="300" r="4" fill="var(--ri-rim)" />
      </g>

      <path d="M336 262 Q382 232 428 262 L422 274 Q382 248 342 274 Z" fill="var(--ri-scoot-dk)" />
      <g>
        <circle cx="382" cy="300" r="44" fill="var(--ri-tyre)" />
        <circle cx="382" cy="300" r="36" fill="var(--ri-tread)" />
        <circle cx="382" cy="300" r="26" fill="var(--ri-rim)" />
        <g className="ri-spoke ri-spoke-front">
          <rect x="378" y="275" width="8" height="50" rx="4" fill="var(--ri-tyre)" opacity=".4" />
          <rect x="357" y="296" width="50" height="8" rx="4" fill="var(--ri-tyre)" opacity=".4" />
          <rect x="378" y="275" width="8" height="50" rx="4" fill="var(--ri-tyre)" opacity=".4"
            transform="rotate(45 382 300)" />
          <rect x="357" y="296" width="50" height="8" rx="4" fill="var(--ri-tyre)" opacity=".4"
            transform="rotate(45 382 300)" />
        </g>
        <circle cx="382" cy="300" r="10" fill="var(--ri-tyre)" />
        <circle cx="382" cy="300" r="4" fill="var(--ri-rim)" />
      </g>

      {/* front fork, over the wheel */}
      <path d="M386 208 L380 272" stroke="var(--ri-rim)" strokeWidth="9"
        strokeLinecap="round" fill="none" />

      {/* ══ SCOOTER ══════════════════════════════════════════════════════
          Side view, right-facing. Built as four masses in the order a scooter
          actually stacks: deck, rear body, legshield column, then the parts
          bolted to them. The previous version drew a frame and a seat, which
          is a motorcycle; the step-through gap above the deck is the single
          shape that makes this read as a scooter. */}
      <g className="ri-bob">
        {/* rear rack + cargo box */}
        <rect x="146" y="216" width="72" height="10" rx="5" fill="var(--ri-tyre)" />
        <rect x="102" y="140" width="86" height="80" rx="9" fill="var(--ri-box)"
          stroke="var(--ri-box-line)" strokeWidth="2" />
        <rect x="102" y="140" width="86" height="16" rx="8" fill="var(--ri-helmet-dk)" />
        <path d="M172 140 L188 140 L188 220 L172 220 Z" fill="#000" opacity=".07" />
        <rect x="122" y="168" width="32" height="32" rx="7" fill="var(--ri-brand)" />
        <path d="M130 184 l6 6 l12 -13" stroke="#fff" strokeWidth="3.6"
          strokeLinecap="round" strokeLinejoin="round" fill="none" />
        <rect x="146" y="222" width="18" height="7" rx="3" fill="var(--ri-st-failed)" />

        {/* ── the body, as ONE silhouette ──────────────────────────────
            Three overlapping masses (deck, rear body, legshield) was the
            mistake: each was individually correct and together they read as
            unrelated grey slabs, because a viewer resolves a vehicle by its
            outline before any of its parts. This is a single closed path
            tracing the whole side profile — rear haunch over the back wheel,
            down to the deck, up the legshield to the bar — with the
            step-through notch cut into its top edge, which is the one feature
            that separates a scooter from a motorcycle. */}
        <path
          d="M226 218
             Q252 208 288 212
             Q308 216 314 240
             L318 262
             L360 262
             Q372 224 386 196
             Q398 170 418 158
             L432 152
             Q410 180 400 214
             Q390 250 390 286
             L360 286
             L262 286
             Q244 286 238 272
             L230 246
             Z"
          fill="var(--ri-scoot)"
        />
        {/* light from up-left: the top of the haunch and the front of the
            legshield catch it, the underside and the inner column do not */}
        <path d="M226 218 Q252 208 288 212 Q304 215 310 230 L246 232 Q232 230 226 224 Z"
          fill="var(--ri-scoot-hi)" opacity=".42" />
        <path d="M386 196 Q398 170 418 158 L428 154 Q408 178 398 208 Z"
          fill="var(--ri-scoot-hi)" opacity=".42" />
        <path d="M262 286 L360 286 L360 276 L262 276 Z" fill="#000" opacity=".16" />
        <path d="M360 262 Q368 236 378 214 L384 216 Q372 240 366 264 Z" fill="#000" opacity=".1" />

        {/* deck lip — the flat the rider's boot actually rests on */}
        <rect x="266" y="262" width="94" height="7" rx="3.5" fill="var(--ri-scoot-dk)" />
        <rect x="268" y="263" width="90" height="3" rx="1.5" fill="var(--ri-scoot-hi)" opacity=".45" />

        {/* seat, sitting on the haunch */}
        <path d="M214 216 Q232 200 270 202 L308 206 Q316 214 308 224 L226 226 Q210 222 214 216 Z"
          fill="var(--ri-seat)" />
        <path d="M216 214 Q234 204 270 205 L304 209 Q306 211 304 213 L222 218 Q212 218 216 214 Z"
          fill="var(--ri-seat-hi)" opacity=".5" />

        {/* headlight housing + lens */}
        <path d="M394 166 L432 154 L444 188 L404 198 Z" fill="var(--ri-helmet)" />
        <path d="M394 166 L432 154 L436 165 L398 176 Z" fill="var(--ri-helmet-dk)" opacity=".6" />
        <ellipse cx="425" cy="176" rx="11" ry="14" fill="var(--ri-lamp)"
          transform="rotate(-16 425 176)" />
        <ellipse cx="421" cy="170" rx="4" ry="5" fill="#fff" opacity=".8"
          transform="rotate(-16 421 170)" />

        {/* handlebar, grips, mirror */}
        <rect x="344" y="140" width="82" height="13" rx="6.5" fill="var(--ri-tyre)" />
        <rect x="340" y="136" width="22" height="22" rx="7" fill="var(--ri-grip)" />
        <path d="M420 142 L428 122" stroke="var(--ri-tyre)" strokeWidth="4.5"
          strokeLinecap="round" fill="none" />
        <ellipse cx="430" cy="118" rx="8.5" ry="6" fill="var(--ri-mirror)"
          stroke="var(--ri-tyre)" strokeWidth="2.5" transform="rotate(-20 430 118)" />

        {/* exhaust */}
        <rect x="214" y="300" width="52" height="12" rx="6" fill="var(--ri-scoot-dk)" />
        <rect x="206" y="302" width="12" height="8" rx="4" fill="var(--ri-rim)" />
      </g>

      {/* ══ RIDER — near side ═════════════════════════════════════════════
          Seated at x≈252 on the seat, feet forward on the deck, hands on the
          grip at (352,146). Every joint below is on that line, which is what
          the previous pose was missing — the arm reached past the bar and the
          knee bent the wrong way. */}
      <g className="ri-bob">
        {/* thigh, then shin down to the deck */}
        <path d="M248 212 L302 236" stroke="var(--ri-trouser)" strokeWidth="28"
          strokeLinecap="round" fill="none" />
        <path d="M302 236 L312 266" stroke="var(--ri-trouser)" strokeWidth="24"
          strokeLinecap="round" fill="none" />
        <path d="M246 204 L298 227" stroke="var(--ri-trouser-hi)" strokeWidth="7"
          strokeLinecap="round" fill="none" opacity=".45" />
        {/* boot, flat on the deck */}
        <path d="M296 258 L338 258 Q346 258 346 265 L346 270 Q346 276 338 276 L300 276
                 Q292 276 292 268 Z" fill="var(--ri-tyre)" />
        <rect x="292" y="270" width="54" height="6" rx="3" fill="var(--ri-rim)" opacity=".55" />

        {/* torso — hip at (248,212) to shoulder at (282,158) */}
        <path d="M248 212 L282 158" stroke="var(--ri-jacket)" strokeWidth="50"
          strokeLinecap="round" fill="none" />
        {/* the hi-vis band every courier jacket has, and the single detail
            that makes this read as a delivery rider and not a commuter */}
        <path d="M254 200 L286 176" stroke="var(--ri-vest)" strokeWidth="13"
          strokeLinecap="round" fill="none" />
        <path d="M262 210 L292 188" stroke="var(--ri-jacket-hi)" strokeWidth="6"
          strokeLinecap="round" fill="none" opacity=".5" />
        <path d="M272 166 Q286 156 296 160" stroke="var(--ri-jacket-hi)" strokeWidth="9"
          strokeLinecap="round" fill="none" opacity=".45" />

        {/* upper arm, forearm, hand — shoulder (284,162) → elbow (318,170) → grip (352,148) */}
        <path d="M284 162 L318 170" stroke="var(--ri-jacket)" strokeWidth="21"
          strokeLinecap="round" fill="none" />
        <path d="M318 170 L350 150" stroke="var(--ri-jacket)" strokeWidth="18"
          strokeLinecap="round" fill="none" />
        <circle cx="352" cy="146" r="12" fill="var(--ri-skin)" />
        <path d="M346 141 Q353 137 359 142" stroke="var(--ri-skin-dk)" strokeWidth="2.8"
          strokeLinecap="round" fill="none" />

        {/* head — jaw, then the helmet clipped to a dome over it */}
        <circle cx="286" cy="122" r="27" fill="var(--ri-skin)" />
        <path d="M286 95 a27 27 0 0 1 0 54 a19 27 0 0 0 0 -54 Z" fill="var(--ri-skin-dk)" opacity=".4" />
        <circle cx="284" cy="112" r="33" fill="var(--ri-helmet)" clipPath={`url(#${domeId})`} />
        <path d="M260 92 Q280 79 304 90" stroke="var(--ri-helmet-hi)" strokeWidth="7"
          strokeLinecap="round" fill="none" opacity=".85" />
        {/* peak */}
        <path d="M250 124 L318 115 L319 124 L250 133 Z" fill="var(--ri-helmet-dk)" />
        {/* visor + shine */}
        <path d="M298 116 L324 112 Q330 118 326 129 L300 132 Z" fill="var(--ri-visor)" opacity=".65" />
        <path d="M304 118 L318 116" stroke="var(--ri-helmet-hi)" strokeWidth="3"
          strokeLinecap="round" opacity=".65" />
        {/* ear cup + chin strap */}
        <circle cx="270" cy="122" r="11" fill="var(--ri-helmet-dk)" />
        <circle cx="270" cy="122" r="4" fill="var(--ri-rim)" opacity=".5" />
        <path d="M268 134 Q274 152 292 150" stroke="var(--ri-helmet-dk)" strokeWidth="5"
          fill="none" strokeLinecap="round" />
      </g>
    </svg>
  );
}
