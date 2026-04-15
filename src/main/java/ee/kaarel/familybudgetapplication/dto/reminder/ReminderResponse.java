package ee.kaarel.familybudgetapplication.dto.reminder;

import ee.kaarel.familybudgetapplication.model.ReminderStatus;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReminderResponse(
        Long id,
        Long recurringTransactionId,
        Long transactionId,
        Long userId,
        String username,
        Long categoryId,
        String categoryName,
        Long accountId,
        String accountName,
        BigDecimal amount,
        String comment,
        LocalDate dueDate,
        ReminderStatus status,
        TransactionType transactionType,
        boolean urgent
) {
}
