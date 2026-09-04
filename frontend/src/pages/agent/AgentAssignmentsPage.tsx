import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Inbox, WifiOff, RefreshCw, TimerOff, ArrowRight, ChevronRight } from 'lucide-react';
import { listMyShipments } from '../../api/shipmentsApi';
import { useAuth } from '../../lib/AuthContext.tsx';
import { formatTime } from '../../utils/format';
import { isTerminal, STATUS_META } from '../../utils/statusMachine';
import type { ShipmentStatus, ShipmentSummary } from '../../types/api';

/**
 * A3 — read-only. Nothing advances from here; every mutation happens in A4,
 * so one place owns the state machine.
 *
 * Structure follows Shopify's Orders screen (Refero ce43cc1f): an app bar with
 * the title and its actions, then a dark summary strip, then the list. The
 * previous design opened with a large figure and no chrome at all, which left
 * refresh and sign-out stranded at the bottom of the scroll — the two controls
 * an agent reaches for most.
 *
 * The empty state used to say "Pull to refresh." There is no such gesture, and
 * this screen is usually a new agent's first, so that was the first instruction
 * they ever read. The Refresh control is in the app bar and the copy names it.
 */

const FILTERS: { id: string; label: string; match: (s: ShipmentSummary) => boolean }[] = [
  { id: 'all', label: 'All', match: () => true },
  { id: 'out_for_delivery', label: 'Out for delivery', match: (s) => s.status === 'out_for_delivery' },
  { id: 'in_transit', label: 'In transit', match: (s) => s.status === 'in_transit' },
  { id: 'assigned', label: 'Assigned', match: (s) => s.status === 'assigned' },
  { id: 'failed', label: 'Failed', match: (s) => s.status === 'failed' }
];

/** "Ravi Kumar" -> "RK". noUncheckedIndexedAccess is on, so guard every index. */
function initials(name: string | null | undefined): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  const first = parts[0] ?? '';
  const last = parts[parts.length - 1] ?? '';
  if (!first) return 'AG';
  if (parts.length === 1) return first.slice(0, 2).toUpperCase();
  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();
}

export default function AgentAssignmentsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState<'loading' | 'ready' | 'expired'>('loading');
  const [ships, setShips] = useState<ShipmentSummary[]>([]);
  const [everLoaded, setEverLoaded] = useState(false);
  const [filter, setFilter] = useState('all');
  const [isOffline, setIsOffline] = useState(!navigator.onLine);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setRefreshing(true);
    try {
      const data = await listMyShipments();
      setShips(data || []);
      setEverLoaded(true);
      setStatus('ready');
    } catch (e: unknown) {
      const err = (e ?? {}) as { status?: number };
      if (err.status === 401 || err.status === 403) setStatus('expired');
      else setStatus('ready');
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
    const on = () => setIsOffline(false);
    const off = () => setIsOffline(true);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); };
  }, [load]);

  const openCount = ships.filter((s) => !isTerminal(s.status)).length;

  if (status === 'expired') {
    return (
      <div className="ag"><div className="ag-wrap">
        <div className="ag-edge ag-edge-top">
          <div className="ag-edge-icon"><TimerOff size={24} strokeWidth={1.9} /></div>
          <h2>Your session expired</h2>
          <p>
            You were signed out after a long gap. Nothing you logged has been lost{everLoaded
              ? <> — your {openCount} open {openCount === 1 ? 'delivery is' : 'deliveries are'} still assigned to you.</>
              : '.'}
          </p>
          <button type="button" className="ag-cta ag-cta-top" onClick={() => navigate('/login')}>
            Sign in again <ArrowRight size={17} strokeWidth={2.2} />
          </button>
        </div>
      </div></div>
    );
  }

  return (
    <div className="ag"><div className="ag-wrap">
      <div className="ag-appbar">
        <h1><span>{user?.name || 'Rider'}</span>Today</h1>
        <button type="button" className="ag-iconbtn" onClick={load} disabled={refreshing}
          aria-label={refreshing ? 'Refreshing' : 'Refresh assignments'}>
          <RefreshCw size={18} strokeWidth={2.2} />
        </button>
        <button type="button" className="ag-avatar"
          onClick={() => navigate('/agent/account')}
          aria-label={`Signed in as ${user?.name || 'rider'}. Open account`}>
          {initials(user?.name)}
        </button>
      </div>

      {status === 'loading' ? (
        <>
          <div className="ag-strip" aria-hidden="true">
            {['Open', 'Delivered · 7d', 'Failed'].map((l) => (
              <div key={l}><dt>{l}</dt><dd><span className="ag-skel ag-skel-stat" /></dd></div>
            ))}
          </div>
          <div className="ag-list">
            {[1, 2, 3].map((r) => (
              <div key={r} className="ag-row ag-s-assigned ag-skel-row">
                <div className="ag-skel ag-skel-name" />
                <div className="ag-skel ag-skel-addr" />
                <div className="ag-skel ag-skel-foot" />
              </div>
            ))}
          </div>
        </>
      ) : (
        <>
          <dl className="ag-strip">
            <div><dt>Open</dt><dd>{openCount}</dd></div>
            <div><dt>Delivered&nbsp;· 7d</dt><dd>{ships.filter((s) => s.status === 'delivered').length}</dd></div>
            <div className={ships.some((s) => s.status === 'failed') ? 'ag-hot' : undefined}>
              <dt>Failed</dt><dd>{ships.filter((s) => s.status === 'failed').length}</dd>
            </div>
          </dl>

          {isOffline && (
            <div className="ag-offline" role="status">
              <WifiOff size={17} strokeWidth={2} />
              <div>
                <b>No connection</b>
                Status changes will not save until you are back online.
              </div>
            </div>
          )}

          <div className="ag-chips-wrap">
          <div className="ag-chips" role="group" aria-label="Filter by status">
            {FILTERS.map((f) => {
              const n = ships.filter(f.match).length;
              return (
                <button key={f.id} type="button" className="ag-chip"
                  aria-pressed={filter === f.id} onClick={() => setFilter(f.id)}>
                  {f.label} <em>{n}</em>
                </button>
              );
            })}
          </div>
          </div>

          {(() => {
            const active = FILTERS.find((f) => f.id === filter) ?? FILTERS[0]!;
            /* Due time, earliest first. The API returns creation order, which
               for a rider is arbitrary — the captured order on a real day was
               11:00, 2:00, 5:00, 1:00. A round is a time-ordered list, and
               the row already leads with "Due …", so any other order makes
               that label read as noise. Windowless shipments sort last:
               they can be fitted around the ones that cannot move. */
            const visible = ships.filter(active.match).slice().sort((a, b) => {
              if (!a.scheduledAt) return b.scheduledAt ? 1 : 0;
              if (!b.scheduledAt) return -1;
              return new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime();
            });
            if (visible.length === 0) {
              return (
                <div className="ag-edge">
                  <div className="ag-edge-icon"><Inbox size={24} strokeWidth={1.9} /></div>
                  <h2>{filter === 'all' ? 'Nothing assigned' : 'Nothing here'}</h2>
                  <p>
                    {filter === 'all'
                      ? 'New shipments land here automatically — they go to whoever is carrying the least. Tap Refresh to check again.'
                      : 'No shipments have this status right now. Try another filter.'}
                  </p>
                </div>
              );
            }
            return (
              <div className="ag-list">
                {visible.map((s) => (
                  <button key={s.id} type="button"
                    className={`ag-row ag-s-${s.status}`}
                    onClick={() => navigate(`/agent/shipments/${s.id}`)}>
                    <div className="ag-row-top">
                      <span className="ag-row-name">{s.customerName}</span>
                      <span className={`ag-badge ag-s-${s.status}`}>{STATUS_META[s.status as ShipmentStatus].label}</span>
                    </div>
                    <div className="ag-row-addr">{s.address}</div>
                    <div className="ag-row-foot">
                      <span className="ag-row-time">{s.scheduledAt ? `Due ${formatTime(s.scheduledAt)}` : 'No window set'}</span>
                      <span className="ag-row-go">Open <ChevronRight size={15} strokeWidth={2.4} /></span>
                    </div>
                  </button>
                ))}
              </div>
            );
          })()}
        </>
      )}
    </div></div>
  );
}
