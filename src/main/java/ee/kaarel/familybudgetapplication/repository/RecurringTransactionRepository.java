package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.RecurringTransaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    List<RecurringTransaction> findAllByActiveTrueAndNextDueDateLessThanEqual(LocalDate today);

    List<RecurringTransaction> findAllByActiveTrueAndNextDueDate(LocalDate nextDueDate);
}
