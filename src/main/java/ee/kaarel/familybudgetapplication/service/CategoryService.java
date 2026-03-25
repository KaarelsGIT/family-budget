package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.category.CategoryResponse;
import ee.kaarel.familybudgetapplication.dto.category.CreateCategoryRequest;
import ee.kaarel.familybudgetapplication.dto.category.UpdateCategoryRequest;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.CategoryRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ee.kaarel.familybudgetapplication.model.TransactionType;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public CategoryService(
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService
    ) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public ListResponse<CategoryResponse> getCategories(Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by("group").ascending().and(Sort.by("name").ascending()));
        Specification<Category> specification = visibleCategories(currentUser);
        Page<Category> page = categoryRepository.findAll(specification, sorted);
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateGroupAccess(currentUser, request.group());

        Category category = new Category();
        apply(category, request.name(), request.type(), request.parentCategoryId(), request.group());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Category category = getCategory(id);
        ensureVisible(currentUser, category);

        String name = request.name() != null ? request.name() : category.getName();
        var type = request.type() != null ? request.type() : category.getType();
        Long parentId = request.parentCategoryId() != null ? request.parentCategoryId() : category.getParentCategory() == null ? null : category.getParentCategory().getId();
        CategoryGroup group = request.group() != null ? request.group() : category.getGroup();
        validateGroupAccess(currentUser, group);
        apply(category, name, type, parentId, group);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        Category category = getCategory(id);
        ensureVisible(currentUser, category);
        if (transactionRepository.existsByCategory(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete category with transactions");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    public void ensureVisible(User currentUser, Category category) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.CHILD && category.getGroup() != CategoryGroup.CHILD) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Children cannot access this category");
        }
    }

    private void validateGroupAccess(User currentUser, CategoryGroup group) {
        if (currentUser.getRole() == Role.CHILD && group != CategoryGroup.CHILD) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Children can only manage CHILD categories");
        }
        if (currentUser.getRole() == Role.PARENT && group != CategoryGroup.FAMILY) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Parents can only manage FAMILY categories");
        }
    }

    private void apply(Category category, String name, TransactionType type, Long parentCategoryId, CategoryGroup group) {
        category.setName(name);
        category.setType(type);
        category.setGroup(group);
        if (parentCategoryId == null) {
            category.setParentCategory(null);
            return;
        }
        Category parentCategory = getCategory(parentCategoryId);
        if (parentCategory.getId().equals(category.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category cannot be its own parent");
        }
        if (parentCategory.getGroup() != group || parentCategory.getType() != type) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Parent category must have the same group and type");
        }
        category.setParentCategory(parentCategory);
    }

    private Specification<Category> visibleCategories(User currentUser) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("parentCategory", JoinType.LEFT);
                query.distinct(true);
            }
            if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.PARENT) {
                return cb.conjunction();
            }
            return cb.equal(root.get("group"), CategoryGroup.CHILD);
        };
    }

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getParentCategory() == null ? null : category.getParentCategory().getId(),
                category.getParentCategory() == null ? null : category.getParentCategory().getName(),
                category.getGroup()
        );
    }
}
