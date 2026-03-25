package ee.kaarel.familybudgetapplication.appConfig;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner createDefaultAdmin(UserRepository userRepository, AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByRole(Role.ADMIN)) {
                return;
            }
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            User savedAdmin = userRepository.save(admin);

            Account account = new Account();
            account.setName("admin MAIN");
            account.setOwner(savedAdmin);
            account.setType(AccountType.MAIN);
            account.setDefault(true);
            account.setDeletionRequested(false);
            accountRepository.save(account);
        };
    }
}
