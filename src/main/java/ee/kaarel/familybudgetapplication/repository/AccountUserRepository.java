package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountUser;
import ee.kaarel.familybudgetapplication.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AccountUserRepository extends JpaRepository<AccountUser, Long> {

    Optional<AccountUser> findByAccountAndUser(Account account, User user);

    List<AccountUser> findAllByUser(User user);

    @EntityGraph(attributePaths = "user")
    List<AccountUser> findAllByAccount(Account account);

    void deleteAllByUser(User user);
}
