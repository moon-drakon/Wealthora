package com.spendwise.repository;

import com.spendwise.model.BudgetPlan;
import com.spendwise.model.BudgetRolloverMode;
import com.spendwise.model.Category;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class CsvBudgetPlanRepository implements BudgetPlanRepository {

    public static final String HEADER = "id,name,startDate,endDate,scope,category,"
            + "amount,rollover,status";
    private static final List<String> HEADER_FIELDS = List.of(
            "id", "name", "startDate", "endDate", "scope", "category",
            "amount", "rollover", "status");
    private final Path csvPath;
    private final Function<String, Category> categoryResolver;

    public CsvBudgetPlanRepository(
            Path csvPath, Function<String, Category> categoryResolver) {
        this.csvPath = Objects.requireNonNull(csvPath)
                .toAbsolutePath().normalize();
        this.categoryResolver = Objects.requireNonNull(categoryResolver);
    }

    @Override
    public List<BudgetPlan> findAll() {
        Optional<String> content = CsvFileSupport.read(csvPath, "budget plan");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return List.of();
        }
        List<List<String>> records = CsvFileSupport.parse(
                content.orElseThrow(), HEADER_FIELDS, "Budget plan");
        LinkedHashMap<String, Builder> builders = new LinkedHashMap<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> fields = records.get(index);
            if (fields.size() != 9) {
                throw corrupt(index + 1, "must contain exactly 9 columns.", null);
            }
            try {
                Builder builder = builders.computeIfAbsent(fields.get(0), ignored ->
                        new Builder(fields));
                builder.verifyMetadata(fields, index + 1);
                BigDecimal amount = new BigDecimal(fields.get(6));
                switch (fields.get(4)) {
                    case "OVERALL" -> builder.setOverall(
                            fields.get(5), amount, index + 1);
                    case "CATEGORY" -> builder.setCategory(
                            fields.get(5), amount, index + 1);
                    default -> throw corrupt(index + 1,
                            "scope must be OVERALL or CATEGORY.", null);
                }
            } catch (RepositoryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw corrupt(index + 1,
                        "contains invalid data: " + safeMessage(exception),
                        exception);
            }
        }
        List<BudgetPlan> plans = builders.values().stream()
                .map(Builder::build)
                .sorted(java.util.Comparator.comparing(BudgetPlan::getStartDate)
                        .thenComparing(BudgetPlan::getIdentifier))
                .toList();
        return List.copyOf(plans);
    }

    @Override
    public Optional<BudgetPlan> findById(String identifier) {
        return findAll().stream().filter(plan ->
                plan.getIdentifier().equals(identifier)).findFirst();
    }

    @Override
    public void add(BudgetPlan plan) {
        BudgetPlan required = Objects.requireNonNull(plan);
        List<BudgetPlan> plans = new ArrayList<>(findAll());
        if (plans.stream().anyMatch(item -> item.getIdentifier()
                .equals(required.getIdentifier()))) {
            throw new RepositoryException("Budget plan ID already exists.");
        }
        plans.add(required);
        write(plans);
    }

    @Override
    public void update(BudgetPlan plan) {
        BudgetPlan required = Objects.requireNonNull(plan);
        List<BudgetPlan> plans = new ArrayList<>(findAll());
        int index = -1;
        for (int current = 0; current < plans.size(); current++) {
            if (plans.get(current).getIdentifier()
                    .equals(required.getIdentifier())) {
                index = current;
                break;
            }
        }
        if (index < 0) {
            throw new RepositoryException("Budget plan was not found.");
        }
        plans.set(index, required);
        write(plans);
    }

    private void write(List<BudgetPlan> plans) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        plans.stream().sorted(java.util.Comparator
                .comparing(BudgetPlan::getStartDate)
                .thenComparing(BudgetPlan::getIdentifier))
                .forEach(plan -> {
                    plan.getOverallLimit().ifPresent(amount -> appendRow(
                            csv, plan, "OVERALL", "", amount));
                    plan.getCategoryLimits().forEach((category, amount) ->
                            appendRow(csv, plan, "CATEGORY",
                                    category.getIdentifier(), amount));
                });
        CsvFileSupport.write(csvPath, ".spendwise-budget-plans-",
                csv.toString(), "budget plan");
    }

    private static void appendRow(
            StringBuilder csv, BudgetPlan plan, String scope,
            String category, BigDecimal amount) {
        append(csv, plan.getIdentifier());
        append(csv, plan.getName());
        append(csv, plan.getStartDate().toString());
        append(csv, plan.getEndDate().toString());
        append(csv, scope);
        append(csv, category);
        append(csv, amount.toPlainString());
        append(csv, plan.getRolloverMode().name());
        CsvFileSupport.appendField(csv, plan.isActive() ? "ACTIVE" : "ARCHIVED");
        csv.append('\n');
    }

    private static void append(StringBuilder csv, String value) {
        CsvFileSupport.appendField(csv, value);
        csv.append(',');
    }

    private static RepositoryException corrupt(
            int record, String detail, RuntimeException cause) {
        String message = "Budget plan CSV record " + record + " " + detail;
        return cause == null ? new RepositoryException(message)
                : new RepositoryException(message, cause);
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "invalid value"
                : exception.getMessage();
    }

    private final class Builder {
        private final String id;
        private final String name;
        private final LocalDate start;
        private final LocalDate end;
        private final BudgetRolloverMode rollover;
        private final boolean active;
        private BigDecimal overall;
        private final Map<Category, BigDecimal> categories =
                new LinkedHashMap<>();

        Builder(List<String> fields) {
            id = fields.get(0);
            name = fields.get(1);
            start = LocalDate.parse(fields.get(2));
            end = LocalDate.parse(fields.get(3));
            rollover = BudgetRolloverMode.valueOf(fields.get(7));
            active = switch (fields.get(8)) {
                case "ACTIVE" -> true;
                case "ARCHIVED" -> false;
                default -> throw new IllegalArgumentException(
                        "Invalid budget plan status.");
            };
        }

        void verifyMetadata(List<String> fields, int record) {
            if (!id.equals(fields.get(0)) || !name.equals(fields.get(1))
                    || !start.toString().equals(fields.get(2))
                    || !end.toString().equals(fields.get(3))
                    || !rollover.name().equals(fields.get(7))
                    || active != "ACTIVE".equals(fields.get(8))) {
                throw corrupt(record,
                        "has inconsistent metadata for budget " + id + ".", null);
            }
        }

        void setOverall(String category, BigDecimal amount, int record) {
            if (!category.isEmpty() || overall != null) {
                throw corrupt(record, "has an invalid overall limit.", null);
            }
            overall = amount;
        }

        void setCategory(String identifier, BigDecimal amount, int record) {
            if (identifier.isEmpty()) {
                throw corrupt(record, "requires a category identifier.", null);
            }
            Category category = categoryResolver.apply(identifier);
            if (categories.putIfAbsent(category, amount) != null) {
                throw corrupt(record, "duplicates a category limit.", null);
            }
        }

        BudgetPlan build() {
            return new BudgetPlan(id, name, start, end, overall,
                    categories, rollover, active);
        }
    }
}
