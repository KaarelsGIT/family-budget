package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.notification.NotificationResponse;
import ee.kaarel.familybudgetapplication.model.Notification;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.NotificationRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void createNotification(User user, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(OffsetDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public void createNotificationIfAbsent(User user, NotificationType type, String message) {
        if (!notificationRepository.existsByUserAndTypeAndMessage(user, type, message)) {
            createNotification(user, type, message);
        }
    }

    @Transactional
    public void notifyAdmins(NotificationType type, String message) {
        List<User> admins = userRepository.findAllByRole(Role.ADMIN);
        admins.forEach(admin -> {
            createNotificationIfAbsent(admin, type, message);
        });
    }

    @Transactional(readOnly = true)
    public ListResponse<NotificationResponse> getNotifications(Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by(Sort.Order.desc("createdAt")));
        Page<Notification> page = notificationRepository.findAllByUser(currentUser, sorted);
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional
    public NotificationResponse markAsRead(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot update this notification");
        }
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
