package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountBalanceAdjustment;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountBalanceAdjustmentRepository extends JpaRepository<AccountBalanceAdjustment, Long> {

    @Query("""
            select coalesce(sum(adjustment.amount), 0)
            from AccountBalanceAdjustment adjustment
            where adjustment.account = :account
            """)
    BigDecimal calculateAdjustmentBalance(@Param("account") Account account);
}
