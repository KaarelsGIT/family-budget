package ee.kaarel.familybudgetapplication.dto.notification;

import ee.kaarel.familybudgetapplication.model.NotificationType;
import java.time.OffsetDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        boolean isRead,
        OffsetDateTime createdAt
) {
}
