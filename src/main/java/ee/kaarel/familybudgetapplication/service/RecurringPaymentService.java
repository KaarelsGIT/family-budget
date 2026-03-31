package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.CreateRecurringPaymentRequest;
import ee.kaarel.familybudgetapplication.dto.recurring.RecurringPaymentResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.RecurringPaymentStatusResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.UpdateRecurringPaymentRequest;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.RecurringPayment;
import ee.kaarel.familybudgetapplication.model.RecurringPaymentStatus;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.CategoryRepository;
import ee.kaarel.familybudgetapplication.repository.RecurringPaymentRepository;
import ee.kaarel.familybudgetapplication.repository.RecurringPaymentStatusRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class RecurringPaymentService {

    private final RecurringPaymentRepository recurringPaymentRepository;
    private final RecurringPaymentStatusRepository recurringPaymentStatusRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public RecurringPaymentService(
            RecurringPaymentRepository recurringPaymentRepository,
            RecurringPaymentStatusRepository recurringPaymentStatusRepository,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService,
            CategoryService categoryService,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.recurringPaymentRepository = recurringPaymentRepository;
        this.recurringPaymentStatusRepository = recurringPaymentStatusRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.categoryService = categoryService;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ListResponse<RecurringPaymentResponse> getRecurringPayments(Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        ensureCurrentMonthStatuses(currentUser);
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by("name").ascending());
        Page<RecurringPayment> page = recurringPaymentRepository.findAll(visibleRecurring(currentUser), sorted);
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional
    public RecurringPaymentResponse create(CreateRecurringPaymentRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Category category = categoryService.getCategory(request.categoryId());
        categoryService.ensureVisible(currentUser, category);

        RecurringPayment recurringPayment = new RecurringPayment();
        recurringPayment.setName(request.name());
        recurringPayment.setAmount(request.amount());
        recurringPayment.setDueDay(request.dueDay());
        recurringPayment.setCategory(category);
        recurringPayment.setOwner(currentUser);
        recurringPayment.setActive(request.active() == null || request.active());
        RecurringPayment saved = recurringPaymentRepository.save(recurringPayment);
        ensureStatus(saved, LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        return toResponse(saved);
    }

    @Transactional
    public RecurringPaymentResponse update(Long id, UpdateRecurringPaymentRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        RecurringPayment recurringPayment = getRecurringPayment(id);
        ensureAccess(currentUser, recurringPayment);

        if (request.name() != null) {
            recurringPayment.setName(request.name());
        }
        if (request.amount() != null) {
            recurringPayment.setAmount(request.amount());
        }
        if (request.dueDay() != null) {
            recurringPayment.setDueDay(request.dueDay());
        }
        if (request.active() != null) {
            recurringPayment.setActive(request.active());
        }
        if (request.categoryId() != null) {
            Category category = categoryService.getCategory(request.categoryId());
            categoryService.ensureVisible(currentUser, category);
            recurringPayment.setCategory(category);
        }
        RecurringPayment saved = recurringPaymentRepository.save(recurringPayment);
        ensureStatus(saved, LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        RecurringPayment recurringPayment = getRecurringPayment(id);
        ensureAccess(currentUser, recurringPayment);
        recurringPaymentRepository.delete(recurringPayment);
    }

    @Transactional
    public void markRecurringAsPaidByTransaction(Category category, User owner, int year, int month) {
        if (category == null) {
            return;
        }
        List<RecurringPayment> recurringPayments = recurringPaymentRepository.findAllByOwner(owner).stream()
                .filter(RecurringPayment::isActive)
                .filter(payment -> payment.getCategory().getId().equals(category.getId()))
                .toList();
        recurringPayments.forEach(payment -> {
            RecurringPaymentStatus status = ensureStatus(payment, year, month);
            status.setPaid(true);
            recurringPaymentStatusRepository.save(status);
        });
    }

    @Transactional
    public void ensureCurrentMonthStatuses(User currentUser) {
        LocalDate now = LocalDate.now();
        List<RecurringPayment> visible = recurringPaymentRepository.findAll(visibleRecurring(currentUser));
        if (visible.isEmpty()) {
            return;
        }

        Set<Long> categoryIds = visible.stream().map(payment -> payment.getCategory().getId()).collect(Collectors.toSet());
        Set<Long> paidCategoryIds = transactionRepository.findPaidCategoryIdsForMonth(categoryIds, now.getYear(), now.getMonthValue())
                .stream()
                .collect(Collectors.toSet());

        visible.stream()
                .filter(RecurringPayment::isActive)
                .forEach(payment -> {
                    RecurringPaymentStatus status = ensureStatus(payment, now.getYear(), now.getMonthValue());
                    if (paidCategoryIds.contains(payment.getCategory().getId())) {
                        status.setPaid(true);
                        recurringPaymentStatusRepository.save(status);
                    }
                    if (!status.isPaid() && now.getDayOfMonth() > payment.getDueDay()) {
                        String message = "Recurring payment '" + payment.getName() + "' is unpaid for " + now.getMonth() + " " + now.getYear();
                        notificationService.createNotificationIfAbsent(payment.getOwner(), NotificationType.RECURRING_PAYMENT_UNPAID, message);
                    }
                });
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void notifyUpcomingRecurringCategories() {
        LocalDate today = LocalDate.now();
        LocalDate dueDate = today.plusDays(1);

        List<Category> recurringCategories = categoryRepository.findAll((root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("parentCategory", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.and(
                    cb.equal(root.get("type"), ee.kaarel.familybudgetapplication.model.TransactionType.EXPENSE),
                    cb.isTrue(root.get("recurring")),
                    cb.isNotNull(root.get("parentCategory")),
                    cb.equal(root.get("dueDayOfMonth"), dueDate.getDayOfMonth())
            );
        });

        if (recurringCategories.isEmpty()) {
            return;
        }

        Set<Long> categoryIds = recurringCategories.stream().map(Category::getId).collect(Collectors.toSet());
        Set<Long> paidCategoryIds = transactionRepository.findPaidCategoryIdsByTransactionDateForMonth(
                        categoryIds,
                        today.getYear(),
                        today.getMonthValue()
                )
                .stream()
                .collect(Collectors.toSet());

        recurringCategories.stream()
                .filter(category -> !paidCategoryIds.contains(category.getId()))
                .forEach(category -> {
                    User owner = userRepository.findById(category.getUserId()).orElse(null);
                    if (owner == null) {
                        return;
                    }
                    String message = "Recurring category '" + category.getName() + "' is due on " + dueDate;
                    notificationService.createNotificationIfAbsent(owner, NotificationType.RECURRING_PAYMENT_UNPAID, message, "PAY", category.getId());
                });
    }

    @Transactional(readOnly = true)
    public RecurringPayment getRecurringPayment(Long id) {
        return recurringPaymentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring payment not found"));
    }

    private void ensureAccess(User currentUser, RecurringPayment recurringPayment) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (!recurringPayment.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot access this recurring payment");
        }
    }

    private Specification<RecurringPayment> visibleRecurring(User currentUser) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("owner", JoinType.LEFT);
                root.fetch("category", JoinType.LEFT);
                query.distinct(true);
            }
            if (currentUser.getRole() == Role.ADMIN) {
                return cb.conjunction();
            }
            return cb.equal(root.get("owner").get("id"), currentUser.getId());
        };
    }

    private RecurringPaymentStatus ensureStatus(RecurringPayment recurringPayment, int year, int month) {
        return recurringPaymentStatusRepository.findByRecurringPaymentAndYearAndMonth(recurringPayment, year, month)
                .orElseGet(() -> {
                    RecurringPaymentStatus status = new RecurringPaymentStatus();
                    status.setRecurringPayment(recurringPayment);
                    status.setYear(year);
                    status.setMonth(month);
                    status.setPaid(false);
                    return recurringPaymentStatusRepository.save(status);
                });
    }

    public RecurringPaymentResponse toResponse(RecurringPayment recurringPayment) {
        LocalDate now = LocalDate.now();
        RecurringPaymentStatus status = ensureStatus(recurringPayment, now.getYear(), now.getMonthValue());
        return new RecurringPaymentResponse(
                recurringPayment.getId(),
                recurringPayment.getName(),
                recurringPayment.getAmount(),
                recurringPayment.getDueDay(),
                recurringPayment.getCategory().getId(),
                recurringPayment.getCategory().getName(),
                recurringPayment.getOwner().getId(),
                recurringPayment.getOwner().getUsername(),
                recurringPayment.isActive(),
                new RecurringPaymentStatusResponse(status.getId(), status.getYear(), status.getMonth(), status.isPaid())
        );
    }
}
