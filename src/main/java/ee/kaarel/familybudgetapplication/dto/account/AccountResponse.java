package ee.kaarel.familybudgetapplication.dto.account;

import ee.kaarel.familybudgetapplication.model.AccountType;
import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String name,
        Long ownerId,
        String ownerUsername,
        AccountType type,
        boolean isDefault,
        boolean deletionRequested,
        BigDecimal balance
) {
}
