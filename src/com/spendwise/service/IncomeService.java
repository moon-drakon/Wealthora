package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Income;
import com.spendwise.model.PaymentMethod;
import com.spendwise.repository.IncomeRepository;
import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class IncomeService {

    private final IncomeRepository repository;
    private final AccountService accountService;

    public IncomeService(
            IncomeRepository repository, AccountService accountService) {
        this.repository = Objects.requireNonNull(
                repository, "Income repository is required.");
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
    }

    public List<Income> getAllIncome() {
        return List.copyOf(repository.findAll());
    }

    public Optional<Income> findById(String id) {
        return repository.findById(validateId(id));
    }

    public Income createIncome(
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            String note) {
        Income income = new Income(
                date,
                amount,
                source,
                accountService.requireSelectable(account),
                note);
        repository.add(income);
        return income;
    }

    public Income createIncome(
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String note) {
        Income income = new Income(date, amount, source,
                accountService.requireSelectable(account),
                paymentMethod, tags, note);
        repository.add(income);
        return income;
    }

    Income createIncomeWithId(
            String id,
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            String note) {
        Income income = new Income(
                id,
                date,
                amount,
                source,
                accountService.requireSelectable(account),
                note);
        repository.add(income);
        return income;
    }

    public Income updateIncome(
            String id,
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            String note) {
        String normalizedId = validateId(id);
        Income existing = repository.findById(normalizedId)
                .orElseThrow(() -> new FinanceNotFoundException(
                    "Income entry was not found."));
        Account validatedAccount =
                accountService.requireSelectableOrHistorical(
                        account, existing.getAccount());
        Income replacement = new Income(
                normalizedId,
                date,
                amount,
                source,
                validatedAccount,
                existing.getPaymentMethod(),
                existing.getTags(),
                note);
        repository.update(replacement);
        return replacement;
    }

    public Income updateIncome(
            String id,
            LocalDate date,
            BigDecimal amount,
            String source,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String note) {
        String normalizedId = validateId(id);
        Income existing = repository.findById(normalizedId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "Income entry was not found."));
        Account validatedAccount = accountService.requireSelectableOrHistorical(
                account, existing.getAccount());
        Income replacement = new Income(normalizedId, date, amount, source,
                validatedAccount, paymentMethod, tags, note);
        repository.update(replacement);
        return replacement;
    }

    public boolean deleteIncome(String id) {
        return repository.deleteById(validateId(id));
    }

    public List<Income> findIncome(
            String searchText,
            Account account,
            LocalDate startDate,
            LocalDate endDate,
            IncomeSortOrder sortOrder) {
        if (sortOrder == null) {
            throw new ValidationException("Income sort order is required.");
        }
        validateDateRange(startDate, endDate);
        String search = searchText == null
                ? ""
                : searchText.strip().toLowerCase(Locale.ROOT);
        List<Income> results = new ArrayList<>();
        for (Income income : repository.findAll()) {
            boolean matchesText = search.isEmpty()
                    || contains(income.getSource(), search)
                    || contains(income.getNote(), search)
                    || contains(income.getPaymentMethod().getDisplayName(), search)
                    || income.getTags().stream().anyMatch(tag -> contains(tag, search))
                    || contains(income.getAccount().getDisplayName(), search);
            boolean matchesAccount = account == null
                    || income.getAccount().equals(account);
            boolean matchesDates = (startDate == null
                    || !income.getDate().isBefore(startDate))
                    && (endDate == null
                    || !income.getDate().isAfter(endDate));
            if (matchesText && matchesAccount && matchesDates) {
                results.add(income);
            }
        }
        if (sortOrder != IncomeSortOrder.ORIGINAL_ORDER) {
            results.sort(comparatorFor(sortOrder));
        }
        return List.copyOf(results);
    }

    private static Comparator<Income> comparatorFor(
            IncomeSortOrder sortOrder) {
        return switch (sortOrder) {
            case DATE_NEWEST_FIRST ->
                Comparator.comparing(Income::getDate).reversed();
            case DATE_OLDEST_FIRST ->
                Comparator.comparing(Income::getDate);
            case AMOUNT_HIGHEST_FIRST ->
                (first, second) ->
                    second.getAmount().compareTo(first.getAmount());
            case AMOUNT_LOWEST_FIRST ->
                (first, second) ->
                    first.getAmount().compareTo(second.getAmount());
            case SOURCE_A_TO_Z ->
                Comparator.comparing(income ->
                    income.getSource().toLowerCase(Locale.ROOT));
            case SOURCE_Z_TO_A ->
                Comparator.comparing(
                        (Income income) ->
                            income.getSource().toLowerCase(Locale.ROOT))
                        .reversed();
            case ORIGINAL_ORDER ->
                throw new IllegalArgumentException(
                        "Original order does not require a comparator.");
        };
    }

    private static String validateId(String id) {
        return FinanceValidator.validateIdentifier(id, "Income", "INCOME_");
    }

    private static void validateDateRange(
            LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null
                && startDate.isAfter(endDate)) {
            throw new ValidationException(
                    "Start date must not be after end date.");
        }
    }

    private static boolean contains(String value, String search) {
        return value.toLowerCase(Locale.ROOT).contains(search);
    }
}
