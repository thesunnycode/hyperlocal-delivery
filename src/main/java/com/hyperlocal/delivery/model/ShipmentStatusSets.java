package com.hyperlocal.delivery.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Canonical definitions of shipment status groupings used across multiple
 * services. Centralised here so that adding a new {@link ShipmentStatus}
 * value requires updating exactly one file rather than grep-hunting for
 * scattered {@code EnumSet.of(...)} calls.
 */
public final class ShipmentStatusSets {

    private ShipmentStatusSets() {
        // utility class — no instances
    }

    /**
     * Statuses that count as "active" for load-balancing and agent
     * deactivation gating. A shipment in one of these states is currently
     * "in flight" and assigned to an agent who must complete it.
     *
     * <p>Does NOT include {@link ShipmentStatus#FAILED} — a failed shipment
     * is awaiting owner intervention (reassignment), not active agent work.
     */
    public static final Set<ShipmentStatus> ACTIVE_FOR_LOAD_COUNTING = EnumSet.of(
            ShipmentStatus.ASSIGNED,
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.OUT_FOR_DELIVERY);

    /**
     * Statuses from which an owner may reassign the shipment (change agent
     * and/or restore to ASSIGNED). Includes {@link ShipmentStatus#FAILED}
     * because the owner's only status mutation is FAILED → ASSIGNED.
     *
     * <p>Terminal statuses ({@code DELIVERED}, {@code RETURNED}) are
     * explicitly excluded — no mutation is possible once terminal.
     */
    public static final Set<ShipmentStatus> NON_TERMINAL_FOR_REASSIGNMENT = EnumSet.of(
            ShipmentStatus.ASSIGNED,
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.OUT_FOR_DELIVERY,
            ShipmentStatus.FAILED);

    /**
     * Terminal statuses — no further transitions are possible for any role.
     */
    public static final Set<ShipmentStatus> TERMINAL = EnumSet.of(
            ShipmentStatus.DELIVERED,
            ShipmentStatus.RETURNED,
            ShipmentStatus.CANCELLED);

    /**
     * Statuses from which an owner may cancel the shipment outright. Same
     * set as {@link #NON_TERMINAL_FOR_REASSIGNMENT} — anything the owner
     * could still reassign, they can also call off entirely.
     */
    public static final Set<ShipmentStatus> CANCELLABLE = NON_TERMINAL_FOR_REASSIGNMENT;
}
