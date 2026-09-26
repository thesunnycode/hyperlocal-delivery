import { createContext, useContext } from 'react';
import { riders as sampleRiders, shipments as sampleShipments } from './delivery-data';
import type { riders, Shipment, ShipmentStatus } from './delivery-data';

export type PreviewRider = (typeof riders)[number] & { email?: string; phone?: string; invited?: boolean };
export type DeliveryEvent = { status: ShipmentStatus; note?: string; time: string };
export type PreviewState = {
  riders: PreviewRider[];
  shipments: Shipment[];
  events: Record<string, DeliveryEvent[]>;
  addRider: (person: { name: string; email: string; phone: string }) => void;
  advance: (id: string, status: ShipmentStatus, note?: string) => void;
};

export const DeliveryPreviewContext = createContext<PreviewState | null>(null);

const fallback: PreviewState = {
  riders: sampleRiders,
  shipments: sampleShipments,
  events: {},
  addRider: () => {},
  advance: () => {},
};

export function useDeliveryPreview() {
  const value = useContext(DeliveryPreviewContext);
  if (!value) {
    if (import.meta.env.DEV) console.warn('Delivery preview provider is missing; showing read-only examples');
    return fallback;
  }
  return value;
}