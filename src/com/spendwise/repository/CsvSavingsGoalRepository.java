package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.GoalContribution;
import com.spendwise.model.SavingsGoal;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class CsvSavingsGoalRepository implements SavingsGoalRepository {

    public static final String HEADER = "recordType,id,goalId,name,targetAmount,"
            + "targetDate,account,date,amount,note,status";
    private static final List<String> HEADER_FIELDS = List.of(
            "recordType", "id", "goalId", "name", "targetAmount",
            "targetDate", "account", "date", "amount", "note", "status");
    private final Path csvPath;
    private final Function<String, Account> accountResolver;

    public CsvSavingsGoalRepository(
            Path csvPath, Function<String, Account> accountResolver) {
        this.csvPath = Objects.requireNonNull(csvPath)
                .toAbsolutePath().normalize();
        this.accountResolver = Objects.requireNonNull(accountResolver);
    }

    @Override
    public List<SavingsGoal> findAllGoals() {
        return read().goals();
    }

    @Override
    public Optional<SavingsGoal> findGoalById(String identifier) {
        return findAllGoals().stream().filter(goal ->
                goal.getIdentifier().equals(identifier)).findFirst();
    }

    @Override
    public List<GoalContribution> findContributions(String goalIdentifier) {
        return read().contributions().stream()
                .filter(item -> item.getGoalIdentifier().equals(goalIdentifier))
                .sorted(java.util.Comparator
                        .comparing(GoalContribution::getDate)
                        .thenComparing(GoalContribution::getIdentifier))
                .toList();
    }

    @Override
    public void addGoal(SavingsGoal goal) {
        SavingsGoal required = Objects.requireNonNull(goal);
        Snapshot snapshot = read();
        if (snapshot.goals().stream().anyMatch(item -> item.getIdentifier()
                .equals(required.getIdentifier()))) {
            throw new RepositoryException("Savings goal ID already exists.");
        }
        List<SavingsGoal> goals = new ArrayList<>(snapshot.goals());
        goals.add(required);
        write(new Snapshot(goals, snapshot.contributions()));
    }

    @Override
    public void updateGoal(SavingsGoal goal) {
        SavingsGoal required = Objects.requireNonNull(goal);
        Snapshot snapshot = read();
        List<SavingsGoal> goals = new ArrayList<>(snapshot.goals());
        int index = indexOf(goals, required.getIdentifier());
        if (index < 0) {
            throw new RepositoryException("Savings goal was not found.");
        }
        goals.set(index, required);
        write(new Snapshot(goals, snapshot.contributions()));
    }

    @Override
    public void addContribution(GoalContribution contribution) {
        GoalContribution required = Objects.requireNonNull(contribution);
        Snapshot snapshot = read();
        if (snapshot.goals().stream().noneMatch(goal -> goal.getIdentifier()
                .equals(required.getGoalIdentifier()))) {
            throw new RepositoryException(
                    "Contribution references an unknown savings goal.");
        }
        if (snapshot.contributions().stream().anyMatch(item ->
                item.getIdentifier().equals(required.getIdentifier()))) {
            throw new RepositoryException("Contribution ID already exists.");
        }
        List<GoalContribution> contributions =
                new ArrayList<>(snapshot.contributions());
        contributions.add(required);
        write(new Snapshot(snapshot.goals(), contributions));
    }

    private Snapshot read() {
        Optional<String> content = CsvFileSupport.read(csvPath, "savings goal");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return new Snapshot(List.of(), List.of());
        }
        List<List<String>> records = CsvFileSupport.parse(
                content.orElseThrow(), HEADER_FIELDS, "Savings goal");
        List<SavingsGoal> goals = new ArrayList<>();
        List<GoalContribution> contributions = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> fields = records.get(index);
            if (fields.size() != 11) {
                throw corrupt(index + 1, "must contain exactly 11 columns.", null);
            }
            try {
                if (!identifiers.add(fields.get(1))) {
                    throw corrupt(index + 1, "duplicates a record ID.", null);
                }
                switch (fields.get(0)) {
                    case "GOAL" -> goals.add(new SavingsGoal(
                            fields.get(1), fields.get(3),
                            new BigDecimal(fields.get(4)),
                            LocalDate.parse(fields.get(5)),
                            accountResolver.apply(fields.get(6)),
                            parseStatus(fields.get(10))));
                    case "CONTRIBUTION" -> contributions.add(
                            new GoalContribution(fields.get(1), fields.get(2),
                                    LocalDate.parse(fields.get(7)),
                                    new BigDecimal(fields.get(8)), fields.get(9)));
                    default -> throw corrupt(index + 1,
                            "has an unsupported record type.", null);
                }
            } catch (RepositoryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw corrupt(index + 1,
                        "contains invalid data: " + safeMessage(exception),
                        exception);
            }
        }
        Set<String> goalIds = goals.stream().map(SavingsGoal::getIdentifier)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (contributions.stream().anyMatch(item ->
                !goalIds.contains(item.getGoalIdentifier()))) {
            throw new RepositoryException(
                    "Savings data contains an orphan contribution.");
        }
        return new Snapshot(goals, contributions);
    }

    private void write(Snapshot snapshot) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        snapshot.goals().stream().sorted(java.util.Comparator
                .comparing(SavingsGoal::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SavingsGoal::getIdentifier))
                .forEach(goal -> {
                    append(csv, "GOAL"); append(csv, goal.getIdentifier());
                    append(csv, ""); append(csv, goal.getName());
                    append(csv, goal.getTargetAmount().toPlainString());
                    append(csv, goal.getTargetDate().toString());
                    append(csv, goal.getLinkedAccount().getIdentifier());
                    append(csv, ""); append(csv, ""); append(csv, "");
                    CsvFileSupport.appendField(csv,
                            goal.isActive() ? "ACTIVE" : "ARCHIVED");
                    csv.append('\n');
                });
        snapshot.contributions().stream().sorted(java.util.Comparator
                .comparing(GoalContribution::getDate)
                .thenComparing(GoalContribution::getIdentifier))
                .forEach(item -> {
                    append(csv, "CONTRIBUTION");
                    append(csv, item.getIdentifier());
                    append(csv, item.getGoalIdentifier());
                    append(csv, ""); append(csv, ""); append(csv, "");
                    append(csv, ""); append(csv, item.getDate().toString());
                    append(csv, item.getAmount().toPlainString());
                    append(csv, item.getNote());
                    CsvFileSupport.appendField(csv, "");
                    csv.append('\n');
                });
        CsvFileSupport.write(csvPath, ".spendwise-savings-goals-",
                csv.toString(), "savings goal");
    }

    private static int indexOf(List<SavingsGoal> goals, String id) {
        for (int index = 0; index < goals.size(); index++) {
            if (goals.get(index).getIdentifier().equals(id)) return index;
        }
        return -1;
    }

    private static boolean parseStatus(String text) {
        return switch (text) {
            case "ACTIVE" -> true;
            case "ARCHIVED" -> false;
            default -> throw new IllegalArgumentException("Invalid goal status.");
        };
    }

    private static void append(StringBuilder csv, String value) {
        CsvFileSupport.appendField(csv, value);
        csv.append(',');
    }

    private static RepositoryException corrupt(
            int record, String detail, RuntimeException cause) {
        String message = "Savings goal CSV record " + record + " " + detail;
        return cause == null ? new RepositoryException(message)
                : new RepositoryException(message, cause);
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "invalid value"
                : exception.getMessage();
    }

    private record Snapshot(
            List<SavingsGoal> goals,
            List<GoalContribution> contributions) {
        Snapshot {
            goals = List.copyOf(goals);
            contributions = List.copyOf(contributions);
        }
    }
}
