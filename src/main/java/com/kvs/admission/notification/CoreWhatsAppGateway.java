package com.kvs.admission.notification;

import com.kvs.core.notification.NotificationChannel;
import com.kvs.core.notification.NotificationDispatchException;
import com.kvs.core.notification.NotificationDispatcher;
import com.kvs.core.notification.NotificationMessage;
import org.springframework.stereotype.Component;

/** WhatsApp gateway backed by the shared notification layer in ms-core (provider set by kvs.notification.whatsapp). */
@Component
public class CoreWhatsAppGateway implements WhatsAppGateway {

    private final NotificationDispatcher dispatcher;

    public CoreWhatsAppGateway(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String send(String recipientPhone, String message) {
        try {
            return dispatcher.send(NotificationChannel.WHATSAPP, new NotificationMessage(recipientPhone, null, message));
        } catch (NotificationDispatchException e) {
            throw new WhatsAppDispatchException(e.getMessage(), e);
        }
    }
}
