package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.Notification;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUser(User user, Pageable pageable);

    boolean existsByUserAndTypeAndMessage(User user, NotificationType type, String message);
}
