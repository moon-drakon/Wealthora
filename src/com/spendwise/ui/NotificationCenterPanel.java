package com.spendwise.ui;

import com.spendwise.repository.RepositoryException;
import com.spendwise.service.FinanceNotification;
import com.spendwise.service.FinanceNotificationService;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledTable;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

public final class NotificationCenterPanel extends JPanel {
    private final FinanceNotificationService service;
    private final NotificationModel model = new NotificationModel();
    private final JLabel status = new JLabel("Loading reminders...");

    public NotificationCenterPanel(FinanceNotificationService service) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException(
                "NotificationCenterPanel must be used on the Event Dispatch Thread.");
        this.service = Objects.requireNonNull(service);
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        AppTheme.mark(this, AppTheme.PAGE_ROLE);
        JLabel title = new JLabel("Notifications & Reminders");
        title.setFont(AppFonts.pageTitle());
        SecondaryButton refresh = new SecondaryButton("Refresh");
        refresh.addActionListener(event -> refreshNotifications());
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(refresh);
        heading.add(actions, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        add(new JScrollPane(new StyledTable(model)), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        refreshNotifications();
    }

    public void refreshNotifications() {
        try {
            List<FinanceNotification> items = service.listNotifications(
                    LocalDate.now());
            model.replace(items);
            status.setText(items.isEmpty()
                    ? "You are all caught up. No current reminders."
                    : items.size() + " active reminder(s) from real finance data.");
        } catch (ValidationException | RepositoryException exception) {
            status.setText("Could not refresh reminders: " + exception.getMessage());
        }
    }

    private static final class NotificationModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "Priority", "Type", "Title", "Message", "Due Date"};
        private List<FinanceNotification> rows = List.of();
        void replace(List<FinanceNotification> values) {
            rows = List.copyOf(values); fireTableDataChanged();
        }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }
        @Override public Object getValueAt(int row, int column) {
            FinanceNotification item = rows.get(row);
            return switch (column) {
                case 0 -> item.severity();
                case 1 -> item.type().replace('_', ' ');
                case 2 -> item.title();
                case 3 -> item.message();
                case 4 -> item.dueDate() == null ? "—" : item.dueDate();
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
        @Override public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
