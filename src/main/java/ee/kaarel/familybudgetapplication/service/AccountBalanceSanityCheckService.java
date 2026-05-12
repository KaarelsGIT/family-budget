package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.dto.account.AccountBalanceSanityCheckResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountBalanceSanityCheckService {

    private final TransactionRepository transactionRepository;

    public AccountBalanceSanityCheckService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public AccountBalanceSanityCheckResponse check(Account account, BigDecimal displayedBalance) {
        BigDecimal databaseBalance = transactionRepository.calculateAccurateBalance(account.getId());
        List<TransactionRepository.BalanceAuditRow> auditRows = transactionRepository.findBalanceAuditRows(account.getId());

        BigDecimal running = safe(account.getInitialBalance());
        BigDecimal divergenceExpected = null;
        BigDecimal divergenceObserved = null;
        Long divergenceEventId = null;
        String divergenceEventKind = null;
        OffsetDateTime divergenceEventCreatedAt = null;

        auditRows.sort(Comparator
                .comparing(TransactionRepository.BalanceAuditRow::getEventCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TransactionRepository.BalanceAuditRow::getEventId, Comparator.nullsLast(Comparator.naturalOrder())));

        for (TransactionRepository.BalanceAuditRow row : auditRows) {
            running = running.add(safe(row.getDelta()));

            BigDecimal databasePrefixBalance = transactionRepository.calculateBalanceUpTo(
                    account.getId(),
                    row.getEventCreatedAt(),
                    row.getEventId()
            );

            if (divergenceEventId == null && databasePrefixBalance.compareTo(running) != 0) {
                divergenceEventId = row.getEventId();
                divergenceEventKind = row.getEventKind();
                divergenceEventCreatedAt = row.getEventCreatedAt();
                divergenceExpected = databasePrefixBalance;
                divergenceObserved = running;
            }
        }

        BigDecimal recalculatedBalance = running;
        boolean matches = databaseBalance.compareTo(recalculatedBalance) == 0
                && (displayedBalance == null || displayedBalance.compareTo(recalculatedBalance) == 0);

        return new AccountBalanceSanityCheckResponse(
                account.getId(),
                databaseBalance,
                recalculatedBalance,
                matches,
                divergenceEventId,
                divergenceEventKind,
                divergenceEventCreatedAt,
                divergenceExpected,
                divergenceObserved
        );
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
