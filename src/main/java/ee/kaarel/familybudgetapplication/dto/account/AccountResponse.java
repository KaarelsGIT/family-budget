package ee.kaarel.familybudgetapplication.dto.account;

import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.AccountUserRole;
import ee.kaarel.familybudgetapplication.model.AccountType;
import java.math.BigDecimal;
import java.util.List;

public record AccountResponse(
        Long id,
        String name,
        Long ownerId,
        String ownerUsername,
        Role ownerRole,
        AccountType type,
        boolean isDefault,
        boolean deletionRequested,
        BigDecimal balance,
        AccountUserRole accessRole,
        List<AccountShareResponse> sharedUsers
) {
}
