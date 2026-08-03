package com.wealthora.server.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record PageResponse<T>(
            List<T> content, int page, int size, long totalElements,
            int totalPages) {
    }

    public record AccountRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 40) String accountType,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotNull BigDecimal openingBalance,
            @NotBlank @Size(max = 30) String iconName,
            @NotBlank @Size(min = 7, max = 7) String colorHex,
            @Size(max = 160) String institutionName,
            LocalDate openedOn,
            boolean archived) {
    }

    public record AccountResponse(
            String externalId, String name, String accountType,
            String currencyCode, BigDecimal openingBalance,
            BigDecimal currentBalance, String iconName, String colorHex,
            String institutionName, LocalDate openedOn, boolean archived,
            boolean defaultAccount) {
    }

    public record DefaultAccountRequest(
            @NotBlank @Size(max = 100) String externalId) {
    }

    public record DefaultAccountResponse(String externalId) {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 100) String parentExternalId,
            boolean archived) {
    }

    public record CategoryResponse(
            String externalId, String name, boolean builtIn,
            boolean archived, String parentExternalId) {
    }

    public record ExpenseRequest(
            @NotBlank @Size(max = 120) String externalId,
            @NotBlank @Size(max = 160) String description,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 100) String accountExternalId,
            @NotBlank @Size(max = 100) String categoryExternalId,
            @NotBlank @Size(max = 40) String paymentMethod,
            @NotNull List<@Size(max = 40) String> tags,
            @Size(max = 500) String note) {
    }

    public record IncomeRequest(
            @NotBlank @Size(max = 120) String externalId,
            @NotBlank @Size(max = 160) String source,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 100) String accountExternalId,
            @NotBlank @Size(max = 40) String paymentMethod,
            @NotNull List<@Size(max = 40) String> tags,
            @Size(max = 500) String note) {
    }

    public record TransactionResponse(
            String externalId, String transactionType, String description,
            BigDecimal amount, LocalDate date, String accountExternalId,
            String categoryExternalId, String paymentMethod,
            List<String> tags, String note, String transferExternalId,
            String transferDirection) {
    }

    public record TransferRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 100) String sourceAccountExternalId,
            @NotBlank @Size(max = 100) String destinationAccountExternalId,
            @NotNull List<@Size(max = 40) String> tags,
            @Size(max = 500) String note) {
    }

    public record TransferResponse(
            String externalId, BigDecimal amount, LocalDate date,
            String sourceAccountExternalId,
            String destinationAccountExternalId,
            List<String> tags, String note) {
    }

    public record MonthlyBudgetRequest(
            @NotNull YearMonth month,
            BigDecimal overallLimit,
            @NotNull Map<String, BigDecimal> categoryLimits) {
    }

    public record MonthlyBudgetResponse(
            YearMonth month, BigDecimal overallLimit,
            Map<String, BigDecimal> categoryLimits) {
    }

    public record BudgetPlanRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotBlank @Size(max = 160) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            BigDecimal overallLimit,
            @NotNull Map<String, BigDecimal> categoryLimits,
            @NotBlank @Size(max = 40) String rolloverMode,
            boolean active) {
    }

    public record BudgetPlanResponse(
            String externalId, String name, LocalDate startDate,
            LocalDate endDate, BigDecimal overallLimit,
            Map<String, BigDecimal> categoryLimits, String rolloverMode,
            boolean active) {
    }

    public record RecurringRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotBlank @Size(max = 40) String entryType,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Size(max = 160) String description,
            @Size(max = 100) String categoryExternalId,
            @NotBlank @Size(max = 100) String sourceAccountExternalId,
            @Size(max = 100) String destinationAccountExternalId,
            @NotBlank @Size(max = 40) String frequency,
            int interval,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull LocalDate nextDueDate,
            @NotBlank @Size(max = 40) String recurringKind,
            int reminderDays,
            boolean active) {
    }

    public record RecurringResponse(
            String externalId, String entryType, BigDecimal amount,
            String description, String categoryExternalId,
            String sourceAccountExternalId,
            String destinationAccountExternalId, String frequency,
            int interval, LocalDate startDate, LocalDate endDate,
            LocalDate nextDueDate, String recurringKind, int reminderDays,
            boolean active) {
    }

    public record GoalRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin(value = "0.01") BigDecimal targetAmount,
            @NotNull LocalDate targetDate,
            @NotBlank @Size(max = 100) String linkedAccountExternalId,
            boolean active) {
    }

    public record GoalResponse(
            String externalId, String name, BigDecimal targetAmount,
            LocalDate targetDate, String linkedAccountExternalId,
            boolean active, BigDecimal contributedAmount) {
    }

    public record ContributionRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotNull LocalDate date,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 500) String note) {
    }

    public record ContributionResponse(
            String externalId, String goalExternalId, LocalDate date,
            BigDecimal amount, String note) {
    }

    public record DebtRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotBlank @Size(max = 40) String direction,
            @NotBlank @Size(max = 160) String counterparty,
            @NotNull @DecimalMin(value = "0.01") BigDecimal originalAmount,
            @NotNull LocalDate dueDate,
            @Size(max = 500) String note) {
    }

    public record DebtResponse(
            String externalId, String direction, String counterparty,
            BigDecimal originalAmount, LocalDate dueDate, String note,
            BigDecimal repaidAmount, BigDecimal remainingAmount) {
    }

    public record RepaymentRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotNull LocalDate date,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 500) String note) {
    }

    public record RepaymentResponse(
            String externalId, String debtExternalId, LocalDate date,
            BigDecimal amount, String note) {
    }

    public record DashboardSummaryResponse(
            YearMonth month, BigDecimal income, BigDecimal expenses,
            BigDecimal net, BigDecimal totalBalance, long accountCount,
            long transactionCount, BigDecimal budgetLimit,
            BigDecimal budgetRemaining) {
    }
}
