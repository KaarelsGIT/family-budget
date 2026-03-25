package ee.kaarel.familybudgetapplication.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateUserRequest(
        @NotBlank String username,
        @NotBlank String currentPassword,
        @NotBlank @Size(max = 100) String newUsername,
        @NotBlank @Size(min = 4, max = 100) String newPassword
) {
}
