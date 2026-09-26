import { useState, type ReactNode } from 'react';
import { riders, shipments, type ShipmentStatus } from './delivery-data';
import { DeliveryPreviewContext, type DeliveryEvent, type PreviewRider } from './delivery-preview-context';

const initialEvents: Record<string, DeliveryEvent[]> = Object.fromEntries(shipments.map(s => [s.id, [{ status: s.status, time: s.time }]]));
export function DeliveryPreviewProvider({ children }: { children: ReactNode }) {
  const [team, setTeam] = useState<PreviewRider[]>(riders);
  const [board, setBoard] = useState(shipments);
  const [events, setEvents] = useState(initialEvents);
  const addRider = ({ name, email, phone }: { name: string; email: string; phone: string }) => {
    const initials = name.trim().split(/\s+/).map(part => part[0]?.toUpperCase()).slice(0, 2).join('');
    setTeam(current => [{ name: name.trim(), email: email.trim(), phone: phone.trim(), initials, state: 'Invite pending', deliveries: 0, invited: true }, ...current]);
  };
  const advance = (id: string, status: ShipmentStatus, note?: string) => {
    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    setBoard(current => current.map(item => item.id === id ? { ...item, status, time } : item));
    setEvents(current => ({ ...current, [id]: [...(current[id] ?? []), { status, ...(note ? { note } : {}), time }] }));
  };
  return <DeliveryPreviewContext.Provider value={{ riders: team, shipments: board, events, addRider, advance }}>{children}</DeliveryPreviewContext.Provider>;
}
