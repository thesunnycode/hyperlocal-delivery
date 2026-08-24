package com.hyperlocal.delivery.dto.shipment;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new shipment.
 *
 * <p>{@code deliveryAddress} and {@code scheduledDeliveryAt} carry
 * {@code @JsonAlias} because the real frontend (shipmentsApi.js
 * {@code createShipment}) sends {@code address} and {@code scheduledAt} —
 * same precedent as the {@code note}/{@code notes} and
 * {@code fullName}/{@code name} request-side aliases fixed elsewhere in this
 * plan.
 */
public record CreateShipmentRequest(
        @NotBlank @Size(min = 2, max = 200) String customerName,
        @NotBlank @Pattern(regexp = "[+\\d\\s\\-]{7,20}") String customerPhone,
        @NotBlank @Size(min = 5, max = 1000) @JsonAlias("address") String deliveryAddress,
        @Future @JsonAlias("scheduledAt") LocalDateTime scheduledDeliveryAt
) {}
