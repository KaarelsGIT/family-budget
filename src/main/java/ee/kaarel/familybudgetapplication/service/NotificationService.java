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
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
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
        createNotification(user, type, message, null, null);
    }

    @Transactional
    public void createNotification(User user, NotificationType type, String message, String action) {
        createNotification(user, type, message, action, null);
    }

    @Transactional
    public void createNotification(User user, NotificationType type, String message, String action, Long relatedCategoryId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        notification.setAction(action);
        notification.setRelatedCategoryId(relatedCategoryId);
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
    public void createNotificationIfAbsent(User user, NotificationType type, String message, String action) {
        if (!notificationRepository.existsByUserAndTypeAndMessage(user, type, message)) {
            createNotification(user, type, message, action);
        }
    }

    @Transactional
    public void createNotificationIfAbsent(User user, NotificationType type, String message, String action, Long relatedCategoryId) {
        if (!notificationRepository.existsByUserAndTypeAndMessage(user, type, message)) {
            createNotification(user, type, message, action, relatedCategoryId);
        }
    }

    @Transactional
    public void notifyAdmins(NotificationType type, String message) {
        List<User> admins = userRepository.findAllByRole(Role.ADMIN);
        admins.forEach(admin -> {
            createNotificationIfAbsent(admin, type, message);
        });
    }

    @Transactional
    public void notifyMoneyReceived(User recipient, User sender, BigDecimal amount, String toAccountName) {
        createNotification(
                recipient,
                NotificationType.MONEY_RECEIVED,
                localizeMoneyReceivedMessage(recipient, sender.getUsername(), amount, toAccountName)
        );
    }

    @Transactional(readOnly = true)
    public ListResponse<NotificationResponse> getNotifications(Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by(Sort.Order.desc("createdAt")));
        Page<Notification> page = notificationRepository.findAllByUser(currentUser, sorted);
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        User currentUser = currentUserService.getCurrentUser();
        return notificationRepository.countByUserAndIsReadFalse(currentUser);
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

    @Transactional
    public void markAllAsRead() {
        User currentUser = currentUserService.getCurrentUser();
        List<Notification> unreadNotifications = notificationRepository.findAllByUserAndIsReadFalse(currentUser);
        unreadNotifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unreadNotifications);
    }

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getAction(),
                notification.getRelatedCategoryId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    private String localizeMoneyReceivedMessage(User recipient, String senderUsername, BigDecimal amount, String toAccountName) {
        String preferredLanguage = resolvePreferredLanguage(recipient);
        String formattedAmount = formatCurrency(preferredLanguage, amount);
        return switch (preferredLanguage) {
            case "en" -> "You received " + formattedAmount + " from " + senderUsername + " to account " + toAccountName;
            case "fi" -> "Sait " + formattedAmount + " kayttajalta " + senderUsername + " tilille " + toAccountName;
            default -> "Said " + formattedAmount + " kasutajalt " + senderUsername + " kontole " + toAccountName;
        };
    }

    private String formatCurrency(String language, BigDecimal amount) {
        Locale locale = switch (language) {
            case "en" -> Locale.ENGLISH;
            case "fi" -> new Locale("fi", "FI");
            default -> new Locale("et", "EE");
        };

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
        currencyFormat.setCurrency(java.util.Currency.getInstance("EUR"));
        return currencyFormat.format(amount);
    }

    private String resolvePreferredLanguage(User user) {
        String preferredLanguage = user.getPreferredLanguage();
        if ("en".equals(preferredLanguage) || "fi".equals(preferredLanguage) || "et".equals(preferredLanguage)) {
            return preferredLanguage;
        }

        return "et";
    }
}
