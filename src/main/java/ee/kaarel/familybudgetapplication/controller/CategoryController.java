package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.category.CreateCategoryRequest;
import ee.kaarel.familybudgetapplication.dto.category.UpdateCategoryRequest;
import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ListResponse<?> getCategories(Pageable pageable) {
        return categoryService.getCategories(pageable);
    }

    @PostMapping
    public ApiResponse<?> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return new ApiResponse<>(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> updateCategory(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return new ApiResponse<>(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return new ApiResponse<>("Category deleted");
    }
}
