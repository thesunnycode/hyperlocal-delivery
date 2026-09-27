import { useCallback, useEffect, useState } from 'react';
import { useParams } from '@tanstack/react-router';
import { Check, ChevronDown, ChevronUp, Clock, Loader2, MapPin, MessageSquare, PackageX, Phone, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Brand } from '@/components/DeliveryApp';
import { trackShipment } from '@/lib/hl/shipmentsApi';
import { FLOW, STATUS_META, isTerminal } from '@/lib/hl/statusMachine';
import { formatDateTime, telHref } from '@/lib/hl/format';
import { ShareTracking, StatusArt, arrivalText, timeAgo } from '@/components/ux';
import type { PublicTracking, ShipmentStatus } from '@/lib/hl/types';

import type { CSSProperties } from 'react';

// Brand accent per shipment state — teal while moving, green when delivered, coral for problems.
const ACCENT: Record<ShipmentStatus, string> = {
  assigned: 'var(--primary)',
  picked_up: 'var(--primary)',
  in_transit: 'var(--primary)',
  out_for_delivery: 'var(--primary)',
  delivered: '#2E9E6B',
  failed: 'var(--coral)',
  returned: 'var(--coral)',
  cancelled: 'var(--coral)',
};

const HEADLINE: Record<ShipmentStatus, { title: string; body: string }> = {
  assigned: { title: 'Order confirmed', body: 'A rider has been assigned and will pick up your parcel soon.' },
  picked_up: { title: 'Picked up', body: 'Your parcel has left the shop.' },
  in_transit: { title: 'On the way', body: 'Your parcel is moving towards you.' },
  out_for_delivery: { title: 'Arriving soon', body: 'Your rider is heading to your address. Please keep your phone nearby.' },
  delivered: { title: 'Delivered', body: 'Your parcel has been delivered. Thank you!' },
  failed: { title: 'We missed you', body: 'We couldn’t complete the delivery. The shop will arrange another attempt.' },
  returned: { title: 'Returned to shop', body: 'Your parcel went back to the shop. Please contact them for next steps.' },
  cancelled: { title: 'Delivery cancelled', body: 'This delivery was cancelled by the shop.' },
};

export function LiveTracking() {
  const { token } = useParams({ from: '/track/$token' });
  const [data, setData] = useState<PublicTracking | null>(null);
  const [state, setState] = useState<'loading' | 'ok' | 'missing' | 'error'>('loading');
  const [refreshing, setRefreshing] = useState(false);
  const [updated, setUpdated] = useState<Date | null>(null);

  const load = useCallback(async () => {
    setRefreshing(true);
    try { setData(await trackShipment(token)); setState('ok'); setUpdated(new Date()); }
    catch (e) { const st = (e as { status?: number })?.status; setState(st === 404 || st === 400 ? 'missing' : (s => s === 'ok' ? 'ok' : 'error')); }
    finally { setRefreshing(false); }
  }, [token]);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!data || isTerminal(data.status)) return;
    const t = setInterval(() => { void load(); }, 30_000);
    return () => clearInterval(t);
  }, [data, load]);

  return <div className="track-page"><header className="track-top"><Brand/></header><main className="track-main">
    {state === 'loading' && <div className="live-loading"><Loader2 className="spin"/> Finding your delivery…</div>}
    {state === 'missing' && <section className="track-card track-empty"><PackageX/><h1>Link not found</h1><p>This tracking link is invalid or has expired. Please check the message from the shop.</p></section>}
    {state === 'error' && <section className="track-card track-empty"><PackageX/><h1>Can’t load right now</h1><p>Check your connection and try again.</p><Button variant="coral" onClick={() => void load()}>Try again</Button></section>}
    {state === 'ok' && data && <TrackView d={data} refreshing={refreshing} updated={updated} reload={() => void load()}/>}
  </main></div>;
}

function TrackView({ d, refreshing, updated, reload }: { d: PublicTracking; refreshing: boolean; updated: Date | null; reload: () => void }) {
  const h = HEADLINE[d.status];
  const step = STATUS_META[d.status]?.step ?? 1;
  const branched = d.status === 'failed' || d.status === 'returned' || d.status === 'cancelled';
  const tone = d.status === 'delivered' ? 'ok' : branched ? 'warn' : '';
  const [showAll, setShowAll] = useState(false);
  const events = [...d.events].reverse();
  const visible = showAll ? events : events.slice(0, 3);
  const phone = d.businessPhone?.replace(/[^\d+]/g, '') ?? '';
  const active = !isTerminal(d.status) && !branched;
  return <>
    <section className={`track-card track-hero ${tone} ${active ? 'dark' : ''}`} style={{ '--accent': ACCENT[d.status] } as CSSProperties}>
      <p className="eyebrow">{d.businessName ? `FROM ${d.businessName.toUpperCase()}` : 'YOUR DELIVERY'}</p>
      <StatusArt status={d.status}/>
      <h1>{h.title}</h1><p>{h.body}</p>
      {!branched && <ol className="journey-strip" aria-label="Delivery progress">{FLOW.map(f => <li key={f} className={STATUS_META[f].step <= step ? 'done' : ''} aria-current={f === d.status ? 'step' : undefined}><span/><small>{STATUS_META[f].label}</small></li>)}</ol>}
      <div className="track-meta">{d.status === 'delivered' && d.deliveredAt ? <span><Check size={15}/> Delivered {formatDateTime(d.deliveredAt)}</span> : d.scheduledAt && !isTerminal(d.status) && !branched ? <span><Clock size={15}/> {arrivalText(d.status, d.scheduledAt) ?? 'Expected'} · {formatDateTime(d.scheduledAt)}</span> : null}</div>
    </section>
    <section className="track-card"><div className="contact-card flat"><MapPin/><div><small>DELIVERING TO</small><strong>{d.customerName}</strong><span className="muted">{d.address}</span></div></div>
      <ShareTracking url={typeof window !== 'undefined' ? window.location.href : ''} business={d.businessName}/>
      {d.businessPhone && <div className="track-contact">
        <a className="track-call" href={telHref(d.businessPhone)}><Phone size={15}/><span>Call {d.businessName ?? 'the shop'}</span></a>
        <a className="track-call alt" href={`https://wa.me/${phone.replace('+', '')}?text=${encodeURIComponent(`Hi! I'm asking about my delivery${d.businessName ? ` from ${d.businessName}` : ''}.`)}`} target="_blank" rel="noreferrer"><MessageSquare size={15}/><span>WhatsApp {d.businessName ?? 'the shop'}</span></a>
      </div>}</section>
    <section className="track-card"><h2 className="mini-heading">Updates</h2><ol className="live-timeline">{visible.map((e, i) => {
      const bad = e.status === 'failed' || e.status === 'cancelled' || e.status === 'returned';
      return <li key={i} className={`${i === 0 ? 'tl-latest' : ''} ${bad ? 'tl-bad' : ''}`}><span className={`tl-dot tl-${e.status}`}/><div><strong>{e.label ?? STATUS_META[e.status]?.label}</strong><small>{e.stamp ? `${timeAgo(e.stamp)} · ${formatDateTime(e.stamp)}` : ''}</small></div></li>;
    })}</ol>
      {events.length > 3 && <button type="button" className="tl-more" onClick={() => setShowAll(v => !v)}>
        {showAll ? <>Show less <ChevronUp size={15}/></> : <>Show all {events.length} updates <ChevronDown size={15}/></>}
      </button>}</section>
    <footer className="track-foot"><span>{updated ? `Updated ${updated.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}` : ''}{!isTerminal(d.status) && ' · refreshes automatically'}</span><Button variant="outline" size="sm" onClick={reload} disabled={refreshing}><RefreshCw className={refreshing ? 'spin' : ''}/> Refresh</Button></footer>
  </>;
}
