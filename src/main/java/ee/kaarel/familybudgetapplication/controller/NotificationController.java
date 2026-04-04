package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.service.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ListResponse<?> getNotifications(Pageable pageable) {
        return notificationService.getNotifications(pageable);
    }

    @GetMapping("/unread-count")
    public ApiResponse<?> getUnreadCount() {
        return new ApiResponse<>(notificationService.getUnreadCount());
    }

    @PutMapping("/{id}/read")
    public ApiResponse<?> markAsRead(@PathVariable Long id) {
        return new ApiResponse<>(notificationService.markAsRead(id));
    }

    @PutMapping("/read-all")
    public ApiResponse<?> markAllAsRead() {
        notificationService.markAllAsRead();
        return new ApiResponse<>("Notifications marked as read");
    }

    @DeleteMapping
    public ApiResponse<?> deleteAllNotifications() {
        notificationService.deleteAllNotificationsForCurrentUser();
        return new ApiResponse<>("Notifications deleted");
    }
}
