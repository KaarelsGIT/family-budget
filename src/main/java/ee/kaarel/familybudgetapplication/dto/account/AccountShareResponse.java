package ee.kaarel.familybudgetapplication.dto.account;

import ee.kaarel.familybudgetapplication.model.AccountUserRole;

public record AccountShareResponse(
        Long userId,
        String username,
        AccountUserRole role
) {
}
