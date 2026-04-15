package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.model.AccountUser;
import ee.kaarel.familybudgetapplication.model.AccountUserRole;
import ee.kaarel.familybudgetapplication.dto.notification.NotificationResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.Notification;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.RecurringTransaction;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.TransactionReminder;
import ee.kaarel.familybudgetapplication.repository.AccountUserRepository;
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
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccountUserRepository accountUserRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(
            NotificationRepository notificationRepository,
            AccountUserRepository accountUserRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService
    ) {
        this.notificationRepository = notificationRepository;
        this.accountUserRepository = accountUserRepository;
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
        createNotification(user, type, message, action, relatedCategoryId, null);
    }

    @Transactional
    public void createNotification(User user, NotificationType type, String message, String action, Long relatedCategoryId, Long relatedReminderId) {
        createNotification(user, type, message, action, relatedCategoryId, relatedReminderId, null, null);
    }

    @Transactional
    public void createNotification(User user, NotificationType type, String message, String action, Long relatedCategoryId, Long relatedReminderId, Long relatedTransactionId) {
        createNotification(user, type, message, action, relatedCategoryId, relatedReminderId, relatedTransactionId, null);
    }

    @Transactional
    public void createNotification(User user, NotificationType type, String message, String action, Long relatedCategoryId, Long relatedReminderId, Long relatedTransactionId, Long relatedAccountId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        notification.setAction(action);
        notification.setRelatedCategoryId(relatedCategoryId);
        notification.setRelatedReminderId(relatedReminderId);
        notification.setRelatedTransactionId(relatedTransactionId);
        notification.setRelatedAccountId(relatedAccountId);
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
    public void createNotificationIfAbsent(User user, NotificationType type, String message, String action, Long relatedCategoryId, Long relatedReminderId) {
        createNotificationIfAbsent(user, type, message, action, relatedCategoryId, relatedReminderId, null, null);
    }

    @Transactional
    public void createNotificationIfAbsent(User user, NotificationType type, String message, String action, Long relatedCategoryId, Long relatedReminderId, Long relatedTransactionId) {
        createNotificationIfAbsent(user, type, message, action, relatedCategoryId, relatedReminderId, relatedTransactionId, null);
    }

    @Transactional
    public void createNotificationIfAbsent(User user, NotificationType type, String message, String action, Long relatedCategoryId, Long relatedReminderId, Long relatedTransactionId, Long relatedAccountId) {
        if (relatedTransactionId != null) {
            if (!notificationRepository.existsByUserAndTypeAndRelatedTransactionIdAndRelatedAccountId(user, type, relatedTransactionId, relatedAccountId)) {
                createNotification(user, type, message, action, relatedCategoryId, relatedReminderId, relatedTransactionId, relatedAccountId);
            }
            return;
        }

        if (relatedReminderId != null) {
            if (!notificationRepository.existsByUserAndTypeAndRelatedReminderId(user, type, relatedReminderId)) {
                createNotification(user, type, message, action, relatedCategoryId, relatedReminderId, null, relatedAccountId);
            }
            return;
        }

        if (!notificationRepository.existsByUserAndTypeAndMessage(user, type, message)) {
            createNotification(user, type, message, action, relatedCategoryId, null, null, relatedAccountId);
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
    public void notifyMoneyReceived(User recipient, User sender, BigDecimal amount, String sourceAccountName) {
        createNotification(
                recipient,
                NotificationType.MONEY_RECEIVED,
                localizeMoneyReceivedMessage(recipient, sender.getUsername(), amount, sourceAccountName)
        );
    }

    @Transactional
    public void notifySharedAccountTransaction(User recipient, User actor, Account account, TransactionType transactionType, BigDecimal amount) {
        createNotification(
                recipient,
                NotificationType.SHARED_ACCOUNT_TRANSACTION,
                localizeTransactionActivityMessage(
                        recipient,
                        actor.getUsername(),
                        account.getName(),
                        transactionType,
                        amount,
                        null,
                        null,
                        NotificationType.SHARED_ACCOUNT_TRANSACTION
                )
        );
    }

    @Transactional
    public void notifySharedAccountTransactionUsers(
            Account account,
            User actor,
            TransactionType transactionType,
            BigDecimal amount,
            Long transactionId,
            NotificationType notificationType
    ) {
        List<AccountUser> accountUsers = accountUserRepository.findAllByAccount(account);
        accountUsers.stream()
                .map(AccountUser::getUser)
                .filter(user -> !user.getId().equals(actor.getId()))
                .distinct()
                .forEach(recipient -> createNotificationIfAbsent(
                        recipient,
                        notificationType,
                        localizeTransactionActivityMessage(
                                recipient,
                                actor.getUsername(),
                                account.getName(),
                                transactionType,
                                amount,
                                transactionId,
                                account.getId(),
                                notificationType
                        ),
                        null,
                        null,
                        null,
                        transactionId,
                        account.getId()
                ));
    }

    @Transactional
    public void notifyAccountShared(User recipient, User sender, Account account, AccountUserRole role) {
        createNotificationIfAbsent(
                recipient,
                NotificationType.ACCOUNT_SHARED,
                localizeAccountSharedMessage(recipient, sender.getUsername(), account.getName(), role)
        );
    }

    @Transactional
    public void notifyAccountUnshared(User recipient, User sender, Account account) {
        createNotificationIfAbsent(
                recipient,
                NotificationType.ACCOUNT_UNSHARED,
                localizeAccountUnsharedMessage(recipient, sender.getUsername(), account.getName())
        );
    }

    @Transactional(readOnly = true)
    public ListResponse<NotificationResponse> getNotifications(Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by(Sort.Order.desc("createdAt")));
        Page<Notification> page = notificationRepository.findAllByUser(currentUser, sorted);
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional
    public void deleteAllNotificationsForCurrentUser() {
        User currentUser = currentUserService.getCurrentUser();
        notificationRepository.deleteAllByUser(currentUser);
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

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Tallinn")
    @Transactional
    public void deleteExpiredNotifications() {
        notificationRepository.deleteAllByCreatedAtBefore(OffsetDateTime.now().minusDays(10));
    }

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getAction(),
                notification.getRelatedCategoryId(),
                notification.getRelatedReminderId(),
                notification.getRelatedTransactionId(),
                notification.getRelatedAccountId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    @Transactional
    public void notifyRecurringTransactionDue(User recipient, RecurringTransaction recurringTransaction, TransactionReminder reminder) {
        String message = localizeRecurringTransactionDueMessage(
                recipient,
                recurringTransaction.getCategory().getName(),
                recurringTransaction.getAmount(),
                reminder.getDueDate()
        );
        createNotificationIfAbsent(
                recipient,
                NotificationType.RECURRING_PAYMENT_DUE,
                message,
                "PAY",
                null,
                reminder.getId()
        );
    }

    private String localizeMoneyReceivedMessage(User recipient, String senderUsername, BigDecimal amount, String sourceAccountName) {
        String preferredLanguage = resolvePreferredLanguage(recipient);
        String formattedAmount = formatCurrency(preferredLanguage, amount);
        return switch (preferredLanguage) {
            case "en" -> "You received " + formattedAmount + " from " + senderUsername + " from account " + sourceAccountName;
            case "fi" -> "Sait " + formattedAmount + " kayttajalta " + senderUsername + " tililta " + sourceAccountName;
            default -> "Said " + formattedAmount + " kasutajalt " + senderUsername + " kontolt " + sourceAccountName;
        };
    }

    private String localizeRecurringTransactionDueMessage(User recipient, String categoryName, BigDecimal amount, java.time.LocalDate dueDate) {
        String preferredLanguage = resolvePreferredLanguage(recipient);
        String formattedAmount = amount == null ? null : formatCurrency(preferredLanguage, amount);
        String dueDateText = dueDate.toString();
        return switch (preferredLanguage) {
            case "en" -> "Recurring payment due: " + categoryName + amountSuffix(formattedAmount) + " due on " + dueDateText;
            case "fi" -> "Toistuva maksu erääntyy: " + categoryName + amountSuffix(formattedAmount) + " eräpäivä " + dueDateText;
            default -> "Korduv makse tähtaegub: " + categoryName + amountSuffix(formattedAmount) + " tähtaeg " + dueDateText;
        };
    }

    private String localizeAccountSharedMessage(User recipient, String senderUsername, String accountName, AccountUserRole role) {
        String preferredLanguage = resolvePreferredLanguage(recipient);
        String roleText = localizeRole(role, preferredLanguage);
        return switch (preferredLanguage) {
            case "en" -> "Account " + accountName + " was shared by " + senderUsername + " with " + roleText + " access";
            case "fi" -> "Tili " + accountName + " jaettiin käyttäjältä " + senderUsername + " oikeuksilla " + roleText;
            default -> "Konto " + accountName + " jagati kasutajalt " + senderUsername + " õigusega " + roleText;
        };
    }

    private String localizeAccountUnsharedMessage(User recipient, String senderUsername, String accountName) {
        String preferredLanguage = resolvePreferredLanguage(recipient);
        return switch (preferredLanguage) {
            case "en" -> "Access to account " + accountName + " was removed by " + senderUsername;
            case "fi" -> "Käyttöoikeus tilille " + accountName + " poistettiin käyttäjältä " + senderUsername;
            default -> "Ligipääs kontole " + accountName + " eemaldati kasutajalt " + senderUsername;
        };
    }

    private String localizeTransactionActivityMessage(
            User recipient,
            String actorUsername,
            String accountName,
            TransactionType transactionType,
            BigDecimal amount,
            Long transactionId,
            Long relatedAccountId,
            NotificationType notificationType
    ) {
        String preferredLanguage = resolvePreferredLanguage(recipient);
        String formattedAmount = formatCurrency(preferredLanguage, amount);
        String transactionText = localizeTransactionType(transactionType, preferredLanguage);
        String actionText = localizeTransactionAction(notificationType, preferredLanguage);
        return switch (preferredLanguage) {
            case "en" -> actorUsername + " " + actionText + " " + transactionText + " " + formattedAmount + " from " + accountName;
            case "fi" -> actorUsername + " " + actionText + " " + transactionText + " " + formattedAmount + " tililtä " + accountName;
            default -> actorUsername + " " + actionText + " " + transactionText + " " + formattedAmount + " kontolt " + accountName;
        };
    }

    private String localizeTransactionAction(NotificationType notificationType, String language) {
        return switch (notificationType) {
            case TRANSACTION_UPDATED -> switch (language) {
                case "en" -> "updated";
                case "fi" -> "muokkasi";
                default -> "muutis";
            };
            case TRANSACTION_DELETED -> switch (language) {
                case "en" -> "deleted";
                case "fi" -> "poisti";
                default -> "kustutas";
            };
            default -> switch (language) {
                case "en" -> "added";
                case "fi" -> "lisäsi";
                default -> "lisas";
            };
        };
    }

    private String localizeTransactionType(TransactionType transactionType, String language) {
        return switch (transactionType) {
            case INCOME -> switch (language) {
                case "en" -> "income";
                case "fi" -> "tulon";
                default -> "tulu";
            };
            case EXPENSE -> switch (language) {
                case "en" -> "expense";
                case "fi" -> "kulun";
                default -> "kulu";
            };
            default -> switch (language) {
                case "en" -> "transaction";
                case "fi" -> "tapahtuman";
                default -> "tehingu";
            };
        };
    }

    private String localizeRole(AccountUserRole role, String language) {
        return switch (role) {
            case EDITOR -> switch (language) {
                case "en" -> "editor";
                case "fi" -> "muokkaaja";
                default -> "muutja";
            };
            case VIEWER -> switch (language) {
                case "en" -> "viewer";
                case "fi" -> "katselija";
                default -> "vaataja";
            };
            default -> switch (language) {
                case "en" -> "owner";
                case "fi" -> "omistaja";
                default -> "omanik";
            };
        };
    }

    private String amountSuffix(String formattedAmount) {
        return formattedAmount == null ? "" : " (" + formattedAmount + ")";
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
