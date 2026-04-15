package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.category.CategoryResponse;
import ee.kaarel.familybudgetapplication.dto.category.CreateCategoryRequest;
import ee.kaarel.familybudgetapplication.dto.category.UpdateCategoryRequest;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.CategoryRepository;
import ee.kaarel.familybudgetapplication.repository.RecurringTransactionRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final CurrentUserService currentUserService;

    public CategoryService(
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            RecurringTransactionRepository recurringTransactionRepository,
            CurrentUserService currentUserService
    ) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.recurringTransactionRepository = recurringTransactionRepository;
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

        Category category = new Category();
        apply(
                category,
                currentUser,
                request.name(),
                request.type(),
                request.parentCategoryId(),
                request.group()
        );
        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Category category = getCategory(id);
        ensureVisible(currentUser, category);

        String name = request.name() != null ? request.name() : category.getName();
        TransactionType type = request.type() != null ? request.type() : category.getType();
        Long parentId = request.parentCategoryId() != null ? request.parentCategoryId() : category.getParentCategory() == null ? null : category.getParentCategory().getId();
        CategoryGroup group = request.group() != null ? request.group() : category.getGroup();

        apply(category, currentUser, name, type, parentId, group);
        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional
    public void deleteCategory(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        Category category = getCategory(id);
        ensureVisible(currentUser, category);
        if (categoryRepository.existsByParentCategory(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete category with subcategories");
        }
        if (transactionRepository.existsByCategory(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete category with transactions");
        }
        if (recurringTransactionRepository.existsByCategory(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete category with recurring transactions");
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

    private void apply(
            Category category,
            User currentUser,
            String name,
            TransactionType type,
            Long parentCategoryId,
            CategoryGroup group
    ) {
        String normalizedName = normalizeName(name);
        CategoryGroup effectiveGroup = group;
        TransactionType effectiveType = type;
        Category parentCategory = null;

        if (parentCategoryId != null) {
            parentCategory = getCategory(parentCategoryId);
            ensureVisible(currentUser, parentCategory);
            if (parentCategory.getId().equals(category.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Category cannot be its own parent");
            }
            effectiveGroup = parentCategory.getGroup();
            effectiveType = parentCategory.getType();
        } else {
            validateCategoryShape(effectiveType);
            validateGroupAccess(currentUser, effectiveGroup);
        }

        ensureUniqueCategory(currentUser, category.getId(), normalizedName, parentCategoryId);

        category.setUserId(currentUser.getId());
        category.setName(normalizedName);
        category.setType(effectiveType);
        category.setGroup(effectiveGroup);
        category.setParentCategory(parentCategory);
    }

    private void validateCategoryShape(TransactionType type) {
        if (type == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category type is required");
        }
        if (type == TransactionType.TRANSFER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category type must be INCOME or EXPENSE");
        }
    }

    private void ensureUniqueCategory(User currentUser, Long categoryId, String normalizedName, Long parentCategoryId) {
        Specification<Category> specification = (root, query, cb) -> {
            var predicates = cb.conjunction();
            predicates = cb.and(predicates, cb.equal(root.get("userId"), currentUser.getId()));
            predicates = cb.and(predicates, cb.equal(cb.lower(root.get("name")), normalizedName.toLowerCase(Locale.ROOT)));
            predicates = parentCategoryId == null
                    ? cb.and(predicates, cb.isNull(root.get("parentCategory")))
                    : cb.and(predicates, cb.equal(root.get("parentCategory").get("id"), parentCategoryId));
            if (categoryId != null) {
                predicates = cb.and(predicates, cb.notEqual(root.get("id"), categoryId));
            }
            return predicates;
        };

        if (categoryRepository.count(specification) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Category already exists");
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category name is required");
        }
        return normalized;
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
                category.getUserId(),
                category.getName(),
                category.getType(),
                category.getParentCategory() == null ? null : category.getParentCategory().getId(),
                category.getParentCategory() == null ? null : category.getParentCategory().getName(),
                category.getGroup(),
                false,
                null,
                null
        );
    }
}
