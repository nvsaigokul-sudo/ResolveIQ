package com.resolveiq.backend.notifications.adapters;

import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.notification.NotificationChannelType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationAdapterRegistry {

    private final List<NotificationDeliveryAdapter> adapters;

    public NotificationAdapterRegistry(List<NotificationDeliveryAdapter> adapters) {
        this.adapters = adapters;
    }

    public NotificationDeliveryAdapter getAdapter(NotificationChannelType channelType) {
        return adapters.stream()
                .filter(a -> a.supports(channelType))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Unsupported notification channel type: " + channelType));
    }
}
