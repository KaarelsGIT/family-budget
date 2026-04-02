package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.RecurringTransaction;
import ee.kaarel.familybudgetapplication.model.ReminderStatus;
import ee.kaarel.familybudgetapplication.model.TransactionReminder;
import ee.kaarel.familybudgetapplication.model.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionReminderRepository extends JpaRepository<TransactionReminder, Long> {

    List<TransactionReminder> findAllByUserAndStatusOrderByDueDateAsc(User user, ReminderStatus status);

    List<TransactionReminder> findAllByStatusOrderByDueDateAsc(ReminderStatus status);

    Optional<TransactionReminder> findByRecurringTransactionAndDueDate(RecurringTransaction recurringTransaction, LocalDate dueDate);

    boolean existsByRecurringTransactionAndDueDate(RecurringTransaction recurringTransaction, LocalDate dueDate);
}
