package com.spendwise.ui;

import com.spendwise.config.AppBrand;
import com.spendwise.model.Category;
import com.spendwise.service.CategoryService;
import com.spendwise.service.CurrencyService;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledComboBox;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import com.spendwise.voice.SpeechRecognitionProvider;
import com.spendwise.voice.UnconfiguredSpeechRecognitionProvider;
import com.spendwise.voice.VoiceEntrySettings;
import com.spendwise.voice.VoiceInputLanguage;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public final class SettingsPanel extends JPanel {

    private final CategoryService categoryService;
    private final Predicate<Category> categoryReferenceChecker;
    private final CurrencyService currencyService;
    private final Consumer<Boolean> themeChangeListener;
    private final Runnable settingsChangeListener;
    private final VoiceEntrySettings voiceSettings;
    private final SpeechRecognitionProvider speechProvider;
    private final Runnable voiceEntryAction;
    private final StyledComboBox<String> themeBox =
            new StyledComboBox<>(new String[] {"Light", "Dark"});
    private final StyledTextField currencyField =
            new StyledTextField("ISO currency code", 8);
    private final JLabel statusLabel = new JLabel(" ");
    private final JCheckBox voiceEnabled = new JCheckBox(
            "Enable Voice Quick Entry");
    private final StyledComboBox<VoiceInputLanguage> voiceLanguage =
            new StyledComboBox<>(VoiceInputLanguage.values());
    private final JCheckBox doNotStoreAudio = new JCheckBox(
            "Do not store audio");

    public SettingsPanel(
            CategoryService categoryService,
            Predicate<Category> categoryReferenceChecker,
            CurrencyService currencyService,
            Consumer<Boolean> themeChangeListener,
            Runnable settingsChangeListener) {
        this(categoryService, categoryReferenceChecker, currencyService,
                themeChangeListener, settingsChangeListener,
                new VoiceEntrySettings(),
                new UnconfiguredSpeechRecognitionProvider(), () -> { });
    }

    public SettingsPanel(
            CategoryService categoryService,
            Predicate<Category> categoryReferenceChecker,
            CurrencyService currencyService,
            Consumer<Boolean> themeChangeListener,
            Runnable settingsChangeListener,
            VoiceEntrySettings voiceSettings,
            SpeechRecognitionProvider speechProvider,
            Runnable voiceEntryAction) {
        super(new BorderLayout());
        this.categoryService = Objects.requireNonNull(categoryService);
        this.categoryReferenceChecker = Objects.requireNonNull(
                categoryReferenceChecker);
        this.currencyService = currencyService;
        this.themeChangeListener = Objects.requireNonNull(themeChangeListener);
        this.settingsChangeListener = Objects.requireNonNull(
                settingsChangeListener);
        this.voiceSettings = Objects.requireNonNull(voiceSettings);
        this.speechProvider = Objects.requireNonNull(speechProvider);
        this.voiceEntryAction = Objects.requireNonNull(voiceEntryAction);
        AppTheme.mark(this, AppTheme.PAGE_ROLE);
        buildInterface();
        refreshSettings();
    }

    public void refreshSettings() {
        themeBox.setSelectedItem(AppTheme.isDarkMode() ? "Dark" : "Light");
        currencyField.setText(currencyService == null
                ? CurrencyService.DEFAULT_CURRENCY_CODE
                : currencyService.getCurrency().getCurrencyCode());
        currencyField.setEnabled(currencyService != null);
        voiceEnabled.setSelected(voiceSettings.isEnabled());
        voiceLanguage.setFont(AppFonts.multilingualBody());
        voiceLanguage.setSelectedItem(voiceSettings.getPreferredLanguage());
        doNotStoreAudio.setSelected(voiceSettings.isDoNotStoreAudio());
    }

    private void buildInterface() {
        JPanel content = new JPanel(new BorderLayout(0, 16));
        AppTheme.mark(content, AppTheme.PAGE_ROLE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 22, 22, 22));

        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 3));
        heading.setOpaque(false);
        JLabel title = new JLabel("Settings");
        title.setFont(AppFonts.pageTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        JLabel subtitle = new JLabel(
                "Appearance, finance preferences, categories, and local data");
        subtitle.setFont(AppFonts.body());
        AppTheme.mark(subtitle, AppTheme.SECONDARY_TEXT_ROLE);
        heading.add(title);
        heading.add(subtitle);
        content.add(heading, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(0, 2, 14, 14));
        cards.setOpaque(false);
        cards.add(appearanceCard());
        cards.add(currencyCard());
        cards.add(categoriesCard());
        cards.add(dataCard());
        cards.add(privacyCard());
        if (!(speechProvider instanceof UnconfiguredSpeechRecognitionProvider)) {
            cards.add(voiceEntryCard());
        }
        cards.add(aboutCard());
        content.add(cards, BorderLayout.CENTER);

        statusLabel.setFont(AppFonts.caption());
        AppTheme.mark(statusLabel, AppTheme.SECONDARY_TEXT_ROLE);
        content.add(statusLabel, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(AppColors.pageBackground());
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel appearanceCard() {
        JPanel actions = actions();
        actions.add(themeBox);
        PrimaryButton apply = new PrimaryButton("Apply theme");
        apply.addActionListener(event -> {
            boolean dark = "Dark".equals(themeBox.getSelectedItem());
            themeChangeListener.accept(dark);
            status("Theme updated.", false);
        });
        actions.add(apply);
        return card("Appearance",
                "Choose a comfortable theme. The header switch remains available.",
                actions);
    }

    private JPanel currencyCard() {
        JPanel actions = actions();
        actions.add(currencyField);
        PrimaryButton save = new PrimaryButton("Save currency");
        save.setEnabled(currencyService != null);
        save.addActionListener(event -> saveCurrency());
        actions.add(save);
        return card("Currency",
                "Use a valid ISO 4217 code such as BDT, USD, EUR, or GBP.",
                actions);
    }

    private JPanel categoriesCard() {
        JPanel actions = actions();
        PrimaryButton manage = new PrimaryButton("Manage categories");
        manage.addActionListener(event -> openCategoryManager());
        actions.add(manage);
        return card("Categories",
                "Create, rename, archive, or restore custom expense categories.",
                actions);
    }

    private static JPanel dataCard() {
        JPanel actions = actions();
        JLabel hint = new JLabel("Use Data in the application menu");
        hint.setFont(AppFonts.button());
        AppTheme.mark(hint, AppTheme.PRIMARY_TEXT_ROLE);
        actions.add(hint);
        return card("Backup and exports",
                "Create safe backups, restore validated data, import CSV, or export reports.",
                actions);
    }

    private JPanel voiceEntryCard() {
        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 6));
        actions.setOpaque(false);
        voiceEnabled.setOpaque(false);
        doNotStoreAudio.setOpaque(false);
        actions.add(voiceEnabled);
        JPanel languageRow = actions();
        languageRow.add(new JLabel("Preferred input language"));
        languageRow.add(voiceLanguage);
        actions.add(languageRow);
        JLabel provider = new JLabel(
                "Provider: " + speechProvider.getStatus());
        provider.setFont(AppFonts.caption());
        AppTheme.mark(provider, AppTheme.SECONDARY_TEXT_ROLE);
        actions.add(provider);
        JLabel microphone = new JLabel(
                "Microphone: " + speechProvider.getMicrophoneStatus());
        microphone.setFont(AppFonts.caption());
        AppTheme.mark(microphone, AppTheme.SECONDARY_TEXT_ROLE);
        actions.add(microphone);
        doNotStoreAudio.setSelected(true);
        doNotStoreAudio.setEnabled(false);
        doNotStoreAudio.setToolTipText(
                "Required privacy safeguard: microphone audio is never stored.");
        actions.add(doNotStoreAudio);
        JPanel buttons = actions();
        SecondaryButton test = new SecondaryButton("Test microphone");
        test.addActionListener(event -> {
            test.setEnabled(false);
            status("Testing the selected microphone...", false);
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    speechProvider.refreshStatus();
                    return speechProvider.testMicrophone();
                }

                @Override
                protected void done() {
                    boolean available = false;
                    String message = speechProvider.getMicrophoneStatus();
                    try {
                        available = get();
                    } catch (Exception exception) {
                        if (exception instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        Throwable cause = exception.getCause();
                        if (cause != null && cause.getMessage() != null
                                && !cause.getMessage().isBlank()) {
                            message = cause.getMessage();
                        }
                    }
                    provider.setText("Provider: "
                            + speechProvider.getStatus());
                    microphone.setText("Microphone: "
                            + speechProvider.getMicrophoneStatus());
                    status(available ? "Microphone test completed."
                            : message, !available);
                    test.setEnabled(true);
                }
            }.execute();
        });
        SecondaryButton manual = new SecondaryButton("Manual parser test");
        manual.addActionListener(event -> voiceEntryAction.run());
        PrimaryButton save = new PrimaryButton("Save voice settings");
        save.addActionListener(event -> saveVoiceSettings());
        buttons.add(test);
        buttons.add(manual);
        buttons.add(save);
        actions.add(buttons);
        return card("Voice Entry",
                "Audio is not retained, and every parsed draft requires explicit review and confirmation.",
                actions);
    }

    private static JPanel profileCard() {
        JPanel actions = actions();
        JLabel value = new JLabel("Local account · email OTP protected");
        value.setFont(AppFonts.button());
        AppTheme.mark(value, AppTheme.PRIMARY_TEXT_ROLE);
        actions.add(value);
        return card("Profile",
                "Registration and email password recovery require a configured OTP relay.",
                actions);
    }

    private static JPanel privacyCard() {
        JPanel actions = actions();
        JLabel value = new JLabel("Project-local storage · offline by default");
        value.setFont(AppFonts.button());
        AppTheme.mark(value, AppTheme.PRIMARY_TEXT_ROLE);
        actions.add(value);
        return card("Privacy",
                AppBrand.APP_NAME
                        + " keeps its finance records in the existing local data directory.",
                actions);
    }

    private static JPanel aboutCard() {
        JPanel actions = actions();
        JLabel value = new JLabel(AppBrand.TAGLINE);
        value.setFont(AppFonts.button());
        value.setForeground(AppColors.accent());
        actions.add(value);
        return card("About " + AppBrand.APP_NAME,
                AppBrand.DESCRIPTION + ".", actions);
    }

    private static JPanel card(
            String titleText, String descriptionText, JPanel actions) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        AppTheme.mark(card, AppTheme.CARD_ROLE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.border()),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        JLabel title = new JLabel(titleText);
        title.setFont(AppFonts.sectionTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        JLabel description = new JLabel(
                "<html><body style='width:260px'>" + descriptionText
                        + "</body></html>");
        description.setFont(AppFonts.body());
        AppTheme.mark(description, AppTheme.SECONDARY_TEXT_ROLE);
        JPanel text = new JPanel(new BorderLayout(0, 7));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(description, BorderLayout.CENTER);
        card.add(text, BorderLayout.NORTH);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel actions() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        return panel;
    }

    private void saveCurrency() {
        try {
            String code = currencyService.setCurrency(currencyField.getText())
                    .getCurrencyCode();
            currencyField.setText(code);
            settingsChangeListener.run();
            status("Currency changed to " + code + ".", false);
        } catch (RuntimeException exception) {
            status(exception.getMessage(), true);
        }
    }

    private void openCategoryManager() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        CategoryManagerDialog dialog = new CategoryManagerDialog(
                owner,
                categoryService,
                categoryReferenceChecker,
                () -> {
                    settingsChangeListener.run();
                    status("Categories updated.", false);
                });
        dialog.showDialog();
    }

    private void saveVoiceSettings() {
        voiceSettings.setEnabled(voiceEnabled.isSelected());
        voiceSettings.setPreferredLanguage(
                (VoiceInputLanguage) voiceLanguage.getSelectedItem());
        voiceSettings.setDoNotStoreAudio(true);
        status("Voice Entry settings updated.", false);
    }

    private void status(String message, boolean error) {
        statusLabel.setForeground(error
                ? AppColors.expense() : AppColors.income());
        statusLabel.setText(message == null || message.isBlank()
                ? "Unable to update settings." : message);
    }
}
