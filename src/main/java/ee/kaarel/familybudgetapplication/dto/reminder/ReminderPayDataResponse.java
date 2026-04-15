package ee.kaarel.familybudgetapplication.dto.reminder;

import ee.kaarel.familybudgetapplication.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReminderPayDataResponse(
        Long reminderId,
        Long recurringTransactionId,
        TransactionType transactionType,
        BigDecimal amount,
        Long categoryId,
        String categoryName,
        Long accountId,
        String accountName,
        String description,
        LocalDate transactionDate
) {
}
