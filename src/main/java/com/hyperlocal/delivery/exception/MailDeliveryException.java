package com.hyperlocal.delivery.exception;

/**
 * Thrown when an OTP email cannot be delivered via SMTP.
 *
 * <p>The caller should treat this as a non-retriable failure. Error details
 * are logged at the point of origin; this exception carries only a generic
 * message suitable for API responses.
 */
public class MailDeliveryException extends DomainException {

    public MailDeliveryException(String message) {
        super(ErrorCode.EMAIL_DELIVERY_FAILED, message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(ErrorCode.EMAIL_DELIVERY_FAILED, message);
        initCause(cause);
    }
}
