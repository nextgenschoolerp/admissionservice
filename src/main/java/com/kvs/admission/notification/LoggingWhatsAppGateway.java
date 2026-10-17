package com.kvs.admission.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Default WhatsApp gateway that only logs messages. It is replaced automatically as soon as
 * a real provider bean implementing {@link WhatsAppGateway} is registered.
 */
@Slf4j
public class LoggingWhatsAppGateway implements WhatsAppGateway {

    @Override
    public String send(String recipientPhone, String message) {
        String messageId = "LOG-" + UUID.randomUUID();
        log.info("[WhatsApp stub] to={} id={} message={}", maskPhone(recipientPhone), messageId, message);
        return messageId;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    @Configuration
    static class DefaultGatewayConfig {
        @Bean
        @ConditionalOnMissingBean(WhatsAppGateway.class)
        WhatsAppGateway loggingWhatsAppGateway() {
            return new LoggingWhatsAppGateway();
        }
    }
}
