package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.CardType;
import com.spendwise.model.PaymentCard;
import com.spendwise.service.AccountService;
import com.spendwise.service.CurrencyService;
import com.spendwise.service.PaymentCardService;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledTable;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

public final class CardsPanel extends JPanel {

    private final PaymentCardService cardService;
    private final AccountService accountService;
    private final CurrencyService currencyService;
    private final Runnable changeListener;
    private final PaymentCardTableModel tableModel = new PaymentCardTableModel();
    private final StyledTable table = new StyledTable(tableModel);
    private final JLabel currencyLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");

    public CardsPanel(
            PaymentCardService cardService,
            AccountService accountService,
            CurrencyService currencyService,
            Runnable changeListener) {
        super(new BorderLayout(0, 12));
        this.cardService = Objects.requireNonNull(cardService);
        this.accountService = Objects.requireNonNull(accountService);
        this.currencyService = Objects.requireNonNull(currencyService);
        this.changeListener = changeListener == null ? () -> { } : changeListener;
        AppTheme.mark(this, AppTheme.PAGE_ROLE);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));
        buildInterface();
        refreshCards();
    }

    public void refreshCards() {
        tableModel.replace(cardService.listAll());
        currencyLabel.setText("Base currency: "
                + currencyService.getCurrency().getCurrencyCode());
        statusLabel.setText(tableModel.getRowCount() + " saved card profile(s). ");
    }

    private void buildInterface() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel guidance = new JLabel(
                "Only card metadata and the last four digits are stored.");
        guidance.setFont(AppFonts.body());
        header.add(guidance, BorderLayout.WEST);
        JPanel currency = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        currency.setOpaque(false);
        currency.add(currencyLabel);
        SecondaryButton changeCurrency = new SecondaryButton("Change Currency");
        changeCurrency.addActionListener(event -> changeCurrency());
        currency.add(changeCurrency);
        header.add(currency, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(statusLabel, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        PrimaryButton add = new PrimaryButton("Add Card Profile");
        add.addActionListener(event -> editCard(null));
        SecondaryButton edit = new SecondaryButton("Edit Selected");
        edit.addActionListener(event -> editSelected());
        SecondaryButton status = new SecondaryButton("Activate / Deactivate");
        status.addActionListener(event -> toggleSelected());
        actions.add(add);
        actions.add(edit);
        actions.add(status);
        bottom.add(actions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    private void editSelected() {
        PaymentCard card = selectedCard();
        if (card == null) {
            showMessage("Select a card profile to edit.");
            return;
        }
        editCard(card);
    }

    private void editCard(PaymentCard existing) {
        List<Account> accounts = accountService.listSelectableAccounts();
        JTextField name = new JTextField(existing == null
                ? "" : existing.getDisplayName());
        JTextField bank = new JTextField(existing == null
                ? "" : existing.getBankName());
        JComboBox<CardType> type = new JComboBox<>(CardType.values());
        JTextField lastFour = new JTextField(existing == null
                ? "" : existing.getLastFourDigits());
        JTextField limit = new JTextField(existing == null
                ? "" : existing.getCreditLimit()
                        .map(BigDecimal::toPlainString).orElse(""));
        JTextField billing = new JTextField(existing == null
                ? "" : existing.getBillingDay().map(String::valueOf).orElse(""));
        JTextField due = new JTextField(existing == null
                ? "" : existing.getDueDay().map(String::valueOf).orElse(""));
        JComboBox<Account> cardAccount = new JComboBox<>(
                accounts.toArray(Account[]::new));
        JComboBox<Object> paymentAccount = new JComboBox<>();
        paymentAccount.addItem("None");
        accounts.forEach(paymentAccount::addItem);
        if (existing != null) {
            type.setSelectedItem(existing.getCardType());
            cardAccount.setSelectedItem(existing.getCardAccount());
            existing.getLinkedPaymentAccount().ifPresent(
                    paymentAccount::setSelectedItem);
        }
        JPanel form = form(
                "Card name", name, "Bank name", bank, "Card type", type,
                "Last four digits", lastFour, "Credit limit", limit,
                "Billing day", billing, "Due day", due,
                "Card account", cardAccount,
                "Linked payment account", paymentAccount);
        if (JOptionPane.showConfirmDialog(this, form,
                existing == null ? "Add Card Profile" : "Edit Card Profile",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            CardType selectedType = (CardType) type.getSelectedItem();
            BigDecimal creditLimit = selectedType == CardType.CREDIT
                    ? new BigDecimal(limit.getText().strip()) : null;
            Integer billingDay = selectedType == CardType.CREDIT
                    ? Integer.valueOf(billing.getText().strip()) : null;
            Integer dueDay = selectedType == CardType.CREDIT
                    ? Integer.valueOf(due.getText().strip()) : null;
            Account linked = paymentAccount.getSelectedItem() instanceof Account account
                    ? account : null;
            if (existing == null) {
                cardService.addCard(name.getText(), bank.getText(), selectedType,
                        lastFour.getText().strip(), creditLimit, billingDay, dueDay,
                        (Account) cardAccount.getSelectedItem(), linked);
            } else {
                cardService.updateCard(existing.getIdentifier(), name.getText(),
                        bank.getText(), selectedType, lastFour.getText().strip(),
                        creditLimit, billingDay, dueDay,
                        (Account) cardAccount.getSelectedItem(), linked);
            }
            refreshCards();
            changeListener.run();
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void toggleSelected() {
        PaymentCard card = selectedCard();
        if (card == null) {
            showMessage("Select a card profile first.");
            return;
        }
        try {
            cardService.setActive(card.getIdentifier(), !card.isActive());
            refreshCards();
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void changeCurrency() {
        String code = JOptionPane.showInputDialog(this,
                "ISO 4217 currency code:",
                currencyService.getCurrency().getCurrencyCode());
        if (code == null) {
            return;
        }
        try {
            currencyService.setCurrency(code);
            refreshCards();
            changeListener.run();
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private PaymentCard selectedCard() {
        int row = table.getSelectedRow();
        return row < 0 ? null : tableModel.getCardAt(
                table.convertRowIndexToModel(row));
    }

    private void showError(RuntimeException exception) {
        String message = exception.getMessage() == null
                ? "The card operation failed safely." : exception.getMessage();
        statusLabel.setText(message);
        JOptionPane.showMessageDialog(this, message,
                "Card Operation Failed", JOptionPane.ERROR_MESSAGE);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message,
                "SpendWise Cards", JOptionPane.INFORMATION_MESSAGE);
    }

    private static JPanel form(Object... fields) {
        JPanel panel = new JPanel(new GridLayout(fields.length / 2, 2, 8, 8));
        for (int index = 0; index < fields.length; index += 2) {
            panel.add(new JLabel(fields[index] + ":"));
            panel.add((java.awt.Component) fields[index + 1]);
        }
        return panel;
    }
}
