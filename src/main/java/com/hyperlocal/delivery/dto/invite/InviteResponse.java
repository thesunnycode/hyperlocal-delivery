package com.hyperlocal.delivery.dto.invite;

/**
 * What the owner gets back after issuing an invite.
 *
 * <p>Carries the full link rather than the bare token so the owner console can
 * offer "copy" and hand it to WhatsApp without rebuilding the URL — the server
 * owns the shape of its own links. This is the only moment the plaintext token
 * exists outside the recipient's mailbox.
 *
 * @param inviteUrl  the link to send
 * @param expiresAt  when it stops working, as a UTC ISO-8601 string (see
 *                   {@link com.hyperlocal.delivery.util.TimeUtils#toIso})
 * @param emailed    whether the server also sent it (false when SMTP is not
 *                   configured, so the console can tell the owner they must
 *                   deliver it themselves rather than implying it was sent)
 */
public record InviteResponse(String inviteUrl, String expiresAt, boolean emailed) {
}
