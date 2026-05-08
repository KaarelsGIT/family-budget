package ee.kaarel.familybudgetapplication.dto.transaction;

import ee.kaarel.familybudgetapplication.model.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTransactionRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,

        @NotNull TransactionType type,

        Long fromAccountId,

        Long toAccountId,

        Long targetUserId,

        Long categoryId,

        @NotNull LocalDate transactionDate,

        @Size(max = 500) String comment,

        Long reminderId,

        // 1. Muudetud 'boolean' -> 'Boolean' (suur täht), et lubada nulli vastuvõtmist
        // 2. Võid lisada loogika, et kui on null, siis käsitletakse kui false
        Boolean useMicroSavings,

        // 3. Multiplier on juba Integer (objekt), mis on hea
        Integer multiplier
) {
    // See kompaktne konstruktor tagab, et isegi kui Angular saadab nulli,
    // on klassi sees väärtused alati olemas (default väärtused).
    public CreateTransactionRequest {
        if (useMicroSavings == null) {
            useMicroSavings = false;
        }
        if (multiplier == null) {
            multiplier = 1;
        }
    }
}