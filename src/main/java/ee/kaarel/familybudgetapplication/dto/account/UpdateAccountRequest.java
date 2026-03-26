package ee.kaarel.familybudgetapplication.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 120) String name
) {
}
