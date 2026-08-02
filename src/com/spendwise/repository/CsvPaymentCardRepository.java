package com.spendwise.repository;

import com.spendwise.model.Account;
import com.spendwise.model.CardType;
import com.spendwise.model.PaymentCard;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class CsvPaymentCardRepository implements PaymentCardRepository {

    public static final String HEADER = "id,name,bank,type,lastFour,creditLimit,"
            + "billingDay,dueDay,cardAccount,paymentAccount,status";
    private static final List<String> HEADER_FIELDS = List.of(
            "id", "name", "bank", "type", "lastFour", "creditLimit",
            "billingDay", "dueDay", "cardAccount", "paymentAccount", "status");

    private final java.nio.file.Path csvPath;
    private final Function<String, Account> accountResolver;

    public CsvPaymentCardRepository(
            java.nio.file.Path csvPath,
            Function<String, Account> accountResolver) {
        this.csvPath = Objects.requireNonNull(csvPath).toAbsolutePath().normalize();
        this.accountResolver = Objects.requireNonNull(accountResolver);
    }

    @Override
    public List<PaymentCard> findAll() {
        Optional<String> content = CsvFileSupport.read(csvPath, "payment card");
        if (content.isEmpty() || content.orElseThrow().isEmpty()) {
            return List.of();
        }
        List<List<String>> records = CsvFileSupport.parse(
                content.orElseThrow(), HEADER_FIELDS, "Payment card");
        List<PaymentCard> cards = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> fields = records.get(index);
            if (fields.size() != 11) {
                throw new RepositoryException(
                        "Payment card CSV record " + (index + 1)
                        + " must contain exactly 11 columns.");
            }
            try {
                PaymentCard card = new PaymentCard(
                        fields.get(0), fields.get(1), fields.get(2),
                        CardType.valueOf(fields.get(3)), fields.get(4),
                        decimalOrNull(fields.get(5)), integerOrNull(fields.get(6)),
                        integerOrNull(fields.get(7)),
                        accountResolver.apply(fields.get(8)),
                        fields.get(9).isEmpty() ? null
                                : accountResolver.apply(fields.get(9)),
                        parseActive(fields.get(10)));
                if (!identifiers.add(card.getIdentifier())) {
                    throw new RepositoryException(
                            "Duplicate payment card ID: " + card.getIdentifier());
                }
                cards.add(card);
            } catch (RepositoryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new RepositoryException(
                        "Payment card CSV record " + (index + 1)
                        + " contains invalid data: " + exception.getMessage(), exception);
            }
        }
        return List.copyOf(cards);
    }

    @Override
    public Optional<PaymentCard> findById(String identifier) {
        return findAll().stream().filter(card ->
                card.getIdentifier().equals(identifier)).findFirst();
    }

    @Override
    public void add(PaymentCard card) {
        PaymentCard required = Objects.requireNonNull(card);
        List<PaymentCard> cards = new ArrayList<>(findAll());
        if (findIndex(cards, required.getIdentifier()) >= 0) {
            throw new RepositoryException("Payment card ID already exists.");
        }
        cards.add(required);
        write(cards);
    }

    @Override
    public void update(PaymentCard card) {
        PaymentCard required = Objects.requireNonNull(card);
        List<PaymentCard> cards = new ArrayList<>(findAll());
        int index = findIndex(cards, required.getIdentifier());
        if (index < 0) {
            throw new RepositoryException("Payment card was not found.");
        }
        cards.set(index, required);
        write(cards);
    }

    private void write(List<PaymentCard> cards) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (PaymentCard card : cards) {
            append(csv, card.getIdentifier());
            append(csv, card.getDisplayName());
            append(csv, card.getBankName());
            append(csv, card.getCardType().name());
            append(csv, card.getLastFourDigits());
            append(csv, card.getCreditLimit().map(BigDecimal::toPlainString).orElse(""));
            append(csv, card.getBillingDay().map(String::valueOf).orElse(""));
            append(csv, card.getDueDay().map(String::valueOf).orElse(""));
            append(csv, card.getCardAccount().getIdentifier());
            append(csv, card.getLinkedPaymentAccount()
                    .map(Account::getIdentifier).orElse(""));
            CsvFileSupport.appendField(csv, card.isActive() ? "ACTIVE" : "INACTIVE");
            csv.append('\n');
        }
        CsvFileSupport.write(csvPath, ".spendwise-cards-", csv.toString(),
                "payment card");
    }

    private static void append(StringBuilder csv, String value) {
        CsvFileSupport.appendField(csv, value);
        csv.append(',');
    }

    private static BigDecimal decimalOrNull(String value) {
        return value.isEmpty() ? null : new BigDecimal(value);
    }

    private static Integer integerOrNull(String value) {
        return value.isEmpty() ? null : Integer.valueOf(value);
    }

    private static boolean parseActive(String value) {
        return switch (value) {
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            default -> throw new IllegalArgumentException("Invalid card status.");
        };
    }

    private static int findIndex(List<PaymentCard> cards, String identifier) {
        for (int index = 0; index < cards.size(); index++) {
            if (cards.get(index).getIdentifier().equals(identifier)) {
                return index;
            }
        }
        return -1;
    }
}
