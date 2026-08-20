package com.hyperlocal.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.hyperlocal.delivery.exception.MailDeliveryException;
import com.hyperlocal.delivery.model.OtpPurpose;

/**
 * Production OTP email sender. Delivers verification codes via Google SMTP
 * (smtp.gmail.com:587, STARTTLS, app-password authentication).
 *
 * <p>Registered by {@link com.hyperlocal.delivery.config.MailConfig} only
 * when {@code spring.mail.host} is set to a non-blank value — regardless of
 * active Spring profile, including {@code dev}, so a local run can still
 * send real OTP emails when SMTP is configured. See that class's Javadoc:
 * this used to be a directly {@code @ConditionalOnExpression}-gated
 * {@code @Component}, independent of {@link ConsoleOtpEmailService}'s old
 * {@code @Profile} gate, which let both register at once (e.g.
 * {@code dev} profile + real SMTP configured) and crashed startup with a
 * duplicate-bean error instead of picking one.
 *
 * <p>On any SMTP failure, logs the error and throws {@link MailDeliveryException}
 * without retrying.
 */
public class SmtpOtpEmailService implements OtpEmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpOtpEmailService.class);

    private static final String SENDER_NAME = "Hyperlocal Delivery";
    private static final String SUBJECT = "Hyperlocal Delivery \u2014 Your verification code";
    private static final int OTP_EXPIRY_MINUTES = 5;

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpOtpEmailService(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendOtpEmail(String toEmail, String otpCode, OtpPurpose purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!fromAddress.isBlank()) {
            message.setFrom(SENDER_NAME + " <" + fromAddress + ">");
        }
        message.setTo(toEmail);
        message.setSubject(SUBJECT);
        message.setText(buildBody(otpCode, purpose));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("SMTP delivery failed for OTP email to={}, purpose={}: {}",
                    toEmail, purpose, ex.getMessage(), ex);
            throw new MailDeliveryException("Email delivery failed. Please try again later.", ex);
        }
    }

    private String buildBody(String otpCode, OtpPurpose purpose) {
        String purposeDescription = switch (purpose) {
            case REGISTRATION -> "complete your registration";
            case PASSWORD_RESET -> "reset your password";
        };

        return """
                Your verification code is:

                    %s

                Use this code to %s. It expires in %d minutes.

                If you did not request this code, please ignore this email. Do not share this code with anyone.

                — %s""".formatted(otpCode, purposeDescription, OTP_EXPIRY_MINUTES, SENDER_NAME);
    }
}
