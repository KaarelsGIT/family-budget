package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountBalanceMigrationService {

    private final AccountRepository accountRepository;

    public AccountBalanceMigrationService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillInitialBalances() {
        List<Account> accounts = accountRepository.findAll().stream()
                .filter((account) -> account.getInitialBalance() == null)
                .toList();

        if (accounts.isEmpty()) {
            return;
        }

        accounts.forEach((account) -> account.setInitialBalance(BigDecimal.ZERO));
        accountRepository.saveAll(accounts);
    }
}
