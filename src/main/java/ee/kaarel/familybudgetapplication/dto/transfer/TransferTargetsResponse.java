package ee.kaarel.familybudgetapplication.dto.transfer;

import ee.kaarel.familybudgetapplication.dto.account.AccountResponse;
import java.util.List;

public record TransferTargetsResponse(
        List<AccountResponse> myAccounts,
        List<TransferTargetUserResponse> otherUsers
) {
}
