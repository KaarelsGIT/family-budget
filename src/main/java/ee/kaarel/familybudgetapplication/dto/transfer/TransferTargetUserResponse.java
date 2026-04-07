package ee.kaarel.familybudgetapplication.dto.transfer;

import ee.kaarel.familybudgetapplication.dto.account.AccountResponse;
import ee.kaarel.familybudgetapplication.model.Role;
import java.util.List;

public record TransferTargetUserResponse(
        Long userId,
        String username,
        Role role,
        List<AccountResponse> accounts
) {
}
