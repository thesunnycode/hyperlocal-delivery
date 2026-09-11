// The delivery state machine — mirrors STATE_MACHINE.md exactly. This is the
// one file every surface imports for status labels, icons, legal
// transitions and the state-class used to tint the status hero. Keep it in
// sync with the backend enum (CREATED does not exist — shipments are
// auto-assigned straight into ASSIGNED).
import type { FailureReason, ShipmentStatus } from '../types/api';

export const STATUSES: ShipmentStatus[] = [
  'assigned',
  'picked_up',
  'in_transit',
  'out_for_delivery',
  'delivered',
  'failed',
  'returned',
  'cancelled'
];

/** How the hero and badge tint themselves for a given status. */
export type StateClass = 'inflight' | 'live' | 'terminal' | 'exception';

/**
 * The lucide-react icon names this machine refers to. Closed on purpose:
 * Each surface maps these to its own badge, and a name added here without a
 * matching entry there is a compile error rather than a blank badge.
 */
export type StatusIconName =
  | 'ClipboardCheck'
  | 'Package'
  | 'Truck'
  | 'Navigation'
  | 'CheckCircle2'
  | 'AlertTriangle'
  | 'Undo2'
  | 'XCircle';

export type StatusMeta = {
  label: string;
  icon: StatusIconName;
  /** Position in FLOW, 1-based. `failed` shares step 4 with out_for_delivery. */
  step: number;
  stateClass: StateClass;
  /** Terminal for the system: nothing can move it onwards. */
  systemTerminal?: boolean;
  /** Terminal for the agent only — an owner can still reassign it back. */
  agentTerminal?: boolean;
};

export const STATUS_META: Record<ShipmentStatus, StatusMeta> = {
  assigned: { label: 'Assigned', icon: 'ClipboardCheck', step: 1, stateClass: 'inflight' },
  picked_up: { label: 'Picked up', icon: 'Package', step: 2, stateClass: 'inflight' },
  in_transit: { label: 'In transit', icon: 'Truck', step: 3, stateClass: 'inflight' },
  out_for_delivery: { label: 'Out for delivery', icon: 'Navigation', step: 4, stateClass: 'live' },
  delivered: { label: 'Delivered', icon: 'CheckCircle2', step: 5, stateClass: 'terminal', systemTerminal: true },
  failed: { label: 'Failed', icon: 'AlertTriangle', step: 4, stateClass: 'exception', agentTerminal: true },
  returned: { label: 'Returned', icon: 'Undo2', step: 5, stateClass: 'terminal', systemTerminal: true },
  cancelled: { label: 'Cancelled', icon: 'XCircle', step: 5, stateClass: 'terminal', systemTerminal: true }
};

// The 5-segment progress strip used on the customer + agent status heroes.
export const FLOW: ShipmentStatus[] = [
  'assigned',
  'picked_up',
  'in_transit',
  'out_for_delivery',
  'delivered'
];

export type AgentStep = {
  next: ShipmentStatus;
  cta: string;
  kind: 'NEXT STEP' | 'OUTCOME';
  /** Only out_for_delivery forks — to Failed (via the sheet) or Returned. */
  fork?: boolean;
  hint: string;
};

// Agent — exactly one legal forward step per state, relabelled per state.
// out_for_delivery forks to Failed (via the attempt sheet) or Returned.
export const AGENT_NEXT: Partial<Record<ShipmentStatus, AgentStep>> = {
  assigned: { next: 'picked_up', cta: 'Confirm pickup', kind: 'NEXT STEP', hint: 'One legal move.' },
  picked_up: { next: 'in_transit', cta: 'Start transit', kind: 'NEXT STEP', hint: 'The label swaps in place after the tap.' },
  in_transit: { next: 'out_for_delivery', cta: 'Out for delivery', kind: 'NEXT STEP', hint: 'Still a single move.' },
  out_for_delivery: {
    next: 'delivered', cta: 'Mark delivered', kind: 'OUTCOME', fork: true,
    hint: 'Failed opens the attempt sheet first — the status changes only once a reason is logged.'
  }
};

export const FAILURE_REASONS: FailureReason[] = [
  'Customer absent',
  'Address not found',
  'Refused',
  'Damaged',
  'Other'
];

export function isTerminal(status: ShipmentStatus): boolean {
  return status === 'delivered' || status === 'returned' || status === 'cancelled';
}

// Owner mutation #1 — plain reassignment, legal on ANY non-terminal shipment
// (not just Failed). Owner mutation #2 — the one status transition an owner
// may perform: Failed back to Assigned. Owner mutation #3 — cancel outright,
// legal on the same set of statuses as reassignment.
export function canReassignAgent(status: ShipmentStatus): boolean {
  return !isTerminal(status);
}
export function canRestoreToAssigned(status: ShipmentStatus): boolean {
  return status === 'failed';
}
export function canCancel(status: ShipmentStatus): boolean {
  return !isTerminal(status);
}
