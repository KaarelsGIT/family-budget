package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.User;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    List<Account> findAllByOwner(User owner);

    Optional<Account> findByOwnerAndTypeAndIsDefaultTrue(User owner, AccountType type);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findById(Long id);

    @EntityGraph(attributePaths = "manualAdjustments")
    Optional<Account> findWithManualAdjustmentsById(Long id);

    void deleteAllByOwner(User owner);
}
