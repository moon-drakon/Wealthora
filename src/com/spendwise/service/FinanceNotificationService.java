package com.spendwise.service;

import com.spendwise.model.CardType;
import com.spendwise.model.PaymentCard;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FinanceNotificationService {
    private final RecurringService recurringService;
    private final PaymentCardService paymentCardService;
    private final ExpenseAnalyticsService analyticsService;
    private final BudgetService monthlyBudgetService;
    private final AdvancedBudgetService advancedBudgetService;
    private final DebtService debtService;

    public FinanceNotificationService(
            RecurringService recurringService,
            PaymentCardService paymentCardService,
            ExpenseAnalyticsService analyticsService,
            BudgetService monthlyBudgetService,
            AdvancedBudgetService advancedBudgetService,
            DebtService debtService) {
        this.recurringService = Objects.requireNonNull(recurringService);
        this.paymentCardService = Objects.requireNonNull(paymentCardService);
        this.analyticsService = Objects.requireNonNull(analyticsService);
        this.monthlyBudgetService = Objects.requireNonNull(monthlyBudgetService);
        this.advancedBudgetService = Objects.requireNonNull(advancedBudgetService);
        this.debtService = Objects.requireNonNull(debtService);
    }

    public List<FinanceNotification> listNotifications(LocalDate referenceDate) {
        LocalDate reference = Objects.requireNonNull(referenceDate);
        List<FinanceNotification> result = new ArrayList<>();
        addRecurring(result, reference);
        addCardDueDates(result, reference);
        addBudgetWarnings(result, reference);
        addDebtWarnings(result, reference);
        result.sort(Comparator
                .comparing(FinanceNotification::severity).reversed()
                .thenComparing(item -> item.dueDate() == null
                        ? LocalDate.MAX : item.dueDate())
                .thenComparing(FinanceNotification::title));
        return List.copyOf(result);
    }

    private void addRecurring(
            List<FinanceNotification> result, LocalDate reference) {
        for (UpcomingRecurringItem item
                : recurringService.findUpcoming(reference, 365)) {
            String type = item.definition().getKind().name();
            result.add(new FinanceNotification(
                    type,
                    item.daysUntilDue() == 0
                            ? NotificationSeverity.CRITICAL
                            : NotificationSeverity.WARNING,
                    item.definition().getDescription(),
                    "Due in " + item.daysUntilDue() + " day(s): "
                    + item.definition().getAmount().toPlainString(),
                    item.dueDate()));
        }
    }

    private void addCardDueDates(
            List<FinanceNotification> result, LocalDate reference) {
        for (PaymentCard card : paymentCardService.listAll()) {
            if (!card.isActive() || card.getCardType() != CardType.CREDIT) {
                continue;
            }
            LocalDate due = nextDayOfMonth(reference,
                    card.getDueDay().orElseThrow());
            if (due.isAfter(reference.plusDays(7))) continue;
            result.add(new FinanceNotification(
                    "CREDIT_CARD", NotificationSeverity.WARNING,
                    card.getDisplayName() + " payment due",
                    card.getBankName() + " card ending "
                    + card.getLastFourDigits(), due));
        }
    }

    private void addBudgetWarnings(
            List<FinanceNotification> result, LocalDate reference) {
        BudgetStatusSnapshot monthly = monthlyBudgetService.evaluate(
                analyticsService.analyzeMonth(YearMonth.from(reference)));
        addBudgetAlert(result, "Monthly budget",
                monthly.getHighestActiveAlertLevel(), null);
        for (var plan : advancedBudgetService.listActiveOn(reference)) {
            BudgetPlanStatus status = advancedBudgetService.evaluate(
                    plan.getIdentifier());
            addBudgetAlert(result, plan.getName(),
                    status.getHighestAlertLevel(), plan.getEndDate());
        }
    }

    private static void addBudgetAlert(
            List<FinanceNotification> result, String name,
            BudgetAlertLevel level, LocalDate dueDate) {
        if (level == BudgetAlertLevel.NOT_SET
                || level == BudgetAlertLevel.WITHIN_LIMIT) return;
        NotificationSeverity severity = switch (level) {
            case NEAR_LIMIT -> NotificationSeverity.WARNING;
            case LIMIT_REACHED, OVER_LIMIT -> NotificationSeverity.CRITICAL;
            default -> NotificationSeverity.INFO;
        };
        result.add(new FinanceNotification(
                "BUDGET", severity, name,
                "Budget status: " + level.name().replace('_', ' ').toLowerCase(),
                dueDate));
    }

    private void addDebtWarnings(
            List<FinanceNotification> result, LocalDate reference) {
        for (DebtProgress debt : debtService.listProgress(reference)) {
            if (debt.status() != com.spendwise.model.DebtStatus.OVERDUE) continue;
            result.add(new FinanceNotification(
                    "DEBT", NotificationSeverity.CRITICAL,
                    "Overdue " + debt.debt().getDirection().toString().toLowerCase()
                    + " balance",
                    debt.debt().getCounterparty() + ": "
                    + debt.remainingAmount().toPlainString(),
                    debt.debt().getDueDate()));
        }
    }

    private static LocalDate nextDayOfMonth(LocalDate reference, int day) {
        YearMonth month = YearMonth.from(reference);
        LocalDate candidate = month.atDay(Math.min(day, month.lengthOfMonth()));
        if (candidate.isBefore(reference)) {
            month = month.plusMonths(1);
            candidate = month.atDay(Math.min(day, month.lengthOfMonth()));
        }
        return candidate;
    }
}
