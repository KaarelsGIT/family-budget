package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.account.CreateAccountRequest;
import ee.kaarel.familybudgetapplication.dto.account.AdjustBalanceRequest;
import ee.kaarel.familybudgetapplication.dto.account.UpdateAccountRequest;
import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.service.AccountService;
import ee.kaarel.familybudgetapplication.service.CurrentUserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("isAuthenticated()")
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;
    private final CurrentUserService currentUserService;

    public AccountController(AccountService accountService, CurrentUserService currentUserService) {
        this.accountService = accountService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ListResponse<?> getAccounts(Pageable pageable) {
        logCurrentUser("GET /api/accounts");
        return accountService.getAccounts(pageable);
    }

    @PostMapping
    public ApiResponse<?> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        logCurrentUser("POST /api/accounts");
        return new ApiResponse<>(accountService.createAccount(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> updateAccount(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) {
        logCurrentUser("PUT /api/accounts/" + id);
        return new ApiResponse<>(accountService.updateAccount(id, request));
    }

    @PatchMapping("/{id}/adjust-balance")
    public ApiResponse<?> adjustBalance(@PathVariable Long id, @Valid @RequestBody AdjustBalanceRequest request) {
        logCurrentUser("PATCH /api/accounts/" + id + "/adjust-balance");
        return new ApiResponse<>(accountService.adjustBalance(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> deleteAccount(@PathVariable Long id) {
        logCurrentUser("DELETE /api/accounts/" + id);
        accountService.deleteAccount(id);
        return new ApiResponse<>("Account deletion processed");
    }

    private void logCurrentUser(String action) {
        var user = currentUserService.getCurrentUser();
        log.info("{} user={} role={}", action, user.getUsername(), user.getRole());
    }
}
