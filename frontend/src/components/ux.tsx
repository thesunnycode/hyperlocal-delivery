import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { ChevronsRight, Loader2, MessageCircle, MessageSquare, Share2, WifiOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { initials } from '@/lib/hl/format';
import type { ShipmentStatus } from '@/lib/hl/types';

/* ---------- colored initials avatar ---------- */
export function Avatar({ name, size = 'md' }: { name?: string | null; size?: 'sm' | 'md' }) {
  const n = name ?? '';
  let h = 0; for (const c of n) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return <span className={`hue-avatar hue-${(h % 6) + 1} hue-${size}`} aria-hidden="true">{n ? initials(n) : '?'}</span>;
}

/* ---------- offline strip ---------- */
export function OfflineBanner() {
  const [off, setOff] = useState(false);
  useEffect(() => {
    const up = () => setOff(false), down = () => setOff(true);
    setOff(!navigator.onLine);
    window.addEventListener('online', up); window.addEventListener('offline', down);
    return () => { window.removeEventListener('online', up); window.removeEventListener('offline', down); };
  }, []);
  if (!off) return null;
  return <div className="offline-strip" role="status"><WifiOff size={15}/> You’re offline — reconnecting… Changes will load once you’re back.</div>;
}

/* ---------- soft chime ---------- */
export function playChime() {
  try {
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    const ctx = new Ctx();
    [660, 880].forEach((f, i) => {
      const o = ctx.createOscillator(), g = ctx.createGain(); const t = ctx.currentTime + i * 0.16;
      o.frequency.value = f; o.type = 'sine';
      g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.18, t + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.35);
      o.connect(g).connect(ctx.destination); o.start(t); o.stop(t + 0.4);
    });
    setTimeout(() => void ctx.close(), 1000);
  } catch { /* sound is optional */ }
  try { navigator.vibrate?.(120); } catch { /* optional */ }
}

/* ---------- swipe to confirm ---------- */
export function SwipeAction({ label, busy, onConfirm }: { label: string; busy: boolean; onConfirm: () => void }) {
  const track = useRef<HTMLDivElement>(null);
  const start = useRef<number | null>(null);
  const [x, setX] = useState(0);
  const max = () => (track.current?.clientWidth ?? 300) - 60;
  useEffect(() => { if (!busy) setX(0); }, [busy]);
  function down(e: PointerEvent<HTMLButtonElement>) { if (busy) return; start.current = e.clientX - x; e.currentTarget.setPointerCapture(e.pointerId); }
  function move(e: PointerEvent<HTMLButtonElement>) { if (start.current == null) return; setX(Math.max(0, Math.min(max(), e.clientX - start.current))); }
  function up() {
    if (start.current == null) return; start.current = null;
    if (x >= max() * 0.85) { setX(max()); try { navigator.vibrate?.(30); } catch { /* optional */ } onConfirm(); } else setX(0);
  }
  function key(e: KeyboardEvent<HTMLButtonElement>) { if ((e.key === 'Enter' || e.key === ' ') && !busy) { e.preventDefault(); onConfirm(); } }
  const pct = Math.min(100, (x / Math.max(1, max())) * 100);
  return <div className={`swipe ${busy ? 'swipe-busy' : ''}`} ref={track}>
    <span className="swipe-fill" style={{ width: `calc(${pct}% + 56px)` }}/>
    <span className="swipe-label" style={{ opacity: 1 - pct / 120 }}>{busy ? 'Saving…' : `Slide to ${label.toLowerCase()}`}</span>
    <button type="button" className="swipe-thumb" style={{ transform: `translateX(${x}px)` }} aria-label={`${label} (press Enter or slide)`} disabled={busy}
      onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up} onKeyDown={key}>
      {busy ? <Loader2 className="spin"/> : <ChevronsRight/>}
    </button>
  </div>;
}

/* ---------- share tracking ---------- */
export function ShareTracking({ url, business }: { url: string; business?: string | null }) {
  const text = `Track my delivery${business ? ` from ${business}` : ''}: ${url}`;
  const [done, setDone] = useState(false);
  async function share() {
    if (navigator.share) { try { await navigator.share({ title: 'Delivery tracking', text, url }); return; } catch { return; } }
    try { await navigator.clipboard.writeText(url); setDone(true); setTimeout(() => setDone(false), 2000); } catch { /* ignore */ }
  }
  return <div className="share-row">
    <Button variant="coral" className="share-btn" onClick={share}><Share2/> {done ? 'Link copied' : 'Share'}</Button>
    <Button variant="outline" className="share-btn" asChild><a href={`https://wa.me/?text=${encodeURIComponent(text)}`} target="_blank" rel="noreferrer"><MessageCircle/> Share on WhatsApp</a></Button>
    <Button variant="outline" className="share-btn" asChild><a href={`sms:?&body=${encodeURIComponent(text)}`}><MessageSquare/> SMS</a></Button>
  </div>;
}

/* ---------- rough arrival estimate ---------- */
export function arrivalText(status: ShipmentStatus, scheduledAt?: string | null): string | null {
  if (!scheduledAt) return status === 'out_for_delivery' ? 'Arriving shortly' : null;
  const iso = /Z|[+-]\d\d:?\d\d$/.test(scheduledAt) ? scheduledAt : `${scheduledAt}Z`;
  const mins = Math.round((new Date(iso).getTime() - Date.now()) / 60000);
  if (Number.isNaN(mins)) return null;
  if (mins <= 10) return status === 'out_for_delivery' ? 'Arriving any minute now' : 'Arriving soon';
  if (mins < 60) return `Arriving in about ${Math.round(mins / 5) * 5 || 5} min`;
  if (mins < 24 * 60) { const h = Math.round(mins / 30) / 2; return `Arriving in about ${h} hour${h === 1 ? '' : 's'}`; }
  const d = Math.round(mins / 1440); return `Arriving in about ${d} day${d === 1 ? '' : 's'}`;
}

/* ---------- relative time ---------- */
export function timeAgo(iso?: string | null): string {
  if (!iso) return '';
  const t = new Date(/Z|[+-]\d\d:?\d\d$/.test(iso) ? iso : `${iso}Z`).getTime();
  if (Number.isNaN(t)) return '';
  const m = Math.max(0, Math.round((Date.now() - t) / 60000));
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} hour${h === 1 ? '' : 's'} ago`;
  const d = Math.round(h / 24);
  return `${d} day${d === 1 ? '' : 's'} ago`;
}

/* ---------- status illustrations ---------- */
export function StatusArt({ status }: { status: ShipmentStatus }) {
  const kind = status === 'delivered' ? 'done' : status === 'failed' ? 'missed' : status === 'returned' || status === 'cancelled' ? 'back' : status === 'assigned' ? 'packed' : 'road';
  return <svg className={`status-art art-${kind}`} viewBox="0 0 120 80" aria-hidden="true">
    <ellipse cx="60" cy="72" rx="46" ry="5" className="art-shadow"/>
    {kind === 'packed' && <g className="art-bob"><rect x="38" y="24" width="44" height="40" rx="4" className="art-box"/><path d="M38 36h44M60 24v12" className="art-line"/><rect x="52" y="44" width="16" height="6" rx="1.5" className="art-label"/></g>}
    {kind === 'road' && <g><path d="M8 66h104" className="art-road"/><g className="art-drive"><rect x="44" y="30" width="26" height="22" rx="3" className="art-box"/><path d="M70 52h14l6-10h-10l-4-8" className="art-line"/><circle cx="50" cy="58" r="7" className="art-wheel"/><circle cx="86" cy="58" r="7" className="art-wheel"/></g><path d="M18 40h14M12 48h16" className="art-speed"/></g>}
    {kind === 'done' && <g className="art-pop"><circle cx="60" cy="38" r="26" className="art-ok"/><path d="M48 38l8 8 16-16" className="art-tick"/></g>}
    {kind === 'missed' && <g className="art-bob"><path d="M36 64V32l24-16 24 16v32z" className="art-house"/><rect x="54" y="44" width="12" height="20" className="art-door"/><circle cx="90" cy="22" r="10" className="art-warn"/><path d="M90 16v7M90 27v1" className="art-tick"/></g>}
    {kind === 'back' && <g className="art-bob"><rect x="40" y="26" width="40" height="36" rx="4" className="art-box"/><path d="M72 18a18 18 0 1 0 6 20" className="art-line"/><path d="M78 30l0 9-9-1" className="art-line"/></g>}
  </svg>;
}
