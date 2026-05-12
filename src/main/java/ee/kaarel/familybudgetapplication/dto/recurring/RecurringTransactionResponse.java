package ee.kaarel.familybudgetapplication.dto.recurring;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public record RecurringTransactionResponse(
        Long id,
        String name,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
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
