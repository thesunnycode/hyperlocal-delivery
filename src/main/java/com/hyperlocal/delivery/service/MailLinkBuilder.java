package com.hyperlocal.delivery.service;

/**
 * Builds the links that carry a raw token, shared by the reset mailers
 * ({@link ConsoleMailService}, {@link SmtpMailService}) and the invite mailers
 * ({@link ConsoleAgentInviteMailer}, {@link SmtpAgentInviteMailer}).
 */
final class MailLinkBuilder {

    private MailLinkBuilder() {
        // utility class
    }

    /**
     * Appends {@code token=<rawToken>} to {@code baseUrl}, using {@code &}
     * instead of {@code ?} when the base URL already carries a query
     * string, so a configured base URL like
     * {@code https://app.example.com/reset?lang=en} doesn't end up with two
     * {@code ?} characters.
     */
    static String buildResetLink(String baseUrl, String rawToken) {
        return append(baseUrl, rawToken);
    }

    /** Same rule for the agent invite link. */
    static String buildInviteLink(String baseUrl, String rawToken) {
        return append(baseUrl, rawToken);
    }

    private static String append(String baseUrl, String rawToken) {
        char separator = baseUrl.contains("?") ? '&' : '?';
        return baseUrl + separator + "token=" + rawToken;
    }
}
