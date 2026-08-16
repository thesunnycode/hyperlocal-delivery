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
 * Append-only record of a failed delivery attempt on a {@link Shipment}.
 *
 * <p>Immutable by design: once persisted, the row is never mutated. Has no
 * {@code updatedAt} column and no {@code @UpdateTimestamp}.
 */
@Entity
@Table(name = "delivery_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = { "shipment", "agent" })
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private User agent;

    @Builder.Default
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "failure_reason",
            nullable = false,
            columnDefinition = "ENUM('CUSTOMER_ABSENT','ADDRESS_NOT_FOUND','REFUSED','DAMAGED','OTHER')"
    )
    private FailureReason failureReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;
}
