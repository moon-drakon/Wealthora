package com.spendwise.ui;

import com.spendwise.repository.RepositoryException;
import com.spendwise.service.CalendarMonthSnapshot;
import com.spendwise.service.DailyActivitySnapshot;
import com.spendwise.service.FinancialReportingService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

public final class CalendarPanel extends JPanel {

    private static final String[] WEEKDAYS = {
        "Sunday", "Monday", "Tuesday", "Wednesday",
        "Thursday", "Friday", "Saturday"
    };
    private static final Color PAGE_BACKGROUND = new Color(244, 247, 250);
    private static final Color ACTIVE_DAY = new Color(220, 238, 247);
    private static final Color SELECTED_DAY = new Color(171, 211, 231);
    private static final DateTimeFormatter MONTH_TITLE =
            DateTimeFormatter.ofPattern("MMMM yyyy");

    private final FinancialReportingService reportingService;
    private final JLabel monthLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Loading calendar...");
    private final JPanel dayGrid = new JPanel(new GridLayout(6, 7, 4, 4));
    private final List<JButton> dayButtons = new ArrayList<>();
    private final FinancialActivityTableModel activityModel =
            new FinancialActivityTableModel();
    private final JTable activityTable = new JTable(activityModel);

    private YearMonth displayedMonth;
    private LocalDate selectedDate;
    private CalendarMonthSnapshot snapshot;

    public CalendarPanel(FinancialReportingService reportingService) {
        this(reportingService, YearMonth.now());
    }

    CalendarPanel(
            FinancialReportingService reportingService,
            YearMonth initialMonth) {
        requireEventDispatchThread();
        this.reportingService = Objects.requireNonNull(
                reportingService, "Financial reporting service is required.");
        this.displayedMonth = Objects.requireNonNull(
                initialMonth, "Initial calendar month is required.");
        buildInterface();
        refreshCalendar();
    }

    public void refreshCalendar() {
        requireEventDispatchThread();
        try {
            CalendarMonthSnapshot loaded =
                    reportingService.buildCalendarMonth(displayedMonth);
            applySnapshot(loaded);
        } catch (RepositoryException exception) {
            statusLabel.setText(
                    "Calendar refresh failed: " + safeMessage(exception));
        }
    }

    YearMonth getDisplayedMonth() {
        return displayedMonth;
    }

    LocalDate getSelectedDate() {
        return selectedDate;
    }

    int getFirstDayColumn() {
        return snapshot == null ? -1 : snapshot.getFirstDayColumn();
    }

    int getDetailRowCount() {
        return activityModel.getRowCount();
    }

    String getStatusText() {
        return statusLabel.getText();
    }

    void showPreviousMonth() {
        displayedMonth = displayedMonth.minusMonths(1);
        selectedDate = null;
        refreshCalendar();
    }

    void showNextMonth() {
        displayedMonth = displayedMonth.plusMonths(1);
        selectedDate = null;
        refreshCalendar();
    }

    void showCurrentMonth() {
        displayedMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        refreshCalendar();
    }

    void selectDate(LocalDate date) {
        requireEventDispatchThread();
        if (snapshot == null || !YearMonth.from(date).equals(displayedMonth)) {
            throw new IllegalArgumentException(
                    "Selected date must belong to the displayed month.");
        }
        selectedDate = date;
        updateDaySelection();
        DailyActivitySnapshot day = snapshot.getDay(date);
        activityModel.replaceEntries(day.getEntries());
        statusLabel.setText(day.hasActivity()
                ? date + " · " + day.getEntries().size()
                    + (day.getEntries().size() == 1 ? " entry" : " entries")
                : date + " · No financial activity.");
    }

    private void buildInterface() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(PAGE_BACKGROUND);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Financial Calendar");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        heading.add(title, BorderLayout.WEST);

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        navigation.setOpaque(false);
        JButton previous = new JButton("Previous");
        previous.addActionListener(event -> showPreviousMonth());
        JButton today = new JButton("Current Month");
        today.addActionListener(event -> showCurrentMonth());
        JButton next = new JButton("Next");
        next.addActionListener(event -> showNextMonth());
        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 16f));
        navigation.add(previous);
        navigation.add(monthLabel);
        navigation.add(today);
        navigation.add(next);
        heading.add(navigation, BorderLayout.EAST);

        JPanel calendar = new JPanel(new BorderLayout(0, 6));
        calendar.setOpaque(false);
        JPanel weekdayHeader = new JPanel(new GridLayout(1, 7, 4, 0));
        for (String weekday : WEEKDAYS) {
            JLabel label = new JLabel(weekday, JLabel.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            weekdayHeader.add(label);
        }
        for (int index = 0; index < 42; index++) {
            JButton dayButton = new JButton();
            dayButton.setVerticalAlignment(JButton.TOP);
            dayButton.setHorizontalAlignment(JButton.LEFT);
            dayButton.setOpaque(true);
            dayButton.setPreferredSize(new Dimension(115, 72));
            dayButtons.add(dayButton);
            dayGrid.add(dayButton);
        }
        calendar.add(weekdayHeader, BorderLayout.NORTH);
        calendar.add(dayGrid, BorderLayout.CENTER);

        activityTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        activityTable.setFillsViewportHeight(true);
        activityTable.setRowHeight(23);
        activityTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane details = new JScrollPane(activityTable);
        details.setBorder(BorderFactory.createTitledBorder("Selected Day Details"));
        details.setPreferredSize(new Dimension(900, 190));

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, calendar, details);
        split.setResizeWeight(0.7);
        split.setBorder(null);

        add(heading, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void applySnapshot(CalendarMonthSnapshot loaded) {
        snapshot = loaded;
        displayedMonth = loaded.getMonth();
        monthLabel.setText(displayedMonth.atDay(1).format(MONTH_TITLE));
        int firstColumn = loaded.getFirstDayColumn();
        for (int index = 0; index < dayButtons.size(); index++) {
            JButton button = dayButtons.get(index);
            int dayNumber = index - firstColumn + 1;
            for (var listener : button.getActionListeners()) {
                button.removeActionListener(listener);
            }
            if (dayNumber < 1 || dayNumber > displayedMonth.lengthOfMonth()) {
                button.setText("");
                button.setEnabled(false);
                button.setBackground(Color.WHITE);
                continue;
            }
            LocalDate date = displayedMonth.atDay(dayNumber);
            DailyActivitySnapshot day = loaded.getDay(date);
            button.setEnabled(true);
            button.setText(dayLabel(day));
            button.setBackground(day.hasActivity() ? ACTIVE_DAY : Color.WHITE);
            button.addActionListener(event -> selectDate(date));
            button.getAccessibleContext().setAccessibleName(
                    "Calendar day " + date);
        }
        if (selectedDate == null
                || !YearMonth.from(selectedDate).equals(displayedMonth)) {
            selectedDate = displayedMonth.equals(YearMonth.now())
                    ? LocalDate.now()
                    : displayedMonth.atDay(1);
        }
        selectDate(selectedDate);
        if (!loaded.hasActivity()) {
            statusLabel.setText(
                    displayedMonth + " · No financial activity this month.");
        }
    }

    private void updateDaySelection() {
        if (snapshot == null) {
            return;
        }
        int selectedIndex = snapshot.getFirstDayColumn()
                + selectedDate.getDayOfMonth() - 1;
        for (int index = 0; index < dayButtons.size(); index++) {
            JButton button = dayButtons.get(index);
            if (!button.isEnabled()) {
                continue;
            }
            int day = index - snapshot.getFirstDayColumn() + 1;
            boolean active = snapshot.getDay(displayedMonth.atDay(day))
                    .hasActivity();
            button.setBackground(index == selectedIndex
                    ? SELECTED_DAY
                    : active ? ACTIVE_DAY : Color.WHITE);
        }
    }

    private static String dayLabel(DailyActivitySnapshot day) {
        StringBuilder label = new StringBuilder("<html><b>")
                .append(day.getDate().getDayOfMonth())
                .append("</b>");
        if (day.hasActivity()) {
            label.append("<br>E ")
                    .append(day.getExpenseTotal().toPlainString())
                    .append(" · I ")
                    .append(day.getIncomeTotal().toPlainString())
                    .append("<br>Net ")
                    .append(day.getNetCashFlow().toPlainString());
        }
        return label.append("</html>").toString();
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Calendar data could not be loaded safely."
                : message;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "CalendarPanel must be used on the Event Dispatch Thread.");
        }
    }
}
