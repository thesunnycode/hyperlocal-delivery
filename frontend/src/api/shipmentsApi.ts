import { apiFetch, fetchAllPages } from '../lib/apiClient';
import type {
  AdvanceBody,
  CreateShipmentBody,
  FailAttemptBody,
  PublicTracking,
  ReassignBody,
  Shipment,
  ShipmentStatus,
  ShipmentSummary
} from '../types/api';

export type ListShipmentsParams = {
  status?: ShipmentStatus;
  /** A select value, so it arrives as a string from the owner surfaces. */
  agentId?: number | string;
  from?: string;
  to?: string;
};

// Owner/admin: filterable register of every shipment in the tenant.
//
// The endpoint is paged with Spring's defaults, so a plain call returned the
// first 20 rows and nothing said the rest existed. fetchAllPages walks to the
// end, which is what these screens mean by "the list".
export function listShipments({ status, agentId, from, to }: ListShipmentsParams = {}) {
  return fetchAllPages<ShipmentSummary>('/shipments', { params: { status, agentId, from, to } });
}

// Agent: their own open queue. status: undefined ('all', server default —
// all non-terminal) | 'assigned' | 'in_transit'. Matches the backend's
// ShipmentStatus wire values 1:1 (ShipmentController#myAssignments binds a
// `status` query param, not `filter`) — pass one of those two literal
// strings, or omit it entirely for "all".
export function listMyShipments({ status }: { status?: ShipmentStatus } = {}) {
  return fetchAllPages<ShipmentSummary>('/shipments/mine', { params: { status } });
}

export function getShipment(id: number | string) {
  return apiFetch<Shipment>(`/shipments/${id}`);
}

// Public, no auth — payload is shaped server-side to the privacy boundary in
// README.md: business name, status, customer name, address, scheduled time,
// delivered-at, and a {status,label,stamp} timeline. No agent identity, no
// phone, no internal ids, no failure reason.
export function trackShipment(token: string) {
  return apiFetch<PublicTracking>(`/track/${token}`, { skipAuth: true });
}

// Owner: create. Auto-assigned server-side to the least-loaded active agent;
// starts life already ASSIGNED (there is no CREATED state).
export function createShipment({
  customerName,
  customerPhone,
  address,
  scheduledAt
}: CreateShipmentBody) {
  return apiFetch<Shipment>('/shipments', {
    method: 'POST',
    body: { customerName, customerPhone, address, scheduledAt }
  });
}

// Agent — explicit per-action endpoints (replacing a generic status PUT).
// Each one only succeeds for the agent it is currently assigned to, and only
// from the one legal predecessor state — enforce both server-side.
export function confirmPickup(id: number | string, note?: string | null) {
  return apiFetch<Shipment>(`/shipments/${id}/pickup`, {
    method: 'POST',
    body: { note } satisfies AdvanceBody
  });
}
export function startTransit(id: number | string, note?: string | null) {
  return apiFetch<Shipment>(`/shipments/${id}/start-transit`, {
    method: 'POST',
    body: { note } satisfies AdvanceBody
  });
}
export function markOutForDelivery(id: number | string, note?: string | null) {
  return apiFetch<Shipment>(`/shipments/${id}/out-for-delivery`, {
    method: 'POST',
    body: { note } satisfies AdvanceBody
  });
}
export function markDelivered(id: number | string, note?: string | null) {
  return apiFetch<Shipment>(`/shipments/${id}/deliver`, {
    method: 'POST',
    body: { note } satisfies AdvanceBody
  });
}
// Attempt sheet: status only changes once a reason is logged; immutable,
// append-only attempt log — enforce append-only server-side, not just in UI.
export function logFailedAttempt(id: number | string, { reason, notes }: FailAttemptBody) {
  return apiFetch<Shipment>(`/shipments/${id}/fail`, { method: 'POST', body: { reason, notes } });
}
export function markReturned(id: number | string, note?: string | null) {
  return apiFetch<Shipment>(`/shipments/${id}/return`, {
    method: 'POST',
    body: { note } satisfies AdvanceBody
  });
}

// Owner — the only two mutations the state machine gives them:
// 1) plain reassignment, legal on ANY non-terminal shipment (agentId omitted
//    = auto, least-loaded active agent).
// 2) when the shipment is currently Failed, this same call also restores it
//    to Assigned as part of the reassignment (the only status transition an
//    owner may perform).
export function reassignShipment(
  id: number | string,
  { agentId, note }: Partial<ReassignBody> = {}
) {
  return apiFetch<Shipment>(`/shipments/${id}/reassign`, {
    method: 'POST',
    body: { agentId: agentId || null, note }
  });
}

// Owner — cancel outright, legal on any non-terminal shipment (same set as
// reassignShipment). Once cancelled, a shipment is terminal.
export function cancelShipment(id: number | string, note?: string | null) {
  return apiFetch<Shipment>(`/shipments/${id}/cancel`, {
    method: 'POST',
    body: { note } satisfies AdvanceBody
  });
}
