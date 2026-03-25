package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.RecurringPayment;
import ee.kaarel.familybudgetapplication.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, Long>, JpaSpecificationExecutor<RecurringPayment> {

    List<RecurringPayment> findAllByOwner(User owner);

    void deleteAllByOwner(User owner);
}
