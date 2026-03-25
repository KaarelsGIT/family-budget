package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.user.CreateUserRequest;
import ee.kaarel.familybudgetapplication.dto.user.UpdateUserRequest;
import ee.kaarel.familybudgetapplication.dto.user.UserResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public UserService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can create users");
        }
        if (request.role() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admin user can only be created on startup");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setStatus(UserStatus.PENDING);
        User savedUser = userRepository.save(user);

        Account mainAccount = new Account();
        mainAccount.setName(savedUser.getUsername() + " MAIN");
        mainAccount.setOwner(savedUser);
        mainAccount.setType(AccountType.MAIN);
        mainAccount.setDefault(true);
        mainAccount.setDeletionRequested(false);
        accountRepository.save(mainAccount);
        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        boolean self = currentUser.getId().equals(id);
        boolean admin = currentUser.getRole() == Role.ADMIN;
        if (!self && !admin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot update this user");
        }

        if (request.username() != null && !request.username().equals(targetUser.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Username already exists");
            }
            targetUser.setUsername(request.username());
        }
        if (request.password() != null) {
            targetUser.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            if (!admin) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can change roles");
            }
            if (targetUser.getRole() == Role.ADMIN || request.role() == Role.ADMIN) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Admin role changes are not supported");
            }
            targetUser.setRole(request.role());
        }
        if (targetUser.getStatus() == UserStatus.PENDING) {
            targetUser.setStatus(UserStatus.ACTIVE);
        }
        return toResponse(userRepository.save(targetUser));
    }

    @Transactional(readOnly = true)
    public User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getStatus());
    }
}
