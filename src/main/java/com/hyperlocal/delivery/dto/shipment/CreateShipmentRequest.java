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
 * {@code @JsonAlias} because the frontend ({@code shipmentsApi.ts}
 * {@code createShipment}) sends {@code address} and {@code scheduledAt} —
 * the same request-side approach as the {@code note}/{@code notes} alias on
 * {@code AdvanceRequest} and the {@code name}/{@code fullName} alias on
 * {@code CreateAgentRequest}.
 */
public record CreateShipmentRequest(
        @NotBlank @Size(min = 2, max = 200) String customerName,
        @NotBlank @Pattern(regexp = "[+\\d\\s\\-]{7,20}") String customerPhone,
        @NotBlank @Size(min = 5, max = 1000) @JsonAlias("address") String deliveryAddress,
        @Future @JsonAlias("scheduledAt") LocalDateTime scheduledDeliveryAt
) {}
