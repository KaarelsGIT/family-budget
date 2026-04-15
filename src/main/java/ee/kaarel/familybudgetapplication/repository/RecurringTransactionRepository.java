package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.RecurringTransaction;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.User;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long>, JpaSpecificationExecutor<RecurringTransaction> {

    List<RecurringTransaction> findAllByActiveTrueAndNextDueDateLessThanEqual(LocalDate today);

    List<RecurringTransaction> findAllByActiveTrueAndNextDueDate(LocalDate nextDueDate);

    List<RecurringTransaction> findAllByActiveTrue();

    List<RecurringTransaction> findAllByUserOrderByNextDueDateAsc(User user);

    boolean existsByCategory(Category category);
}
