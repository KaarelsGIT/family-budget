package ee.kaarel.familybudgetapplication.dto.account;

import ee.kaarel.familybudgetapplication.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull AccountType type
) {
}
