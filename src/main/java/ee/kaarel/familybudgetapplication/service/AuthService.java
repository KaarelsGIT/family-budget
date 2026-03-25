package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.auth.ActivateUserRequest;
import ee.kaarel.familybudgetapplication.dto.auth.LoginRequest;
import ee.kaarel.familybudgetapplication.dto.auth.LoginResponse;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return toResponse(user);
    }

    @Transactional
    public LoginResponse activate(ActivateUserRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (!user.getUsername().equals(request.newUsername()) && userRepository.existsByUsername(request.newUsername())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        user.setUsername(request.newUsername());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setStatus(UserStatus.ACTIVE);
        return toResponse(userRepository.save(user));
    }

    private LoginResponse toResponse(User user) {
        return new LoginResponse(user.getId(), user.getUsername(), user.getRole(), user.getStatus(), "BASIC");
    }
}
