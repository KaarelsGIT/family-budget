package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsResponse;
import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsRow;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final UserService userService;

    public StatisticsService(
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService,
            AccountService accountService,
            UserService userService
    ) {
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.accountService = accountService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public YearlyStatisticsResponse getYearly(Integer year, Long userId, Long accountId) {
        User currentUser = currentUserService.getCurrentUser();
        int effectiveYear = year != null ? year : LocalDate.now().getYear();
        if (userId != null && currentUser.getRole() == Role.CHILD && !currentUser.getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot view statistics for this user");
        }
        User effectiveUser = userId == null ? currentUser : userService.findUser(userId);
        if (userId != null && currentUser.getRole() != Role.CHILD) {
            userService.ensureSelectableUser(currentUser, effectiveUser);
        }

        List<Long> visibleAccountIds = accountService.getVisibleAccounts(currentUser).stream()
                .map(Account::getId)
                .toList();

        if (accountId != null && visibleAccountIds.stream().noneMatch((visibleId) -> visibleId.equals(accountId))) {
            throw new ee.kaarel.familybudgetapplication.appConfig.ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "You cannot access this account");
        }

        if (visibleAccountIds.isEmpty()) {
            return emptyStatistics(effectiveYear);
        }

        List<YearlyStatisticsRow> rows = transactionRepository.findYearlyStatisticsRows(
                effectiveYear,
                effectiveUser.getId(),
                accountId,
                visibleAccountIds
        );

        Map<Integer, MonthlyAccumulator> monthly = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            monthly.put(month, new MonthlyAccumulator());
        }

        Map<Long, CategoryAccumulator> incomeCategories = new LinkedHashMap<>();
        Map<Long, CategoryAccumulator> expenseCategories = new LinkedHashMap<>();
        Map<Long, AccountAccumulator> accounts = new LinkedHashMap<>();
        Map<Integer, TransferMonthAccumulator> transferMonthly = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            transferMonthly.put(month, new TransferMonthAccumulator());
        }

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        BigDecimal savings = BigDecimal.ZERO;
        BigDecimal transfersTotalAmount = BigDecimal.ZERO;
        long transfersCount = 0L;

        for (YearlyStatisticsRow row : rows) {
            BigDecimal amount = normalize(row.totalAmount());
            int month = row.month() == null ? 0 : row.month();

            if (row.type() == TransactionType.INCOME) {
                income = income.add(amount);
                if (month >= 1 && month <= 12) {
                    monthly.get(month).income = monthly.get(month).income.add(amount);
                }
                addAccountIncome(accounts, row.toAccountId(), row.toAccountName(), amount);
                if (row.toAccountType() == AccountType.SAVINGS) {
                    savings = savings.add(amount);
                    if (month >= 1 && month <= 12) {
                        monthly.get(month).savings = monthly.get(month).savings.add(amount);
                    }
                }
                accumulateCategory(incomeCategories, row, amount, month);
                continue;
            }

            if (row.type() == TransactionType.EXPENSE) {
                expenses = expenses.add(amount);
                if (month >= 1 && month <= 12) {
                    monthly.get(month).expenses = monthly.get(month).expenses.add(amount);
                }
                addAccountExpense(accounts, row.fromAccountId(), row.fromAccountName(), amount);
                if (row.fromAccountType() == AccountType.SAVINGS) {
                    savings = savings.subtract(amount);
                    if (month >= 1 && month <= 12) {
                        monthly.get(month).savings = monthly.get(month).savings.subtract(amount);
                    }
                }
                accumulateCategory(expenseCategories, row, amount, month);
                continue;
            }

            if (row.type() == TransactionType.TRANSFER) {
                transfersTotalAmount = transfersTotalAmount.add(amount);
                transfersCount += row.transactionCount() == null ? 0L : row.transactionCount();
                if (month >= 1 && month <= 12) {
                    TransferMonthAccumulator transferMonth = transferMonthly.get(month);
                    transferMonth.totalAmount = transferMonth.totalAmount.add(amount);
                    transferMonth.count += row.transactionCount() == null ? 0L : row.transactionCount();
                }
                addAccountTransfer(accounts, row.fromAccountId(), row.fromAccountName(), amount, false);
                addAccountTransfer(accounts, row.toAccountId(), row.toAccountName(), amount, true);
                if (row.fromAccountType() == AccountType.SAVINGS) {
                    savings = savings.subtract(amount);
                    if (month >= 1 && month <= 12) {
                        monthly.get(month).savings = monthly.get(month).savings.subtract(amount);
                    }
                }
                if (row.toAccountType() == AccountType.SAVINGS) {
                    savings = savings.add(amount);
                    if (month >= 1 && month <= 12) {
                        monthly.get(month).savings = monthly.get(month).savings.add(amount);
                    }
                }
            }
        }

        List<YearlyStatisticsResponse.MonthlyEntry> monthlyEntries = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            MonthlyAccumulator accumulator = monthly.get(month);
            monthlyEntries.add(new YearlyStatisticsResponse.MonthlyEntry(
                    month,
                    accumulator.income,
                    accumulator.expenses,
                    accumulator.savings,
                    calculateRate(accumulator.savings, accumulator.income)
            ));
        }

        List<YearlyStatisticsResponse.CategoryEntry> incomeCategoryEntries = mapCategories(incomeCategories);
        List<YearlyStatisticsResponse.CategoryEntry> expenseCategoryEntries = mapCategories(expenseCategories);
        List<YearlyStatisticsResponse.AccountEntry> accountEntries = accounts.values().stream()
                .map(account -> new YearlyStatisticsResponse.AccountEntry(
                        account.accountId,
                        account.name,
                        account.income,
                        account.expenses,
                        account.balanceChange
                ))
                .sorted(Comparator.comparing(YearlyStatisticsResponse.AccountEntry::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        List<YearlyStatisticsResponse.MonthlyTransferEntry> transferMonthlyEntries = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            TransferMonthAccumulator accumulator = transferMonthly.get(month);
            transferMonthlyEntries.add(new YearlyStatisticsResponse.MonthlyTransferEntry(
                    month,
                    accumulator.totalAmount,
                    accumulator.count
            ));
        }

        BigDecimal net = income.subtract(expenses);

        return new YearlyStatisticsResponse(
                effectiveYear,
                new YearlyStatisticsResponse.Totals(
                        income,
                        expenses,
                        net,
                        savings,
                        calculateRate(savings, income)
                ),
                monthlyEntries,
                new YearlyStatisticsResponse.Categories(incomeCategoryEntries, expenseCategoryEntries),
                accountEntries,
                new YearlyStatisticsResponse.Transfers(transfersTotalAmount, transfersCount, transferMonthlyEntries)
        );
    }

    private YearlyStatisticsResponse emptyStatistics(int year) {
        List<YearlyStatisticsResponse.MonthlyEntry> monthlyEntries = new ArrayList<>(12);
        List<YearlyStatisticsResponse.MonthlyTransferEntry> transferMonthlyEntries = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            monthlyEntries.add(new YearlyStatisticsResponse.MonthlyEntry(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            transferMonthlyEntries.add(new YearlyStatisticsResponse.MonthlyTransferEntry(month, BigDecimal.ZERO, 0L));
        }

        return new YearlyStatisticsResponse(
                year,
                new YearlyStatisticsResponse.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                monthlyEntries,
                new YearlyStatisticsResponse.Categories(List.of(), List.of()),
                List.of(),
                new YearlyStatisticsResponse.Transfers(BigDecimal.ZERO, 0L, transferMonthlyEntries)
        );
    }

    private void accumulateCategory(Map<Long, CategoryAccumulator> buckets, YearlyStatisticsRow row, BigDecimal amount, int month) {
        if (row.categoryId() == null) {
            return;
        }

        Long parentKey = row.parentCategoryId() != null ? row.parentCategoryId() : row.categoryId();
        String parentName = row.parentCategoryId() != null ? row.parentCategoryName() : row.categoryName();
        CategoryAccumulator bucket = buckets.computeIfAbsent(parentKey, key -> new CategoryAccumulator(parentName));
        bucket.total = bucket.total.add(amount);
        if (month >= 1 && month <= 12) {
            bucket.monthly.merge(month, amount, BigDecimal::add);
        }

        if (row.parentCategoryId() != null) {
            SubcategoryAccumulator subcategory = bucket.subcategories.computeIfAbsent(
                    row.categoryId(),
                    key -> new SubcategoryAccumulator(row.categoryName())
            );
            subcategory.total = subcategory.total.add(amount);
            if (month >= 1 && month <= 12) {
                subcategory.monthly.merge(month, amount, BigDecimal::add);
            }
        }
    }

    private List<YearlyStatisticsResponse.CategoryEntry> mapCategories(Map<Long, CategoryAccumulator> buckets) {
        return buckets.values().stream()
                .map(bucket -> new YearlyStatisticsResponse.CategoryEntry(
                        bucket.parentCategory,
                        bucket.total,
                        toMonthlyMap(bucket.monthly),
                        bucket.subcategories.values().stream()
                                .map(subcategory -> new YearlyStatisticsResponse.SubcategoryEntry(
                                        subcategory.name,
                                        subcategory.total,
                                        toMonthlyMap(subcategory.monthly)
                                ))
                                .sorted(Comparator.comparing(
                                        YearlyStatisticsResponse.SubcategoryEntry::name,
                                        Comparator.nullsLast(String::compareToIgnoreCase)
                                ))
                                .toList()
                ))
                .sorted(Comparator.comparing(
                                YearlyStatisticsResponse.CategoryEntry::total,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                YearlyStatisticsResponse.CategoryEntry::parentCategory,
                                Comparator.nullsLast(String::compareToIgnoreCase)
                        ))
                .toList();
    }

    private void addAccountIncome(Map<Long, AccountAccumulator> accounts, Long accountId, String accountName, BigDecimal amount) {
        if (accountId == null) {
            return;
        }
        AccountAccumulator account = accounts.computeIfAbsent(accountId, key -> new AccountAccumulator(accountId, accountName));
        account.income = account.income.add(amount);
        account.balanceChange = account.balanceChange.add(amount);
    }

    private void addAccountExpense(Map<Long, AccountAccumulator> accounts, Long accountId, String accountName, BigDecimal amount) {
        if (accountId == null) {
            return;
        }
        AccountAccumulator account = accounts.computeIfAbsent(accountId, key -> new AccountAccumulator(accountId, accountName));
        account.expenses = account.expenses.add(amount);
        account.balanceChange = account.balanceChange.subtract(amount);
    }

    private void addAccountTransfer(Map<Long, AccountAccumulator> accounts, Long accountId, String accountName, BigDecimal amount, boolean incoming) {
        if (accountId == null) {
            return;
        }
        AccountAccumulator account = accounts.computeIfAbsent(accountId, key -> new AccountAccumulator(accountId, accountName));
        account.balanceChange = incoming ? account.balanceChange.add(amount) : account.balanceChange.subtract(amount);
    }

    private BigDecimal calculateRate(BigDecimal savings, BigDecimal income) {
        if (income == null || income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return savings
                .multiply(HUNDRED)
                .divide(income, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class MonthlyAccumulator {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expenses = BigDecimal.ZERO;
        private BigDecimal savings = BigDecimal.ZERO;
    }

    private static final class AccountAccumulator {
        private final Long accountId;
        private final String name;
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expenses = BigDecimal.ZERO;
        private BigDecimal balanceChange = BigDecimal.ZERO;

        private AccountAccumulator(Long accountId, String name) {
            this.accountId = accountId;
            this.name = name;
        }
    }

    private static final class CategoryAccumulator {
        private final String parentCategory;
        private BigDecimal total = BigDecimal.ZERO;
        private final Map<Long, SubcategoryAccumulator> subcategories = new LinkedHashMap<>();
        private final Map<Integer, BigDecimal> monthly = createMonthlyBuckets();

        private CategoryAccumulator(String parentCategory) {
            this.parentCategory = parentCategory;
        }
    }

    private static final class SubcategoryAccumulator {
        private final String name;
        private BigDecimal total = BigDecimal.ZERO;
        private final Map<Integer, BigDecimal> monthly = createMonthlyBuckets();

        private SubcategoryAccumulator(String name) {
            this.name = name;
        }
    }

    private static final class TransferMonthAccumulator {
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private long count = 0L;
    }

    private static Map<Integer, BigDecimal> createMonthlyBuckets() {
        Map<Integer, BigDecimal> monthly = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            monthly.put(month, BigDecimal.ZERO);
        }
        return monthly;
    }

    private static Map<Integer, BigDecimal> toMonthlyMap(Map<Integer, BigDecimal> monthly) {
        Map<Integer, BigDecimal> copy = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            copy.put(month, monthly.getOrDefault(month, BigDecimal.ZERO));
        }
        return copy;
    }
}
