import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Search, WifiOff, RefreshCw, Phone, User, MapPin, Check } from 'lucide-react';
import { trackShipment } from '../../api/shipmentsApi';
import { ApiError } from '../../lib/apiClient';
import type { PublicTracking, ShipmentStatus } from '../../types/api';
import { STATUS_META, FLOW } from '../../utils/statusMachine';
import { telHref, formatDateTime, formatTime } from '../../utils/format';
import RiderIllustration from '../../components/RiderIllustration.tsx';

/**
 * Public tracking link, opened from an SMS. Read-only by construction.
 *
 * THE PRIVACY BOUNDARY IS THE DESIGN CONSTRAINT. The API shapes this payload
 * (see README): no agent identity, no phone, no internal ids, no failure
 * reason. This page must never fetch the full shipment and filter client-side.
 *
 * So it never says "your agent is 1.2 km away" — there is no agent identity and
 * no coordinates to know — and it never explains WHY an attempt failed. The
 * customer is pointed at the business, the only party who can act.
 *
 * Following Uber Eats' "What your delivery person sees" (Refero 49012510), that
 * boundary is now stated on the page rather than left as an invisible API
 * decision. It reassures instead of merely restricting.
 *
 * The quiet states matter as much as the busy ones: this page can sit for hours
 * between assigned and out_for_delivery. Klarna and PayPal both design for
 * "nothing has happened yet" instead of leaving a stale screen, so the
 * pre-delivery states say when the next change is due.
 *
 * ── Redesign ────────────────────────────────────────────────────────────
 * Added the FAQ block. This is the only screen a stranger sees, it has no
 * navigation, and every question it cannot answer becomes a phone call to a
 * shop owner who is out on a round. Four disclosures cost nothing above the
 * fold and remove the four calls a shop actually fields.
 */

const POLL_SECONDS = 30;
const CLOSED: ShipmentStatus[] = ['delivered', 'returned'];

/** States where something is actually moving, and the scooter may animate. */
const MOVING: ShipmentStatus[] = ['in_transit', 'out_for_delivery'];
/** States that get no scooter at all. A drawing of a rider mid-flight — even
 *  a frozen one, the speed streaks still say motion — under "We could not
 *  complete the delivery" is cheerful at exactly the wrong moment. */
const NO_SCENE: ShipmentStatus[] = ['failed', 'returned'];

/** Short label per state. `failed`/`returned` get customer-facing wording:
 *  "Failed" reads like the customer did something wrong. */
const CHIP: Record<ShipmentStatus, string> = {
  assigned: 'Assigned', picked_up: 'Picked up', in_transit: 'In transit',
  out_for_delivery: 'Out for delivery', delivered: 'Delivered',
  failed: 'Delivery attempted', returned: 'Returned to store'
};

/**
 * The questions this page can answer without a phone call.
 *
 * These four are what a shop actually fields: can I change the address, nobody
 * will be home, how do I pay, can I talk to the rider. Each answer says what
 * the reader can DO next, and the last one states the privacy boundary the
 * page enforces rather than leaving them to discover it by trying.
 *
 * `store` is threaded through rather than hardcoded, so the copy names the
 * actual business — "call Nandini Stores" is a next step, "call the store" is
 * a shrug. Nothing here needs a field the tracking payload does not already
 * carry.
 */
const FAQS: { q: string; a: (store: string) => string }[] = [
  {
    q: 'Can I change the delivery address?',
    a: (s) => `Not from this page — it is read-only by design. Call ${s} and they can update it while the delivery is still open.`
  },
  {
    q: 'Nobody will be home. What happens?',
    a: (s) => `The rider logs a failed attempt and ${s} reschedules it. One missed attempt does not cancel anything, and this page shows the new window as soon as they set it.`
  },
  {
    q: 'How do I pay?',
    a: (s) => `Whatever you agreed with ${s} when you ordered. This page never handles payment and never asks for card details — if something claiming to be this page does, it is not us.`
  },
  {
    q: 'Can I speak to the delivery rider?',
    a: (s) => `Not directly. Riders do not share personal numbers, and you will not see theirs here. Call ${s} and they will pass anything urgent straight on.`
  }
];

/** Headline + sentence, derived only from status and the two timestamps the
 *  payload carries. Nothing here needs data the endpoint does not send. */
function heroCopy(s: PublicTracking): { title: string; note: string } {
  const at = s.scheduledAt ? formatTime(s.scheduledAt) : '';
  const on = s.scheduledAt ? formatDateTime(s.scheduledAt) : '';
  const biz = s.businessName || 'the store';
  switch (s.status) {
    case 'delivered':
      return {
        title: s.deliveredAt ? `Delivered at ${formatTime(s.deliveredAt)}` : 'Delivered',
        note: 'Handed over at your address. Nothing further is needed — thank you.'
      };
    case 'returned':
      return {
        title: 'Your order went back to the store',
        note: `After the attempts allowed, the order was returned to ${biz}. Call them to arrange redelivery or a refund.`
      };
    case 'failed':
      return {
        title: 'We could not complete the delivery',
        note: `The rider attempted your address and could not hand the order over. ${biz} will try again — call them if you would like to arrange a time.`
      };
    case 'out_for_delivery':
      return {
        title: 'Out for delivery now',
        note: at
          ? `Your order is on its final leg and should reach you around ${at}. Please keep your phone nearby.`
          : 'Your order is on its final leg. Please keep your phone nearby.'
      };
    default:
      return {
        title: on ? `Arriving ${on}` : 'Arriving today',
        note: `${biz} has your order and a delivery rider is assigned. This page updates as it moves.`
      };
  }
}

/** Only the pre-final states are quiet. Naming when the next change is due
 *  beats a screen that merely looks stale. */
const QUIET: Partial<Record<ShipmentStatus, string>> = {
  assigned: 'The rider has not set out yet. This page changes the moment your order is collected from the store.',
  picked_up: 'Your order is with the rider. The next update comes when the delivery round begins.',
  in_transit: 'The next update comes when your order is out for its final leg.'
};

/** "Nandini Stores" -> "NS". noUncheckedIndexedAccess is on. */
function initials(name: string | null | undefined): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  const first = parts[0] ?? '';
  const last = parts[parts.length - 1] ?? '';
  if (!first) return '—';
  if (parts.length === 1) return first.slice(0, 2).toUpperCase();
  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();
}

export default function CustomerTrackingPage() {
  const { token } = useParams();
  const [state, setState] = useState<'loading' | 'notfound' | 'error' | 'shipment'>('loading');
  const [shipment, setShipment] = useState<PublicTracking | null>(null);
  // Set when a refresh (poll or manual) fails while a good shipment is
  // already on screen — kept separate from `state` so that failure doesn't
  // tear the page down. See `load` below.
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [isOffline, setIsOffline] = useState(!navigator.onLine);
  const [secondsAgo, setSecondsAgo] = useState(0);
  const tickTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  // Mirrors `shipment` for `load` to read without depending on it directly —
  // `load` is also the effect below's dependency, and letting its identity
  // change every time a poll succeeds would re-fire that effect and flash
  // the loading skeleton on every 30-second refresh.
  const shipmentRef = useRef<PublicTracking | null>(null);
  useEffect(() => { shipmentRef.current = shipment; }, [shipment]);

  const load = useCallback(async () => {
    try {
      // The route is /track/:token, so a match always carries one.
      const data = await trackShipment(token ?? '');
      setShipment(data);
      setState('shipment');
      setSecondsAgo(0);
      setRefreshError(null);
    } catch (err) {
      // A 4xx means the server looked the token up and said no — genuinely
      // not found (or closed long enough to be gone). Anything else (a 5xx,
      // or status 0 for a request that never reached the server) says
      // nothing about whether the delivery exists, and a shipment already on
      // screen is still correct — replacing it with "not found" during a
      // transient outage told a customer mid-delivery their order had
      // vanished. Only tear the page down if there was nothing to keep.
      const notFound = err instanceof ApiError && err.status >= 400 && err.status < 500;
      if (notFound) {
        setState('notfound');
        setShipment(null);
      } else if (shipmentRef.current) {
        setRefreshError('Could not refresh just now. Will try again shortly.');
      } else {
        setState('error');
      }
    }
  }, [token]);

  useEffect(() => {
    setState('loading');
    load();
    const onOnline = () => setIsOffline(false);
    const onOffline = () => setIsOffline(true);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, [load]);

  useEffect(() => {
    if (state !== 'shipment') return;
    const shouldPoll = !isOffline && shipment && !CLOSED.includes(shipment.status);
    if (!shouldPoll) return;
    tickTimer.current = setInterval(() => {
      setSecondsAgo((s) => {
        if (s + 1 >= POLL_SECONDS) { load(); return 0; }
        return s + 1;
      });
    }, 1000);
    return () => clearInterval(tickTimer.current ?? undefined);
  }, [state, isOffline, shipment, load]);

  // The tab is often one of many on a phone; the answer belongs in its title.
  useEffect(() => {
    if (shipment) document.title = `${heroCopy(shipment).title} · ${shipment.businessName}`;
    else if (state === 'notfound') document.title = 'Shipment not found';
    return () => { document.title = 'Hyperlocal'; };
  }, [shipment, state]);

  if (state === 'loading') {
    return (
      /* F2: aria-busy + label so AT announces "loading" instead of silence */
      <main className="tk" aria-busy="true" aria-label="Loading delivery status">
        <div className="tk-wrap">
          <div className="tk-sender">
            {/* F6: named skeleton classes replace all inline style= props */}
            <span className="tk-skel tk-skel-avatar" />
            <span className="tk-skel tk-skel-line" />
          </div>
          <div className="tk-answer">
            <span className="tk-skel tk-skel-chip" />
            <span className="tk-skel tk-skel-head" />
            <span className="tk-skel tk-skel-body" />
          </div>
          <div className="tk-rail">
            <div className="tk-track">
              {FLOW.map((f, i) => (
                /* F9: class replaces inline style={{ display: 'contents' }} */
                <span key={f} className="tk-rail-item">
                  <span className="tk-node" />
                  {i < FLOW.length - 1 && <span className="tk-seg" />}
                </span>
              ))}
            </div>
          </div>
        </div>
      </main>
    );
  }

  if (state === 'error') {
    return (
      <main className="tk"><div className="tk-wrap">
        <div className="tk-edge">
          <div className="tk-edge-icon"><RefreshCw size={24} strokeWidth={1.9} /></div>
          <h1>Couldn&rsquo;t load your delivery status</h1>
          <p>
            The server could not be reached just now. This is not the same as the delivery
            being missing — try again in a moment.
          </p>
        </div>
        <button type="button" className="tk-cta tk-solid" onClick={load}>Try again</button>
      </div></main>
    );
  }

  if (state === 'notfound' || !shipment) {
    return (
      <main className="tk"><div className="tk-wrap">
        <div className="tk-edge">
          <div className="tk-edge-icon"><Search size={24} strokeWidth={1.9} /></div>
          <h1>We can&rsquo;t find that delivery</h1>
          <p>
            The link may be mistyped, or the delivery may be old enough to have been closed.
            Check the message it came from, or contact the store you ordered from.
          </p>
        </div>
      </div></main>
    );
  }

  const hero = heroCopy(shipment);
  const quiet = QUIET[shipment.status];
  const branched = shipment.status === 'failed' || shipment.status === 'returned';
  const reached = branched ? 4 : (STATUS_META[shipment.status]?.step ?? 1);
  const lastEvent = (shipment.events ?? [])[0];
  const cls = `tk-s-${shipment.status}`;

  return (
    <main className={`tk ${cls}`}><div className="tk-wrap">
      <div className="tk-sender">
        <div className="tk-mark">{initials(shipment.businessName)}</div>
        <div>
          <div className="tk-kick">Your delivery from</div>
          <div className="tk-who">{shipment.businessName || 'Your order'}</div>
        </div>
      </div>

      <div className="tk-answer">
        <span className="tk-chip">{CHIP[shipment.status]}</span>
        <h1>{hero.title}</h1>
        <p>{hero.note}</p>
      </div>

      {/* F3: render quiet copy whenever the status has quiet text, not only
          when a last-event timestamp exists. A freshly assigned shipment
          (events:[]) still needs the "hasn't set out yet" reassurance. */}
      {quiet && (
        <div className="tk-quiet">
          <i />
          <p>
            {lastEvent?.stamp && <b>Nothing new since {formatDateTime(lastEvent.stamp)}. </b>}
            {quiet}
          </p>
        </div>
      )}

      {isOffline && (
        <div className="tk-offline" role="status">
          <WifiOff size={16} strokeWidth={2} />
          <span>You are offline. This page will refresh itself once you reconnect.</span>
        </div>
      )}

      {/* A refresh (poll or manual) failed while a good shipment was already
          on screen — see `load`. Says so without touching anything above:
          the shipment shown is still the last one actually confirmed. */}
      {refreshError && !isOffline && (
        <div className="tk-offline" role="status">
          <RefreshCw size={16} strokeWidth={2} />
          <span>{refreshError}</span>
        </div>
      )}

      <div className="tk-updated">
        <span>
          {CLOSED.includes(shipment.status)
            ? 'This delivery is closed'
            : secondsAgo < 5 ? 'Updated just now' : `Updated ${secondsAgo}s ago`}
        </span>
        {!CLOSED.includes(shipment.status) && (
          /* F7: sentence case — was "refresh now" */
          <button type="button" className="tk-refresh" onClick={load}>Refresh now</button>
        )}
      </div>

      {/* F1: role="progressbar" so AT exposes the step position semantically.
          aria-label alone on a generic div is ignored by all AT. */}
      <div className="tk-rail">
        <div className="tk-track"
          role="progressbar"
          aria-valuenow={reached}
          aria-valuemin={1}
          aria-valuemax={FLOW.length}
          aria-label={`Step ${reached} of ${FLOW.length}`}>
          {FLOW.map((f, i) => {
            const n = i + 1;
            const done = n < reached || (shipment.status === 'delivered' && n === FLOW.length);
            const now = n === reached && !done;
            return (
              /* F9: class replaces inline style={{ display: 'contents' }} */
              <span key={f} className="tk-rail-item">
                <span className={`tk-node ${done ? 'done' : now ? (branched ? 'branch' : 'now') : ''}`}>
                  {done && <Check size={11} strokeWidth={3.4} />}
                </span>
                {i < FLOW.length - 1 && <span className={`tk-seg ${n < reached ? 'done' : ''}`} />}
              </span>
            );
          })}
        </div>
        <div className="tk-lbls">
          {/* A failed or returned delivery branches off step 4, and the label
              row still read "Out for delivery" under the node the hero had
              just described as a failed attempt. The branch names itself. */}
          {FLOW.map((f, i) => (
            <span key={f} className={i + 1 === reached ? 'on' : ''}>
              {branched && i + 1 === reached
                ? STATUS_META[shipment.status].label
                : STATUS_META[f].label}
            </span>
          ))}
        </div>
      </div>

      <dl className="tk-card">
        <div className="tk-row">
          <dt>Delivering to</dt>
          <dd>{shipment.customerName}<small>{shipment.address}</small></dd>
        </div>
        <div className="tk-row">
          <dt>{shipment.status === 'delivered' ? 'Delivered' : 'Expected'}</dt>
          <dd className="tk-mono">
            {shipment.status === 'delivered'
              ? formatDateTime(shipment.deliveredAt)
              : formatDateTime(shipment.scheduledAt)}
          </dd>
        </div>
      </dl>

      {shipment.businessPhone && (
        <a className={`tk-cta ${branched ? 'tk-solid' : ''}`} href={telHref(shipment.businessPhone)}>
          <Phone size={17} strokeWidth={2.1} /> Call {shipment.businessName || 'the store'}
        </a>
      )}

      {/* Both of these were full-height blocks on a page whose one job is
          "where is my parcel". The privacy card is a reassurance you read
          once; the event list repeats the stepper above it beat for beat.
          They are still here, one tap away, instead of pushing the answer up
          the page. */}
      <details className="tk-more tk-priv">
        <summary>What the delivery rider can see</summary>
        <div className="tk-pr"><User size={16} strokeWidth={2} />{shipment.customerName}</div>
        <div className="tk-pr"><MapPin size={16} strokeWidth={2} />{shipment.address}</div>
        <div className="tk-pr"><Phone size={16} strokeWidth={2} />Your phone number</div>
        <p className="tk-note">
          They cannot see anything else about you. You will not see the rider&rsquo;s personal
          details either — if you need to reach someone, call the store.
        </p>
      </details>

      <details className="tk-more">
        <summary>Full history{(shipment.events ?? []).length
          ? ` · ${(shipment.events ?? []).length} updates` : ''}</summary>
        <ul className="tk-log">
          {(shipment.events ?? []).map((e, i) => (
            /* F8: the backend's TimelineItem has no id field (same gap as
               ShipmentEventDto, see docs/audits/2026-09-10-flow-owner-reassign.md),
               so a stamp+status composite can collide — confirmed live: two
               "assigned" events landing in the same second (auto-assign
               immediately followed by a reassign) render identically and
               trigger a real React duplicate-key warning. The index is
               included unconditionally, not just as a tiebreaker, so two
               colliding events still get distinct keys — safe here because
               this array is chronological and only ever grows by polling,
               never reordered. */
            <li key={`e-${i}-${e.stamp}-${e.status}`} className={`tk-s-${e.status}`}>
              <span className="t">{formatDateTime(e.stamp)}</span>
              <span className="m" />
              <span><b>{e.label ?? CHIP[e.status] ?? STATUS_META[e.status]?.label}</b></span>
            </li>
          ))}
        </ul>
      </details>

      {/* Deliberately after the call button — someone who wants the store on
          the phone should reach that first — and deliberately below the
          details, because "where is my parcel" outranks all of it. Reuses
          .tk-more so it inherits the disclosure styling the two blocks above
          already use; a fourth visual treatment on a page this short would be
          noise. The group is labelled, because three unlabelled disclosures in
          a row give no clue that the last ones are a different kind of thing. */}
      <div className="tk-faq">
        <div className="tk-faq-h">Common questions</div>
        {FAQS.map((f) => (
          <details className="tk-more" key={f.q}>
            <summary>{f.q}</summary>
            <p>{f.a(shipment.businessName || 'the store')}</p>
          </details>
        ))}
      </div>

      {/* Last, deliberately. Above the fold it would push the answer, the call
          button and the details down to make room for decoration; here it
          closes the page and costs the reader nothing. It animates only while
          something is genuinely in motion, and a delivery that failed gets no
          scooter at all. */}
      {!NO_SCENE.includes(shipment.status) && (
        <div className="tk-scene" aria-hidden="true">
          <RiderIllustration className="on-light" still={!MOVING.includes(shipment.status)} />
        </div>
      )}

      <div className="tk-foot">
        {/* F4: full token displayed directly — title= is inaccessible on touch */}
        <span>{shipment.trackingToken}</span>
        <span>Delivered by Hyperlocal</span>
      </div>
    </div></main>
  );
}
