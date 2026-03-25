package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.RecurringPayment;
import ee.kaarel.familybudgetapplication.model.RecurringPaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringPaymentStatusRepository extends JpaRepository<RecurringPaymentStatus, Long> {

    Optional<RecurringPaymentStatus> findByRecurringPaymentAndYearAndMonth(RecurringPayment recurringPayment, Integer year, Integer month);

    List<RecurringPaymentStatus> findAllByRecurringPaymentIn(List<RecurringPayment> recurringPayments);
}
