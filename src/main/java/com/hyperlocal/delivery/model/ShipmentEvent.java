package com.hyperlocal.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Append-only record of a single {@link Shipment} status transition.
 *
 * <p>Immutable by design: no {@code updatedAt} field and no
 * {@code @UpdateTimestamp}. A row is inserted once when the shipment status
 * changes and is never mutated afterwards.
 */
@Entity
@Table(name = "shipment_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = { "shipment", "changedBy" })
public class ShipmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "from_status",
            columnDefinition = "ENUM('ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED')"
    )
    private ShipmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "to_status",
            nullable = false,
            columnDefinition = "ENUM('ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED')"
    )
    private ShipmentStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
