package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.RecurringPayment;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, Long>, JpaSpecificationExecutor<RecurringPayment> {

    List<RecurringPayment> findAllByOwner(User owner);

    Optional<RecurringPayment> findByOwnerAndCategory(User owner, Category category);

    void deleteAllByOwner(User owner);
}
