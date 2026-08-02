package com.spendwise.ui.shell;

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
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class TopBar extends JPanel {

    private final JLabel titleLabel = new JLabel("Dashboard");
    private final SearchField searchField =
            new SearchField("Search all transactions", 24);
    private final SecondaryButton themeButton = new SecondaryButton("Dark");
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
        JPanel profile = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 2));
        profile.setOpaque(false);
        JLabel avatar = new JLabel("SW", JLabel.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(new Color(220, 239, 233));
        avatar.setForeground(new Color(25, 105, 80));
        avatar.setFont(AppFonts.caption().deriveFont(java.awt.Font.BOLD));
        avatar.setPreferredSize(new Dimension(30, 30));
        JLabel profileLabel = new JLabel("Local profile");
        profileLabel.setFont(AppFonts.caption());
        profileLabel.setToolTipText(
                "Local development profile; no online authentication is active.");
        AppTheme.mark(profileLabel, AppTheme.SECONDARY_TEXT_ROLE);
        profile.add(avatar);
        profile.add(profileLabel);
        actions.add(quickButton);
        actions.add(themeButton);
        actions.add(profile);
        add(actions, BorderLayout.EAST);
        refreshThemeText();
    }

    public void setPageTitle(String pageTitle) {
        titleLabel.setText(pageTitle);
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
}
