package com.hyperlocal.delivery.dto.shipment;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * Optional free-text note accompanying a single-step agent transition
 * (pickup, transit, out-for-delivery, deliver, return).
 */
public record AdvanceRequest(@JsonAlias("note") @Size(max = 1000) String notes) {}
