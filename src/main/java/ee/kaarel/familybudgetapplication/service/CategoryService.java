package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.category.CategoryResponse;
import ee.kaarel.familybudgetapplication.dto.category.CreateCategoryRequest;
import ee.kaarel.familybudgetapplication.dto.category.UpdateCategoryRequest;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.RecurringPayment;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.CategoryRepository;
import ee.kaarel.familybudgetapplication.repository.RecurringPaymentRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
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
    private final RecurringPaymentRepository recurringPaymentRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public CategoryService(
            CategoryRepository categoryRepository,
            RecurringPaymentRepository recurringPaymentRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService
    ) {
        this.categoryRepository = categoryRepository;
        this.recurringPaymentRepository = recurringPaymentRepository;
        this.userRepository = userRepository;
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
        apply(
                category,
                currentUser,
                request.name(),
                request.type(),
                request.parentCategoryId(),
                request.group(),
                request.isRecurring(),
                request.dueDayOfMonth(),
                request.recurringAmount()
        );
        Category saved = categoryRepository.save(category);
        syncRecurringPayment(currentUser, saved, saved.isRecurring(), request.recurringAmount());
        return toResponse(saved);
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
        Boolean isRecurring = request.isRecurring() != null ? request.isRecurring() : category.isRecurring();
        Integer dueDayOfMonth;
        if (request.isRecurring() != null && !request.isRecurring()) {
            dueDayOfMonth = null;
        } else if (request.isRecurring() != null && request.dueDayOfMonth() == null && category.isRecurring()) {
            dueDayOfMonth = category.getDueDayOfMonth();
        } else if (request.isRecurring() != null && request.isRecurring()) {
            dueDayOfMonth = request.dueDayOfMonth() != null ? request.dueDayOfMonth() : category.getDueDayOfMonth();
        } else {
            dueDayOfMonth = request.dueDayOfMonth() != null ? request.dueDayOfMonth() : category.getDueDayOfMonth();
        }
        validateGroupAccess(currentUser, group);
        apply(category, currentUser, name, type, parentId, group, isRecurring, dueDayOfMonth, request.recurringAmount());
        Category saved = categoryRepository.save(category);
        syncRecurringPayment(currentUser, saved, saved.isRecurring(), request.recurringAmount());
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
        removeRecurringPayment(category);
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
            CategoryGroup group,
            Boolean isRecurring,
            Integer dueDayOfMonth,
            BigDecimal recurringAmount
    ) {
        String normalizedName = normalizeName(name);
        TransactionType resolvedType = type;
        boolean recurring = Boolean.TRUE.equals(isRecurring);
        Integer resolvedDueDayOfMonth = recurring ? dueDayOfMonth : null;

        validateCategoryShape(resolvedType, parentCategoryId, recurring, resolvedDueDayOfMonth);
        ensureUniqueCategory(currentUser, category.getId(), normalizedName, parentCategoryId);

        category.setUserId(currentUser.getId());
        category.setName(normalizedName);
        category.setType(resolvedType);
        category.setGroup(group);
        category.setRecurring(recurring);
        category.setDueDayOfMonth(resolvedDueDayOfMonth);
        if (parentCategoryId == null) {
            category.setParentCategory(null);
            return;
        }
        Category parentCategory = getCategory(parentCategoryId);
        if (parentCategory.getId().equals(category.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category cannot be its own parent");
        }
        if (parentCategory.getGroup() != group || parentCategory.getType() != resolvedType) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Parent category must have the same group and type");
        }
        category.setParentCategory(parentCategory);
    }

    private void validateCategoryShape(TransactionType type, Long parentCategoryId, boolean recurring, Integer dueDayOfMonth) {
        if (type == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category type is required");
        }
        if (type == TransactionType.TRANSFER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category type must be INCOME or EXPENSE");
        }
        if (recurring) {
            if (type != TransactionType.EXPENSE || parentCategoryId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Only expense subcategories can be recurring");
            }
            if (dueDayOfMonth == null || dueDayOfMonth < 1 || dueDayOfMonth > 31) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring categories require dueDayOfMonth between 1 and 31");
            }
        } else if (dueDayOfMonth != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "dueDayOfMonth is only allowed for recurring categories");
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
        BigDecimal recurringAmount = resolveRecurringAmount(category);
        return new CategoryResponse(
                category.getId(),
                category.getUserId(),
                category.getName(),
                category.getType(),
                category.getParentCategory() == null ? null : category.getParentCategory().getId(),
                category.getParentCategory() == null ? null : category.getParentCategory().getName(),
                category.getGroup(),
                category.isRecurring(),
                category.getDueDayOfMonth(),
                recurringAmount
        );
    }

    private void syncRecurringPayment(User currentUser, Category category, boolean recurring, BigDecimal recurringAmount) {
        if (category.getType() != TransactionType.EXPENSE || !recurring || category.getParentCategory() == null) {
            removeRecurringPayment(category);
            return;
        }

        User owner = userRepository.findById(category.getUserId()).orElse(currentUser);
        Optional<RecurringPayment> existing = recurringPaymentRepository.findByOwnerAndCategory(owner, category);
        BigDecimal resolvedAmount = recurringAmount != null ? recurringAmount : existing.map(RecurringPayment::getAmount).orElse(null);
        if (resolvedAmount == null || resolvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring categories require a positive amount");
        }

        RecurringPayment recurringPayment = existing.orElseGet(RecurringPayment::new);
        recurringPayment.setOwner(owner);
        recurringPayment.setCategory(category);
        recurringPayment.setName(category.getName());
        recurringPayment.setAmount(resolvedAmount);
        recurringPayment.setDueDay(category.getDueDayOfMonth());
        recurringPayment.setActive(true);
        recurringPaymentRepository.save(recurringPayment);
    }

    private void removeRecurringPayment(Category category) {
        User owner = userRepository.findById(category.getUserId()).orElse(null);
        if (owner == null) {
            return;
        }
        recurringPaymentRepository.findByOwnerAndCategory(owner, category)
                .ifPresent(recurringPaymentRepository::delete);
    }

    private BigDecimal resolveRecurringAmount(Category category) {
        if (!category.isRecurring() || category.getParentCategory() == null || category.getType() != TransactionType.EXPENSE) {
            return null;
        }
        User owner = userRepository.findById(category.getUserId()).orElse(null);
        if (owner == null) {
            return null;
        }
        return recurringPaymentRepository.findByOwnerAndCategory(owner, category)
                .map(RecurringPayment::getAmount)
                .orElse(null);
    }
}
