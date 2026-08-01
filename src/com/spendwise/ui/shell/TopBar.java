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
        searchArea.setMaximumSize(new Dimension(520, 40));
        searchField.addActionListener(event -> submitSearch());
        add(searchArea, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        PrimaryButton quickButton = new PrimaryButton("Quick Transaction");
        quickButton.setIcon(AppIcons.icon(AppIcons.Type.ADD, 15));
        quickButton.setMnemonic('Q');
        quickButton.addActionListener(event -> quickEntryListener.run());
        themeButton.setIcon(AppIcons.icon(AppIcons.Type.THEME, 15));
        themeButton.setToolTipText("Switch between light and dark themes");
        themeButton.addActionListener(event -> {
            this.themeListener.run();
            refreshThemeText();
        });
        JLabel profile = new JLabel("Local workspace");
        profile.setFont(AppFonts.caption());
        profile.setToolTipText(
                "Authentication will be connected when a real backend exists.");
        AppTheme.mark(profile, AppTheme.SECONDARY_TEXT_ROLE);
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

    private void refreshThemeText() {
        themeButton.setText(AppTheme.isDarkMode() ? "Light" : "Dark");
    }
}
