package com.wealthora.server.service;

import static com.wealthora.server.api.FinanceDtos.AccountRequest;
import static com.wealthora.server.api.FinanceDtos.AccountResponse;
import static com.wealthora.server.api.FinanceDtos.CategoryRequest;
import static com.wealthora.server.api.FinanceDtos.CategoryResponse;
import static com.wealthora.server.api.FinanceDtos.DefaultAccountResponse;
import static com.wealthora.server.api.FinanceDtos.PageResponse;

import com.wealthora.server.domain.FinanceAccount;
import com.wealthora.server.domain.FinanceCategory;
import com.wealthora.server.domain.FinancePreference;
import com.wealthora.server.repository.FinanceAccountRepository;
import com.wealthora.server.repository.FinanceCategoryRepository;
import com.wealthora.server.repository.FinancePreferenceRepository;
import com.wealthora.server.security.SessionPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceWorkspaceService {

    public static final String DEFAULT_ACCOUNT = "ACCOUNT_DEFAULT_CASH";
    private static final Map<String, String> BUILT_IN_CATEGORIES = Map.of(
            "FOOD", "Food",
            "TRANSPORT", "Transport",
            "SHOPPING", "Shopping",
            "BILLS", "Bills",
            "HEALTH", "Health",
            "EDUCATION", "Education",
            "ENTERTAINMENT", "Entertainment",
            "OTHER", "Other");
    private static final Set<String> ACCOUNT_TYPES = Set.of(
            "CASH", "BANK", "SAVINGS", "MOBILE_BANKING",
            "DIGITAL_WALLET", "CREDIT_CARD", "DEBIT_CARD", "OTHER");

    private final FinanceAccountRepository accounts;
    private final FinanceCategoryRepository categories;
    private final FinancePreferenceRepository preferences;
    private final Clock clock;

    public FinanceWorkspaceService(
            FinanceAccountRepository accounts,
            FinanceCategoryRepository categories,
            FinancePreferenceRepository preferences,
            Clock clock) {
        this.accounts = accounts;
        this.categories = categories;
        this.preferences = preferences;
        this.clock = clock;
    }

    @Transactional
    public PageResponse<AccountResponse> accounts(
            SessionPrincipal principal, int page, int size) {
        ensureWorkspace(principal.userId());
        Page<FinanceAccount> result = accounts.findByUserId(
                principal.userId(), page(page, size, "defaultAccount", "name"));
        return page(result.map(this::response));
    }

    @Transactional
    public AccountResponse account(SessionPrincipal principal, String externalId) {
        ensureWorkspace(principal.userId());
        return response(ownedAccount(principal.userId(), externalId));
    }

    @Transactional
    public AccountResponse createAccount(
            SessionPrincipal principal, AccountRequest request) {
        ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        String externalId = FinanceValidation.externalId(
                request.externalId(), "ACCOUNT_");
        if (DEFAULT_ACCOUNT.equals(externalId)
                || accounts.existsByUserIdAndExternalId(userId, externalId)) {
            throw FinanceValidation.duplicate();
        }
        Instant now = clock.instant();
        FinanceAccount account = new FinanceAccount(
                UUID.randomUUID(), userId, externalId,
                FinanceValidation.requiredText(request.name(), "Account name", 160),
                FinanceValidation.enumValue(request.accountType(),
                        "Account type", ACCOUNT_TYPES),
                FinanceValidation.currency(request.currencyCode()),
                FinanceValidation.signedAmount(request.openingBalance(),
                        "Opening balance"),
                FinanceValidation.requiredText(request.iconName(),
                        "Account icon", 30),
                FinanceValidation.color(request.colorHex()),
                FinanceValidation.optionalText(request.institutionName(),
                        "Institution name", 160),
                validateOpenedOn(request.openedOn()), request.archived(), false,
                now);
        accounts.save(account);
        return response(account);
    }

    @Transactional
    public AccountResponse updateAccount(
            SessionPrincipal principal, String externalId,
            AccountRequest request) {
        ensureWorkspace(principal.userId());
        FinanceAccount account = ownedAccount(principal.userId(), externalId);
        if (account.isDefaultAccount()) {
            throw FinanceValidation.invalid(
                    "The protected default account cannot be changed.");
        }
        if (!account.getExternalId().equals(request.externalId())) {
            throw FinanceValidation.invalid(
                    "Account identifier cannot be changed.");
        }
        account.update(
                FinanceValidation.requiredText(request.name(), "Account name", 160),
                FinanceValidation.enumValue(request.accountType(),
                        "Account type", ACCOUNT_TYPES),
                FinanceValidation.currency(request.currencyCode()),
                FinanceValidation.signedAmount(request.openingBalance(),
                        "Opening balance"),
                FinanceValidation.requiredText(request.iconName(),
                        "Account icon", 30),
                FinanceValidation.color(request.colorHex()),
                FinanceValidation.optionalText(request.institutionName(),
                        "Institution name", 160),
                validateOpenedOn(request.openedOn()), request.archived(),
                clock.instant());
        return response(account);
    }

    @Transactional
    public DefaultAccountResponse defaultAccount(SessionPrincipal principal) {
        ensureWorkspace(principal.userId());
        FinancePreference preference = preferences.findById(principal.userId())
                .orElseThrow(FinanceValidation::missing);
        FinanceAccount account = accounts.findByUserIdAndId(
                principal.userId(), preference.getDefaultAccountId())
                .orElseThrow(FinanceValidation::missing);
        return new DefaultAccountResponse(account.getExternalId());
    }

    @Transactional
    public DefaultAccountResponse setDefaultAccount(
            SessionPrincipal principal, String externalId) {
        ensureWorkspace(principal.userId());
        FinanceAccount account = ownedAccount(principal.userId(), externalId);
        if (account.isArchived()) {
            throw FinanceValidation.invalid(
                    "Archived accounts cannot be the default account.");
        }
        FinancePreference preference = preferences.findById(principal.userId())
                .orElseThrow(FinanceValidation::missing);
        preference.setDefaultAccountId(account.getId(), clock.instant());
        return new DefaultAccountResponse(account.getExternalId());
    }

    @Transactional
    public PageResponse<CategoryResponse> categories(
            SessionPrincipal principal, int page, int size) {
        ensureWorkspace(principal.userId());
        Page<FinanceCategory> result = categories.findByUserId(
                principal.userId(), page(page, size, "builtIn", "name"));
        return page(result.map(category -> categoryResponse(
                principal.userId(), category)));
    }

    @Transactional
    public CategoryResponse createCategory(
            SessionPrincipal principal, CategoryRequest request) {
        ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        String externalId = FinanceValidation.externalId(
                request.externalId(), "CUSTOM_");
        if (categories.existsByUserIdAndExternalId(userId, externalId)) {
            throw FinanceValidation.duplicate();
        }
        FinanceCategory parent = request.parentExternalId() == null
                || request.parentExternalId().isBlank() ? null
                : ownedCategory(userId, request.parentExternalId());
        validateParent(parent);
        FinanceCategory category = new FinanceCategory(
                UUID.randomUUID(), userId, externalId,
                FinanceValidation.requiredText(request.name(),
                        "Category name", 120),
                "EXPENSE", false, request.archived(),
                parent == null ? null : parent.getId(), clock.instant());
        categories.save(category);
        return categoryResponse(userId, category);
    }

    @Transactional
    public CategoryResponse updateCategory(
            SessionPrincipal principal, String externalId,
            CategoryRequest request) {
        ensureWorkspace(principal.userId());
        FinanceCategory category = ownedCategory(
                principal.userId(), externalId);
        if (category.isBuiltIn()) {
            throw FinanceValidation.invalid(
                    "Built-in categories cannot be changed.");
        }
        if (!category.getExternalId().equals(request.externalId())) {
            throw FinanceValidation.invalid(
                    "Category identifier cannot be changed.");
        }
        FinanceCategory parent = request.parentExternalId() == null
                || request.parentExternalId().isBlank() ? null
                : ownedCategory(principal.userId(), request.parentExternalId());
        validateParent(parent);
        if (parent != null && parent.getId().equals(category.getId())) {
            throw FinanceValidation.invalid(
                    "A category cannot be its own parent.");
        }
        category.update(FinanceValidation.requiredText(
                request.name(), "Category name", 120), request.archived(),
                parent == null ? null : parent.getId(), clock.instant());
        return categoryResponse(principal.userId(), category);
    }

    @Transactional
    public void ensureWorkspace(UUID userId) {
        Instant now = clock.instant();
        FinanceAccount defaultAccount = accounts.findByUserIdAndExternalId(
                userId, DEFAULT_ACCOUNT).orElseGet(() -> accounts.save(
                        new FinanceAccount(UUID.randomUUID(), userId,
                                DEFAULT_ACCOUNT, "Cash", "CASH", "BDT",
                                new BigDecimal("0.00"), "cash", "#1F7E60",
                                "", null, false, true, now)));
        if (!preferences.existsById(userId)) {
            preferences.save(new FinancePreference(
                    userId, defaultAccount.getId(), now));
        }
        for (Map.Entry<String, String> builtIn
                : BUILT_IN_CATEGORIES.entrySet()) {
            if (!categories.existsByUserIdAndExternalId(
                    userId, builtIn.getKey())) {
                categories.save(new FinanceCategory(
                        UUID.randomUUID(), userId, builtIn.getKey(),
                        builtIn.getValue(), "EXPENSE", true, false, null, now));
            }
        }
    }

    FinanceAccount ownedAccount(UUID userId, String externalId) {
        return accounts.findByUserIdAndExternalId(userId,
                FinanceValidation.externalId(externalId, "ACCOUNT_"))
                .orElseThrow(FinanceValidation::missing);
    }

    FinanceCategory ownedCategory(UUID userId, String externalId) {
        return categories.findByUserIdAndExternalId(userId,
                FinanceValidation.externalId(externalId, ""))
                .orElseThrow(FinanceValidation::missing);
    }

    Map<UUID, FinanceAccount> lockAccounts(UUID userId, UUID... identifiers) {
        java.util.TreeSet<UUID> ordered = new java.util.TreeSet<>();
        java.util.Collections.addAll(ordered, identifiers);
        Map<UUID, FinanceAccount> result = new LinkedHashMap<>();
        for (UUID identifier : ordered) {
            result.put(identifier, accounts.findOwnedForUpdate(userId, identifier)
                    .orElseThrow(FinanceValidation::missing));
        }
        return result;
    }

    String accountExternalId(UUID userId, UUID id) {
        return accounts.findByUserIdAndId(userId, id)
                .map(FinanceAccount::getExternalId)
                .orElseThrow(FinanceValidation::missing);
    }

    String categoryExternalId(UUID userId, UUID id) {
        if (id == null) return null;
        return categories.findByUserIdAndId(userId, id)
                .map(FinanceCategory::getExternalId)
                .orElseThrow(FinanceValidation::missing);
    }

    void validateCategoryLimits(UUID userId, Map<String, BigDecimal> limits) {
        if (limits == null) throw FinanceValidation.invalid(
                "Category limits are required.");
        for (Map.Entry<String, BigDecimal> entry : limits.entrySet()) {
            FinanceCategory category = ownedCategory(userId, entry.getKey());
            if (category.isArchived()) {
                throw FinanceValidation.invalid(
                        "Archived categories cannot receive new limits.");
            }
            FinanceValidation.positiveAmount(entry.getValue(), "Category limit");
        }
    }

    AccountResponse response(FinanceAccount account) {
        return new AccountResponse(account.getExternalId(), account.getName(),
                account.getAccountType(), account.getCurrencyCode(),
                account.getOpeningBalance(), account.getCurrentBalance(),
                account.getIconName(), account.getColorHex(),
                account.getInstitutionName(), account.getOpenedOn(),
                account.isArchived(), account.isDefaultAccount());
    }

    private CategoryResponse categoryResponse(
            UUID userId, FinanceCategory category) {
        return new CategoryResponse(category.getExternalId(), category.getName(),
                category.isBuiltIn(), category.isArchived(),
                category.getParentId() == null ? null
                        : categoryExternalId(userId, category.getParentId()));
    }

    static PageRequest page(int page, int size, String... properties) {
        if (page < 0 || size < 1 || size > 100) {
            throw FinanceValidation.invalid(
                    "Page must be non-negative and size must be from 1 through 100.");
        }
        return PageRequest.of(page, size, Sort.by(properties));
    }

    static <T> PageResponse<T> page(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static LocalDate validateOpenedOn(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now())) {
            throw FinanceValidation.invalid(
                    "Account creation date cannot be in the future.");
        }
        return date;
    }

    private static void validateParent(FinanceCategory parent) {
        if (parent == null) return;
        if (parent.isArchived() || parent.getParentId() != null) {
            throw FinanceValidation.invalid(
                    "Category parent must be an active top-level category.");
        }
    }
}
