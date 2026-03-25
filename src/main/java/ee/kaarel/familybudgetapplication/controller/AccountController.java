package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.account.CreateAccountRequest;
import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ListResponse<?> getAccounts(Pageable pageable) {
        return accountService.getAccounts(pageable);
    }

    @PostMapping
    public ApiResponse<?> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return new ApiResponse<>(accountService.createSavingsAccount(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return new ApiResponse<>("Account deletion processed");
    }
}
