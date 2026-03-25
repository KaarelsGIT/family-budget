package ee.kaarel.familybudgetapplication.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 120) String name
) {
}
