package ee.kaarel.familybudgetapplication.dto.recurring;

import java.math.BigDecimal;

public record RecurringTransactionResponse(
        Long id,
        String name,
        BigDecimal amount,
        Integer dueDay,
        Long categoryId,
        String categoryName,
        Long ownerId,
        String ownerUsername,
        boolean active,
        RecurringTransactionStatusResponse currentMonthStatus
) {
}
