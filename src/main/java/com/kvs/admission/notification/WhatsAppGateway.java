package com.kvs.admission.notification;

/**
 * Outbound WhatsApp messaging provider used for applicant nudges (US-02009).
 * Implementations must throw {@link WhatsAppDispatchException} when the provider rejects a message.
 */
public interface WhatsAppGateway {

    /**
     * Sends a text message.
     *
     * @param recipientPhone E.164 phone number
     * @param message        message text
     * @return the provider's message identifier
     */
    String send(String recipientPhone, String message);

    class WhatsAppDispatchException extends RuntimeException {
        public WhatsAppDispatchException(String message) {
            super(message);
        }

        public WhatsAppDispatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
