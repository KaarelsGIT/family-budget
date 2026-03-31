package ee.kaarel.familybudgetapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FamilyBudgetApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyBudgetApplication.class, args);
    }

}
