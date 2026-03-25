package ee.kaarel.familybudgetapplication.dto.user;

import ee.kaarel.familybudgetapplication.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 4, max = 100) String password,
        @NotNull Role role
) {
}
