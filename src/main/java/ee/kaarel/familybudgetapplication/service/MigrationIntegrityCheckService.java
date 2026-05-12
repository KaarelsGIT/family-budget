package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationIntegrityCheckService {

    private static final Logger log = LoggerFactory.getLogger(MigrationIntegrityCheckService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public MigrationIntegrityCheckService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void runOnceOnStartup() {
        accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getId))
                .forEach(this::auditAccount);
    }

    private void auditAccount(Account account) {
        BigDecimal expected = transactionRepository.calculateAccurateBalance(account.getId());
        BigDecimal recalculated = recalculateFromHistory(account);
        BigDecimal difference = expected.subtract(recalculated);

        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            log.warn(
                    "Balance mismatch detected for account {} ({}). expected={}, recalculated={}, diff={}",
                    account.getId(),
                    account.getName(),
                    expected,
                    recalculated,
                    difference
            );
        }
    }

    private BigDecimal recalculateFromHistory(Account account) {
        BigDecimal running = safe(account.getInitialBalance());
        for (TransactionRepository.BalanceAuditRow row : transactionRepository.findBalanceAuditRows(account.getId())) {
            running = running.add(safe(row.getDelta()));
        }
        return running;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
