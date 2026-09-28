package com.darshan.circl.notification;

import com.darshan.circl.common.outbox.ActivityEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka delivers at least once, so the same event can show up again after a crash or a rebalance.
 * That is fine here because EventNotifier inserts with ON CONFLICT DO NOTHING on (event_id, user_id).
 */
@Component
public class NotificationListener {

    private final EventNotifier notifier;

    public NotificationListener(EventNotifier notifier) {
        this.notifier = notifier;
    }

    @KafkaListener(topics = "${circl.events.topic}", groupId = "circl-notifications")
    public void on(ActivityEvent event) {
        notifier.handle(event);
    }
}
