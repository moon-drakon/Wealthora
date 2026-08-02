package com.spendwise.ui;

import com.spendwise.model.PaymentCard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class PaymentCardTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Card", "Bank", "Type", "Last Four", "Credit Limit",
        "Billing Day", "Due Day", "Card Account", "Payment Account", "Status"
    };
    private List<PaymentCard> cards = List.of();

    void replace(List<PaymentCard> newCards) {
        cards = List.copyOf(Objects.requireNonNull(newCards));
        fireTableDataChanged();
    }

    PaymentCard getCardAt(int row) {
        return cards.get(row);
    }

    @Override
    public int getRowCount() {
        return cards.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 4 ? BigDecimal.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        PaymentCard card = getCardAt(row);
        return switch (column) {
            case 0 -> card.getDisplayName();
            case 1 -> card.getBankName();
            case 2 -> card.getCardType().toString();
            case 3 -> "•••• " + card.getLastFourDigits();
            case 4 -> card.getCreditLimit().orElse(null);
            case 5 -> card.getBillingDay().map(String::valueOf).orElse("—");
            case 6 -> card.getDueDay().map(String::valueOf).orElse("—");
            case 7 -> card.getCardAccount().getDisplayName();
            case 8 -> card.getLinkedPaymentAccount()
                    .map(account -> account.getDisplayName()).orElse("—");
            case 9 -> card.isActive() ? "Active" : "Inactive";
            default -> throw new IndexOutOfBoundsException(column);
        };
    }
}
