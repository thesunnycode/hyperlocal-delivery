package com.hyperlocal.delivery.dto.invite;

import java.time.LocalDateTime;

/**
 * What the (unauthenticated) setup screen may know before a password is set.
 *
 * <p>Deliberately thin: enough to greet the person and show them which
 * mailbox and which business the account belongs to, and nothing that would
 * make a leaked link worth harvesting. No id, no phone, no shipment data.
 */
public record InvitePreviewResponse(
        String fullName,
        String email,
        String businessName,
        LocalDateTime expiresAt) {
}
