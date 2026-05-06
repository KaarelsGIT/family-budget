package ee.kaarel.familybudgetapplication.appConfig;

import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner createDefaultAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (userRepository.existsByRole(Role.ADMIN)) {
                return;
            }
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setRole(Role.ADMIN);
            admin.setFamilyId(null);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setPreferredLanguage("et");
            User savedAdmin = userRepository.save(admin);
            savedAdmin.setFamilyId(savedAdmin.getId());
            userRepository.save(savedAdmin);
        };
    }
}
