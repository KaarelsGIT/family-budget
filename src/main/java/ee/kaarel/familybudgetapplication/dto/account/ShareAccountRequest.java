package ee.kaarel.familybudgetapplication.dto.account;

import ee.kaarel.familybudgetapplication.model.AccountUserRole;
import jakarta.validation.constraints.NotNull;

public record ShareAccountRequest(
        @NotNull Long userId,
        @NotNull AccountUserRole role
) {
}
