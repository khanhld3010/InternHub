package org.example.internservice.intern.client;

import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "notification-service", url = "${app.notification-service.url:http://notification-service:8085}")
public interface NotificationServiceClient {

    @PostMapping("/api/notifications/internal")
    void sendNotification(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody CreateNotificationInternalRequest request
    );
}
