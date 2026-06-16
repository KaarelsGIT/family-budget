package ee.kaarel.familybudgetapplication.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "family_savings_selections")
public class FamilySavingsSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false, unique = true)
    private Long familyId;

    @Column(name = "selected_account_ids", nullable = false, length = 2000)
    private String selectedAccountIds = "";

    @Column(name = "target_amount")
    private BigDecimal targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;
}
