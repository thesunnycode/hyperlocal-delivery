package com.hyperlocal.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyperlocal.delivery.model.OtpPurpose;

/**
 * Dev/test fallback implementation of {@link OtpEmailService} that logs
 * the OTP code to the console instead of sending a real email. Registered
 * by {@link com.hyperlocal.delivery.config.MailConfig} only under the
 * {@code dev}/{@code test}/{@code mysql-test} profiles, and only when
 * {@link SmtpOtpEmailService} didn't already register (i.e.
 * {@code spring.mail.host} is blank). Deliberately narrower than the
 * password-reset/agent-invite console mailers, which fall back in every
 * profile: an OTP is a live credential, so a misconfigured prod deploy
 * (SMTP_HOST blank) should fail loudly at startup rather than silently
 * start logging real users' OTP codes in plaintext.
 *
 * <p>See {@code MailConfig}'s Javadoc for why the console/SMTP pair is
 * wired as {@code @Bean} methods rather than {@code @Profile}/
 * {@code @ConditionalOnExpression}-gated {@code @Component}s directly:
 * those two conditions used to be independent and could both hold at once
 * (e.g. {@code dev} profile + real SMTP configured for local OTP testing),
 * which failed startup with a duplicate-bean error instead of picking one.
 *
 * <p>Logging the plaintext code is acceptable here because this bean is
 * only ever active in a dev/test profile with no real mail transport
 * configured.
 */
public class ConsoleOtpEmailService implements OtpEmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleOtpEmailService.class);

    @Override
    public void sendOtpEmail(String toEmail, String otpCode, OtpPurpose purpose) {
        log.info("[DEV MAIL] OTP code generated for {} ({}): {}", toEmail, purpose, otpCode);
    }
}
