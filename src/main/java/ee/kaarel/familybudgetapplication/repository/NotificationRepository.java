package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.Notification;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.User;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUser(User user, Pageable pageable);

    long countByUserAndIsReadFalse(User user);

    List<Notification> findAllByUserAndIsReadFalse(User user);

    boolean existsByUserAndTypeAndMessage(User user, NotificationType type, String message);

    boolean existsByUserAndTypeAndRelatedReminderId(User user, NotificationType type, Long relatedReminderId);

    void deleteAllByUser(User user);

    void deleteAllByCreatedAtBefore(OffsetDateTime cutoff);
}
