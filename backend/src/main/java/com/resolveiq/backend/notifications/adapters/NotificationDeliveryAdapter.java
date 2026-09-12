package com.resolveiq.backend.notifications.adapters;

import com.resolveiq.backend.domain.NotificationChannelEntity;
import com.resolveiq.backend.domain.NotificationEntity;
import com.resolveiq.common.notification.NotificationChannelType;

public interface NotificationDeliveryAdapter {

    boolean supports(NotificationChannelType channelType);

    DeliveryResult deliver(NotificationChannelEntity channel, NotificationEntity notification);
}
