package com.spendwise.ui.shell;

import com.spendwise.auth.UserSession;
import com.spendwise.auth.CloudConnectionState;
import com.spendwise.auth.FinanceMode;
import com.spendwise.ui.component.AppIcons;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SearchField;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Color;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;

public final class TopBar extends JPanel {

    private final JLabel titleLabel = new JLabel("Dashboard");
    private final SearchField searchField =
            new SearchField("Search all transactions", 24);
    private final SecondaryButton themeButton = new SecondaryButton("Dark");
    private final JLabel avatar = new JLabel("?", JLabel.CENTER);
    private final JButton profileButton = new SecondaryButton("Account");
    private final JLabel financeModeLabel = new JLabel("Local data");
    private final JPopupMenu profileMenu = new JPopupMenu();
    private final Consumer<String> searchListener;
    private final Runnable themeListener;

    public TopBar(
            Consumer<String> searchListener,
            Runnable quickEntryListener,
            Runnable themeListener) {
        super(new BorderLayout(18, 0));
        this.searchListener = Objects.requireNonNull(
                searchListener, "Search listener is required.");
        this.themeListener = Objects.requireNonNull(
                themeListener, "Theme listener is required.");
        Objects.requireNonNull(
                quickEntryListener, "Quick-entry listener is required.");
        AppTheme.mark(this, AppTheme.CARD_ROLE);
        setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

        titleLabel.setFont(AppFonts.pageTitle());
        AppTheme.mark(titleLabel, AppTheme.PRIMARY_TEXT_ROLE);
        add(titleLabel, BorderLayout.WEST);

        JPanel searchArea = new JPanel(new BorderLayout());
        searchArea.setOpaque(false);
        searchArea.add(searchField, BorderLayout.CENTER);
        searchArea.setMinimumSize(new Dimension(120, 38));
        searchArea.setMaximumSize(new Dimension(520, 40));
        searchField.addActionListener(event -> submitSearch());
        add(searchArea, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        PrimaryButton quickButton = new PrimaryButton("Quick Add Transaction");
        quickButton.setIcon(AppIcons.icon(AppIcons.Type.ADD, 15));
        quickButton.setMnemonic('Q');
        quickButton.addActionListener(event -> quickEntryListener.run());
        themeButton.setIcon(AppIcons.icon(AppIcons.Type.THEME, 15));
        themeButton.setToolTipText("Switch between light and dark themes");
        themeButton.addActionListener(event -> {
            this.themeListener.run();
            refreshThemeText();
        });
        JPanel profile = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        profile.setOpaque(false);
        avatar.setOpaque(true);
        avatar.setBackground(new Color(220, 239, 233));
        avatar.setForeground(new Color(25, 105, 80));
        avatar.setFont(AppFonts.caption().deriveFont(java.awt.Font.BOLD));
        avatar.setPreferredSize(new Dimension(30, 30));
        profile.add(avatar);
        profileButton.setToolTipText("Open account menu");
        profileButton.addActionListener(event -> profileMenu.show(
                profileButton, 0, profileButton.getHeight()));
        profile.add(profileButton);
        actions.add(quickButton);
        financeModeLabel.setFont(AppFonts.caption());
        financeModeLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        actions.add(financeModeLabel);
        actions.add(themeButton);
        actions.add(profile);
        add(actions, BorderLayout.EAST);
        refreshThemeText();
    }

    public void configureProfile(
            UserSession session, ProfileMenuActions actions) {
        Objects.requireNonNull(session, "User session is required.");
        Objects.requireNonNull(actions, "Profile-menu actions are required.");
        avatar.setText(initials(session.getDisplayName()));
        profileButton.setText(shortDisplayName(session.getDisplayName())
                + "  ▾");
        profileButton.setToolTipText(session.getEmail() + " · "
                + session.getUser().getHighestRole().name());
        profileMenu.removeAll();
        JMenuItem identity = new JMenuItem(session.getEmail());
        identity.setEnabled(false);
        profileMenu.add(identity);
        JMenuItem role = new JMenuItem("Role: "
                + session.getUser().getHighestRole().name());
        role.setEnabled(false);
        profileMenu.add(role);
        profileMenu.addSeparator();
        addMenuItem("My Finance", actions.openMyFinance());
        addMenuItem("My Profile", actions.openProfile());
        addMenuItem("Security and Sessions",
                actions.openSecurityAndSessions());
        if (session.canAccessAdminConsole()
                && actions.openAdminConsole() != null) {
            profileMenu.addSeparator();
            addMenuItem("Admin Console", actions.openAdminConsole());
        }
        profileMenu.addSeparator();
        addMenuItem("Switch Account", actions.switchAccount());
        addMenuItem("Sign Out", actions.signOut());
    }

    public void setPageTitle(String pageTitle) {
        titleLabel.setText(pageTitle);
    }

    public void configureFinanceMode(FinanceMode mode,
            Supplier<CloudConnectionState> connectionState) {
        Objects.requireNonNull(mode, "Finance mode is required.");
        Objects.requireNonNull(connectionState,
                "Connection-state supplier is required.");
        Runnable refresh = () -> {
            CloudConnectionState state = mode == FinanceMode.LOCAL
                    ? CloudConnectionState.OFFLINE : connectionState.get();
            financeModeLabel.setText(mode == FinanceMode.LOCAL
                    ? "Local data"
                    : mode.name() + " · " + state.getDisplayName());
            financeModeLabel.setToolTipText(mode == FinanceMode.LOCAL
                    ? "Finance records are stored on this computer."
                    : "Cloud finance data only; local data is not loaded.");
        };
        refresh.run();
        if (mode == FinanceMode.CLOUD) {
            Timer timer = new Timer(2500, event -> refresh.run());
            timer.setRepeats(true);
            timer.start();
        }
    }

    public void focusSearch() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    private void submitSearch() {
        searchListener.accept(searchField.getText());
    }

    void refreshThemeText() {
        themeButton.setText(AppTheme.isDarkMode() ? "Light" : "Dark");
    }

    private void addMenuItem(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        profileMenu.add(item);
    }

    private static String initials(String name) {
        StringBuilder initials = new StringBuilder();
        for (String part : name.strip().split("\\s+")) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.isEmpty() ? "?" : initials.toString();
    }

    private static String shortDisplayName(String name) {
        String normalized = name.strip();
        return normalized.length() <= 22
                ? normalized : normalized.substring(0, 19) + "...";
    }
}
