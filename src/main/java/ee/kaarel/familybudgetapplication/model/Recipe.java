package ee.kaarel.familybudgetapplication.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "recipes")
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodRecipeType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "base_servings", nullable = false)
    private Integer baseServings;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cost;

    @OneToMany(mappedBy = "recipe", orphanRemoval = true, cascade = jakarta.persistence.CascadeType.ALL)
    private List<Ingredient> ingredients = new ArrayList<>();
}
