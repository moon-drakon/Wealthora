package com.spendwise.ui.shell;

import com.spendwise.auth.UserSession;
import com.spendwise.ui.component.AppIcons;
import com.spendwise.ui.component.NotificationBanner;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JPanel;

public final class AppShellPanel extends JPanel {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pageContainer = new JPanel(cardLayout);
    private final Sidebar sidebar = new Sidebar(this::showPage);
    private final TopBar topBar;
    private final NotificationBanner notificationBanner =
            new NotificationBanner();
    private final Map<String, PageRegistration> pages = new LinkedHashMap<>();
    private Consumer<String> globalSearchListener = ignored -> { };
    private Runnable themeChangedListener = () -> { };
    private String currentPage;

    public AppShellPanel(Runnable quickEntryListener) {
        super(new BorderLayout());
        Objects.requireNonNull(
                quickEntryListener, "Quick-entry listener is required.");
        topBar = new TopBar(
                query -> globalSearchListener.accept(query),
                quickEntryListener,
                this::toggleTheme);
        AppTheme.mark(pageContainer, AppTheme.PAGE_ROLE);
        add(sidebar, BorderLayout.WEST);

        JPanel mainArea = new JPanel(new BorderLayout());
        AppTheme.mark(mainArea, AppTheme.PAGE_ROLE);
        mainArea.add(topBar, BorderLayout.NORTH);
        mainArea.add(pageContainer, BorderLayout.CENTER);
        mainArea.add(notificationBanner, BorderLayout.SOUTH);
        add(mainArea, BorderLayout.CENTER);
    }

    public void addPage(
            String identifier,
            String title,
            AppIcons.Type iconType,
            Component component,
            Runnable onShow) {
        if (pages.containsKey(identifier)) {
            throw new IllegalArgumentException(
                    "A page with this identifier already exists: " + identifier);
        }
        PageRegistration page = new PageRegistration(
                title,
                Objects.requireNonNull(component, "Page component is required."),
                identifier,
                onShow == null ? () -> { } : onShow);
        pages.put(identifier, page);
        pageContainer.add(component, identifier);
        sidebar.addNavigationItem(
                identifier, title, AppIcons.icon(iconType, 18));
        if (currentPage == null) {
            showPage(identifier);
        }
    }

    public void addPageAlias(
            String identifier,
            String title,
            AppIcons.Type iconType,
            String targetIdentifier,
            Runnable onShow) {
        if (pages.containsKey(identifier)) {
            throw new IllegalArgumentException(
                    "A page with this identifier already exists: " + identifier);
        }
        PageRegistration target = pages.get(targetIdentifier);
        if (target == null) {
            throw new IllegalArgumentException(
                    "Unknown alias target: " + targetIdentifier);
        }
        PageRegistration page = new PageRegistration(
                title,
                target.component(),
                target.cardIdentifier(),
                onShow == null ? () -> { } : onShow);
        pages.put(identifier, page);
        sidebar.addNavigationItem(
                identifier, title, AppIcons.icon(iconType, 18));
    }

    public void addNavigationSection(String title) {
        sidebar.addSection(title);
    }

    public void showPage(String identifier) {
        PageRegistration page = pages.get(identifier);
        if (page == null) {
            throw new IllegalArgumentException("Unknown page: " + identifier);
        }
        currentPage = identifier;
        cardLayout.show(pageContainer, page.cardIdentifier());
        sidebar.select(identifier);
        topBar.setPageTitle(page.title());
        page.onShow().run();
    }

    public void setGlobalSearchListener(Consumer<String> listener) {
        globalSearchListener = Objects.requireNonNull(
                listener, "Global search listener is required.");
    }

    public void setThemeChangedListener(Runnable listener) {
        themeChangedListener = Objects.requireNonNull(
                listener, "Theme-change listener is required.");
    }

    public void showNotification(
            String message, NotificationBanner.Level level) {
        notificationBanner.showMessage(message, level);
    }

    public int getPageCount() {
        return pages.size();
    }

    public String getCurrentPage() {
        return currentPage;
    }

    public void focusGlobalSearch() {
        topBar.focusSearch();
    }

    public void configureProfile(
            UserSession session, ProfileMenuActions actions) {
        topBar.configureProfile(session, actions);
    }

    public void setDarkMode(boolean darkMode) {
        AppTheme.setDarkMode(darkMode);
        sidebar.refreshTheme();
        topBar.refreshThemeText();
        AppTheme.applyCustomColors(this);
        themeChangedListener.run();
        revalidate();
        repaint();
    }

    private void toggleTheme() {
        setDarkMode(!AppTheme.isDarkMode());
    }

    private record PageRegistration(
            String title,
            Component component,
            String cardIdentifier,
            Runnable onShow) {
    }
}
