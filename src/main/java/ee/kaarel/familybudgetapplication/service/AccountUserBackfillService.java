package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountUserRole;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.AccountUserRepository;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountUserBackfillService {

    private final AccountRepository accountRepository;
    private final AccountUserRepository accountUserRepository;
    private final AccountService accountService;

    public AccountUserBackfillService(
            AccountRepository accountRepository,
            AccountUserRepository accountUserRepository,
            AccountService accountService
    ) {
        this.accountRepository = accountRepository;
        this.accountUserRepository = accountUserRepository;
        this.accountService = accountService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillAccountUsers() {
        List<Account> accounts = accountRepository.findAll().stream()
                .filter((account) -> accountUserRepository.findByAccountAndUser(account, account.getOwner()).isEmpty())
                .toList();

        if (accounts.isEmpty()) {
            return;
        }

        accounts.forEach((account) -> accountService.grantAccountAccess(account, account.getOwner(), AccountUserRole.OWNER));
    }
}
