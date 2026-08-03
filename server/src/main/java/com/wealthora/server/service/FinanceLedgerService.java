package com.wealthora.server.service;

import static com.wealthora.server.api.FinanceDtos.DashboardSummaryResponse;
import static com.wealthora.server.api.FinanceDtos.ExpenseRequest;
import static com.wealthora.server.api.FinanceDtos.IncomeRequest;
import static com.wealthora.server.api.FinanceDtos.PageResponse;
import static com.wealthora.server.api.FinanceDtos.TransactionResponse;
import static com.wealthora.server.api.FinanceDtos.TransferRequest;
import static com.wealthora.server.api.FinanceDtos.TransferResponse;

import com.wealthora.server.domain.FinanceAccount;
import com.wealthora.server.domain.FinanceCategory;
import com.wealthora.server.domain.FinanceTransaction;
import com.wealthora.server.domain.FinanceTransfer;
import com.wealthora.server.domain.MonthlyBudgetRecord;
import com.wealthora.server.repository.FinanceAccountRepository;
import com.wealthora.server.repository.FinanceTransactionRepository;
import com.wealthora.server.repository.FinanceTransferRepository;
import com.wealthora.server.repository.MonthlyBudgetRecordRepository;
import com.wealthora.server.security.SessionPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceLedgerService {

    private static final Set<String> PAYMENT_METHODS = Set.of(
            "UNSPECIFIED", "CASH", "BANK_TRANSFER", "MOBILE_BANKING",
            "DIGITAL_WALLET", "CREDIT_CARD", "DEBIT_CARD", "CHECK", "OTHER");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceWorkspaceService workspace;
    private final FinanceAccountRepository accounts;
    private final FinanceTransactionRepository transactions;
    private final FinanceTransferRepository transfers;
    private final MonthlyBudgetRecordRepository budgets;
    private final Clock clock;

    public FinanceLedgerService(
            FinanceWorkspaceService workspace,
            FinanceAccountRepository accounts,
            FinanceTransactionRepository transactions,
            FinanceTransferRepository transfers,
            MonthlyBudgetRecordRepository budgets,
            Clock clock) {
        this.workspace = workspace;
        this.accounts = accounts;
        this.transactions = transactions;
        this.transfers = transfers;
        this.budgets = budgets;
        this.clock = clock;
    }

    @Transactional
    public PageResponse<TransactionResponse> transactions(
            SessionPrincipal principal, int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        Page<FinanceTransaction> result = transactions.findByUserId(
                principal.userId(), FinanceWorkspaceService.page(
                        page, size, "occurredOn", "externalId"));
        return FinanceWorkspaceService.page(result.map(
                item -> response(principal.userId(), item)));
    }

    @Transactional
    public PageResponse<TransactionResponse> expenses(
            SessionPrincipal principal, int page, int size) {
        return typedTransactions(principal, "EXPENSE", page, size);
    }

    @Transactional
    public PageResponse<TransactionResponse> income(
            SessionPrincipal principal, int page, int size) {
        return typedTransactions(principal, "INCOME", page, size);
    }

    @Transactional
    public TransactionResponse createExpense(
            SessionPrincipal principal, ExpenseRequest request) {
        workspace.ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        String externalId = FinanceValidation.externalId(
                request.externalId(), "");
        rejectDuplicateTransaction(userId, externalId);
        FinanceAccount requestedAccount = workspace.ownedAccount(
                userId, request.accountExternalId());
        FinanceCategory category = workspace.ownedCategory(
                userId, request.categoryExternalId());
        requireAvailable(requestedAccount, "Expense account");
        requireAvailable(category);
        FinanceAccount account = workspace.lockAccounts(
                userId, requestedAccount.getId()).get(requestedAccount.getId());
        BigDecimal amount = FinanceValidation.positiveAmount(
                request.amount(), "Expense amount");
        LocalDate date = FinanceValidation.postedDate(
                request.date(), "Expense date");
        Instant now = clock.instant();
        FinanceTransaction transaction = new FinanceTransaction(
                UUID.randomUUID(), userId, externalId, account.getId(),
                category.getId(), "EXPENSE", amount,
                FinanceValidation.requiredText(request.description(),
                        "Expense description", 160),
                date, paymentMethod(request.paymentMethod()),
                FinanceValidation.encodeTags(request.tags()),
                FinanceValidation.optionalText(request.note(),
                        "Expense note", 500), null, null, now);
        account.changeBalance(amount.negate(), now);
        transactions.save(transaction);
        return response(userId, transaction);
    }

    @Transactional
    public TransactionResponse updateExpense(
            SessionPrincipal principal, String externalId,
            ExpenseRequest request) {
        return updateTransaction(principal, externalId, request.externalId(),
                "EXPENSE", request.amount(), request.date(),
                request.accountExternalId(), request.categoryExternalId(),
                request.description(), request.paymentMethod(), request.tags(),
                request.note());
    }

    @Transactional
    public void deleteExpense(SessionPrincipal principal, String externalId) {
        deleteTransaction(principal, externalId, "EXPENSE");
    }

    @Transactional
    public TransactionResponse createIncome(
            SessionPrincipal principal, IncomeRequest request) {
        workspace.ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        String externalId = FinanceValidation.externalId(
                request.externalId(), "INCOME_");
        rejectDuplicateTransaction(userId, externalId);
        FinanceAccount requestedAccount = workspace.ownedAccount(
                userId, request.accountExternalId());
        requireAvailable(requestedAccount, "Income account");
        FinanceAccount account = workspace.lockAccounts(
                userId, requestedAccount.getId()).get(requestedAccount.getId());
        BigDecimal amount = FinanceValidation.positiveAmount(
                request.amount(), "Income amount");
        LocalDate date = FinanceValidation.postedDate(
                request.date(), "Income date");
        Instant now = clock.instant();
        FinanceTransaction transaction = new FinanceTransaction(
                UUID.randomUUID(), userId, externalId, account.getId(), null,
                "INCOME", amount,
                FinanceValidation.requiredText(request.source(),
                        "Income source", 160),
                date, paymentMethod(request.paymentMethod()),
                FinanceValidation.encodeTags(request.tags()),
                FinanceValidation.optionalText(request.note(),
                        "Income note", 500), null, null, now);
        account.changeBalance(amount, now);
        transactions.save(transaction);
        return response(userId, transaction);
    }

    @Transactional
    public TransactionResponse updateIncome(
            SessionPrincipal principal, String externalId,
            IncomeRequest request) {
        return updateTransaction(principal, externalId, request.externalId(),
                "INCOME", request.amount(), request.date(),
                request.accountExternalId(), null, request.source(),
                request.paymentMethod(), request.tags(), request.note());
    }

    @Transactional
    public void deleteIncome(SessionPrincipal principal, String externalId) {
        deleteTransaction(principal, externalId, "INCOME");
    }

    @Transactional
    public PageResponse<TransferResponse> transfers(
            SessionPrincipal principal, int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        Page<FinanceTransfer> result = transfers.findByUserId(
                principal.userId(), FinanceWorkspaceService.page(
                        page, size, "occurredOn", "externalId"));
        return FinanceWorkspaceService.page(result.map(
                item -> response(principal.userId(), item)));
    }

    @Transactional
    public TransferResponse createTransfer(
            SessionPrincipal principal, TransferRequest request) {
        workspace.ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        String externalId = FinanceValidation.externalId(
                request.externalId(), "TRANSFER_");
        if (transfers.existsByUserIdAndExternalId(userId, externalId)) {
            throw FinanceValidation.duplicate();
        }
        FinanceAccount source = workspace.ownedAccount(
                userId, request.sourceAccountExternalId());
        FinanceAccount destination = workspace.ownedAccount(
                userId, request.destinationAccountExternalId());
        validateTransferAccounts(source, destination);
        Map<UUID, FinanceAccount> locked = workspace.lockAccounts(
                userId, source.getId(), destination.getId());
        source = locked.get(source.getId());
        destination = locked.get(destination.getId());
        BigDecimal amount = FinanceValidation.positiveAmount(
                request.amount(), "Transfer amount");
        LocalDate date = FinanceValidation.postedDate(
                request.date(), "Transfer date");
        List<String> tags = FinanceValidation.tags(request.tags());
        String note = FinanceValidation.optionalText(
                request.note(), "Transfer note", 500);
        Instant now = clock.instant();
        FinanceTransfer transfer = new FinanceTransfer(
                UUID.randomUUID(), userId, externalId, source.getId(),
                destination.getId(), amount, date,
                FinanceValidation.encodeTags(tags), note, now);
        transfers.save(transfer);
        transactions.save(newTransferLeg(transfer, source.getId(),
                "OUT", externalId + "_OUT", tags, note, now));
        transactions.save(newTransferLeg(transfer, destination.getId(),
                "IN", externalId + "_IN", tags, note, now));
        source.changeBalance(amount.negate(), now);
        destination.changeBalance(amount, now);
        return response(userId, transfer);
    }

    @Transactional
    public TransferResponse updateTransfer(
            SessionPrincipal principal, String externalId,
            TransferRequest request) {
        workspace.ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        FinanceTransfer transfer = transfers.findByUserIdAndExternalId(
                userId, FinanceValidation.externalId(externalId, "TRANSFER_"))
                .orElseThrow(FinanceValidation::missing);
        if (!transfer.getExternalId().equals(request.externalId())) {
            throw FinanceValidation.invalid(
                    "Transfer identifier cannot be changed.");
        }
        FinanceAccount newSource = workspace.ownedAccount(
                userId, request.sourceAccountExternalId());
        FinanceAccount newDestination = workspace.ownedAccount(
                userId, request.destinationAccountExternalId());
        validateTransferAccounts(newSource, newDestination);
        Map<UUID, FinanceAccount> locked = workspace.lockAccounts(userId,
                transfer.getSourceId(), transfer.getDestinationId(),
                newSource.getId(), newDestination.getId());
        Instant now = clock.instant();
        locked.get(transfer.getSourceId()).changeBalance(
                transfer.getAmount(), now);
        locked.get(transfer.getDestinationId()).changeBalance(
                transfer.getAmount().negate(), now);
        BigDecimal amount = FinanceValidation.positiveAmount(
                request.amount(), "Transfer amount");
        locked.get(newSource.getId()).changeBalance(amount.negate(), now);
        locked.get(newDestination.getId()).changeBalance(amount, now);
        LocalDate date = FinanceValidation.postedDate(
                request.date(), "Transfer date");
        List<String> tags = FinanceValidation.tags(request.tags());
        String note = FinanceValidation.optionalText(
                request.note(), "Transfer note", 500);
        transfer.update(newSource.getId(), newDestination.getId(), amount,
                date, FinanceValidation.encodeTags(tags), note, now);
        List<FinanceTransaction> legs = transactions.findByUserIdAndTransferId(
                userId, transfer.getId());
        if (legs.size() != 2) {
            throw new IllegalStateException(
                    "Stored transfer ledger is incomplete.");
        }
        for (FinanceTransaction leg : legs) {
            boolean outgoing = "OUT".equals(leg.getTransferDirection());
            leg.update(outgoing ? newSource.getId() : newDestination.getId(),
                    null, amount, "Transfer", date, "BANK_TRANSFER",
                    FinanceValidation.encodeTags(tags), note, now);
        }
        return response(userId, transfer);
    }

    @Transactional
    public void deleteTransfer(SessionPrincipal principal, String externalId) {
        workspace.ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        FinanceTransfer transfer = transfers.findByUserIdAndExternalId(userId,
                FinanceValidation.externalId(externalId, "TRANSFER_"))
                .orElseThrow(FinanceValidation::missing);
        Map<UUID, FinanceAccount> locked = workspace.lockAccounts(
                userId, transfer.getSourceId(), transfer.getDestinationId());
        Instant now = clock.instant();
        locked.get(transfer.getSourceId()).changeBalance(
                transfer.getAmount(), now);
        locked.get(transfer.getDestinationId()).changeBalance(
                transfer.getAmount().negate(), now);
        transactions.deleteAll(transactions.findByUserIdAndTransferId(
                userId, transfer.getId()));
        transfers.delete(transfer);
    }

    @Transactional
    public DashboardSummaryResponse dashboard(
            SessionPrincipal principal, YearMonth month) {
        workspace.ensureWorkspace(principal.userId());
        YearMonth requiredMonth = month == null ? YearMonth.now() : month;
        LocalDate start = requiredMonth.atDay(1);
        LocalDate end = requiredMonth.atEndOfMonth();
        List<FinanceTransaction> entries =
                transactions.findByUserIdAndOccurredOnBetween(
                        principal.userId(), start, end);
        BigDecimal income = sum(entries, "INCOME");
        BigDecimal expenses = sum(entries, "EXPENSE");
        BigDecimal totalBalance = accounts.findByUserId(principal.userId(),
                FinanceWorkspaceService.page(0, 100, "name")).stream()
                .map(FinanceAccount::getCurrentBalance)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal limit = budgets.findByUserIdAndBudgetMonth(
                principal.userId(), requiredMonth.toString())
                .map(MonthlyBudgetRecord::getOverallLimit).orElse(null);
        BigDecimal remaining = limit == null ? null : limit.subtract(expenses);
        long accountCount = accounts.findByUserId(principal.userId(),
                FinanceWorkspaceService.page(0, 1, "name"))
                .getTotalElements();
        return new DashboardSummaryResponse(requiredMonth, income, expenses,
                income.subtract(expenses), totalBalance, accountCount,
                entries.size(), limit, remaining);
    }

    private PageResponse<TransactionResponse> typedTransactions(
            SessionPrincipal principal, String type, int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        Page<FinanceTransaction> result =
                transactions.findByUserIdAndTransactionType(
                        principal.userId(), type,
                        FinanceWorkspaceService.page(
                                page, size, "occurredOn", "externalId"));
        return FinanceWorkspaceService.page(result.map(
                item -> response(principal.userId(), item)));
    }

    private TransactionResponse updateTransaction(
            SessionPrincipal principal, String pathId, String requestId,
            String expectedType, BigDecimal requestedAmount,
            LocalDate requestedDate, String accountExternalId,
            String categoryExternalId, String description,
            String paymentMethod, List<String> tags, String note) {
        workspace.ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        FinanceTransaction transaction = transactions
                .findByUserIdAndExternalId(userId,
                        FinanceValidation.externalId(pathId, ""))
                .filter(item -> expectedType.equals(item.getTransactionType()))
                .orElseThrow(FinanceValidation::missing);
        if (!transaction.getExternalId().equals(requestId)) {
            throw FinanceValidation.invalid(
                    "Transaction identifier cannot be changed.");
        }
        FinanceAccount requestedAccount = workspace.ownedAccount(
                userId, accountExternalId);
        requireAvailable(requestedAccount, "Transaction account");
        FinanceCategory category = categoryExternalId == null ? null
                : workspace.ownedCategory(userId, categoryExternalId);
        if (category != null) requireAvailable(category);
        Map<UUID, FinanceAccount> locked = workspace.lockAccounts(userId,
                transaction.getAccountId(), requestedAccount.getId());
        Instant now = clock.instant();
        BigDecimal oldDelta = "EXPENSE".equals(expectedType)
                ? transaction.getAmount() : transaction.getAmount().negate();
        locked.get(transaction.getAccountId()).changeBalance(oldDelta, now);
        BigDecimal amount = FinanceValidation.positiveAmount(
                requestedAmount, expectedType + " amount");
        BigDecimal newDelta = "EXPENSE".equals(expectedType)
                ? amount.negate() : amount;
        locked.get(requestedAccount.getId()).changeBalance(newDelta, now);
        transaction.update(requestedAccount.getId(),
                category == null ? null : category.getId(), amount,
                FinanceValidation.requiredText(description,
                        expectedType + " description", 160),
                FinanceValidation.postedDate(requestedDate,
                        expectedType + " date"),
                paymentMethod(paymentMethod),
                FinanceValidation.encodeTags(tags),
                FinanceValidation.optionalText(note,
                        expectedType + " note", 500), now);
        return response(userId, transaction);
    }

    private void deleteTransaction(
            SessionPrincipal principal, String externalId,
            String expectedType) {
        workspace.ensureWorkspace(principal.userId());
        UUID userId = principal.userId();
        FinanceTransaction transaction = transactions
                .findByUserIdAndExternalId(userId,
                        FinanceValidation.externalId(externalId, ""))
                .filter(item -> expectedType.equals(item.getTransactionType()))
                .orElseThrow(FinanceValidation::missing);
        FinanceAccount account = workspace.lockAccounts(
                userId, transaction.getAccountId()).get(transaction.getAccountId());
        BigDecimal reverse = "EXPENSE".equals(expectedType)
                ? transaction.getAmount() : transaction.getAmount().negate();
        account.changeBalance(reverse, clock.instant());
        transactions.delete(transaction);
    }

    private TransactionResponse response(
            UUID userId, FinanceTransaction transaction) {
        String transferExternal = transaction.getTransferId() == null ? null
                : transfers.findByUserIdAndId(userId, transaction.getTransferId())
                        .map(FinanceTransfer::getExternalId)
                        .orElseThrow(FinanceValidation::missing);
        return new TransactionResponse(transaction.getExternalId(),
                transaction.getTransactionType(), transaction.getDescription(),
                transaction.getAmount(), transaction.getOccurredOn(),
                workspace.accountExternalId(userId, transaction.getAccountId()),
                workspace.categoryExternalId(userId, transaction.getCategoryId()),
                transaction.getPaymentMethod(),
                FinanceValidation.decodeTags(transaction.getTags()),
                transaction.getNote(), transferExternal,
                transaction.getTransferDirection());
    }

    private TransferResponse response(UUID userId, FinanceTransfer transfer) {
        return new TransferResponse(transfer.getExternalId(),
                transfer.getAmount(), transfer.getOccurredOn(),
                workspace.accountExternalId(userId, transfer.getSourceId()),
                workspace.accountExternalId(userId, transfer.getDestinationId()),
                FinanceValidation.decodeTags(transfer.getTags()),
                transfer.getNote());
    }

    private FinanceTransaction newTransferLeg(
            FinanceTransfer transfer, UUID accountId, String direction,
            String externalId, List<String> tags, String note, Instant now) {
        return new FinanceTransaction(UUID.randomUUID(), transfer.getUserId(),
                externalId, accountId, null, "TRANSFER", transfer.getAmount(),
                "Transfer", transfer.getOccurredOn(), "BANK_TRANSFER",
                FinanceValidation.encodeTags(tags), note, transfer.getId(),
                direction, now);
    }

    private void rejectDuplicateTransaction(UUID userId, String externalId) {
        if (transactions.existsByUserIdAndExternalId(userId, externalId)) {
            throw FinanceValidation.duplicate();
        }
    }

    private static void validateTransferAccounts(
            FinanceAccount source, FinanceAccount destination) {
        requireAvailable(source, "Source account");
        requireAvailable(destination, "Destination account");
        if (source.getId().equals(destination.getId())) {
            throw FinanceValidation.invalid(
                    "Source and destination accounts must be different.");
        }
        if (!source.getCurrencyCode().equals(destination.getCurrencyCode())) {
            throw FinanceValidation.invalid(
                    "Transfers require accounts with the same currency.");
        }
    }

    private static void requireAvailable(
            FinanceAccount account, String field) {
        if (account.isArchived()) {
            throw FinanceValidation.invalid(
                    field + " cannot be archived.");
        }
    }

    private static void requireAvailable(FinanceCategory category) {
        if (category.isArchived()) {
            throw FinanceValidation.invalid(
                    "Expense category cannot be archived.");
        }
    }

    private static String paymentMethod(String value) {
        return FinanceValidation.enumValue(
                value, "Payment method", PAYMENT_METHODS);
    }

    private static BigDecimal sum(
            List<FinanceTransaction> entries, String type) {
        return entries.stream()
                .filter(item -> type.equals(item.getTransactionType()))
                .map(FinanceTransaction::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }
}
