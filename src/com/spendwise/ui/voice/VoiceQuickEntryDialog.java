package com.spendwise.ui.voice;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.service.AccountService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.QuickEntryResult;
import com.spendwise.service.QuickEntryService;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import com.spendwise.voice.SpeechRecognitionProvider;
import com.spendwise.voice.SpeechRecognitionResult;
import com.spendwise.voice.VoiceCaptureService;
import com.spendwise.voice.VoiceEntrySettings;
import com.spendwise.voice.VoiceParseResult;
import com.spendwise.voice.VoiceTransactionDraft;
import com.spendwise.voice.VoiceTransactionParser;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public final class VoiceQuickEntryDialog extends JDialog {

    private static final String LISTENING = "listening";
    private static final String TRANSCRIPT = "transcript";
    private static final String REVIEW = "review";

    private final VoiceCaptureService captureService;
    private final QuickEntryService quickEntryService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final VoiceEntrySettings settings;
    private final Runnable successListener;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final VoiceListeningPanel listeningPanel;
    private final VoiceTranscriptPanel transcriptPanel;
    private final VoiceDraftReviewPanel reviewPanel;
    private List<Account> accounts = List.of();
    private List<Category> categories = List.of();
    private SwingWorker<SpeechRecognitionResult, Void> captureWorker;

    public VoiceQuickEntryDialog(
            Window owner,
            VoiceCaptureService captureService,
            QuickEntryService quickEntryService,
            AccountService accountService,
            CategoryService categoryService,
            VoiceEntrySettings settings,
            Runnable successListener) {
        super(owner, "Voice Quick Entry", Dialog.ModalityType.APPLICATION_MODAL);
        this.captureService = Objects.requireNonNull(captureService);
        this.quickEntryService = Objects.requireNonNull(quickEntryService);
        this.accountService = Objects.requireNonNull(accountService);
        this.categoryService = Objects.requireNonNull(categoryService);
        this.settings = Objects.requireNonNull(settings);
        this.successListener = Objects.requireNonNull(successListener);
        listeningPanel = new VoiceListeningPanel(
                this::stopListening, this::startListening,
                this::showManualEntry, this::cancel);
        transcriptPanel = new VoiceTranscriptPanel(settings,
                this::startListening, this::parseCommand, this::cancel);
        reviewPanel = new VoiceDraftReviewPanel(
                this::confirm, this::showManualEntry, this::cancel);
        buildInterface();
    }

    public void open() {
        requireEventDispatchThread();
        accounts = accountService.listSelectableAccounts();
        categories = categoryService.listSelectableCategories();
        transcriptPanel.setTranscript("");
        SpeechRecognitionProvider provider = captureService.getProvider();
        transcriptPanel.setProviderStatus(
                "Speech provider: " + provider.getDisplayName()
                + " · " + provider.getStatus());
        transcriptPanel.setListeningAvailable(
                settings.isEnabled() && provider.isConfigured());
        if (settings.isEnabled() && provider.isConfigured()) {
            startListening();
        } else {
            showManualEntry();
        }
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    private void buildInterface() {
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        content.add(listeningPanel, LISTENING);
        content.add(transcriptPanel, TRANSCRIPT);
        content.add(reviewPanel, REVIEW);
        JPanel root = new JPanel(new BorderLayout());
        root.add(content, BorderLayout.CENTER);
        JLabel privacy = new JLabel(
                "Privacy: audio is not stored. A transaction is created only after Confirm and Add.");
        privacy.setFont(AppFonts.caption());
        AppTheme.mark(privacy, AppTheme.SECONDARY_TEXT_ROLE);
        privacy.setBorder(BorderFactory.createEmptyBorder(6, 20, 10, 20));
        root.add(privacy, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(820, 680);
        setMinimumSize(new Dimension(700, 560));
    }

    private void startListening() {
        if (!settings.isEnabled()) {
            transcriptPanel.setProviderStatus(
                    "Voice Quick Entry is disabled in Settings. Manual parsing remains available.");
            showManualEntry();
            return;
        }
        if (!captureService.getProvider().isConfigured()) {
            transcriptPanel.setProviderStatus(captureService.getProvider().getStatus());
            showManualEntry();
            return;
        }
        stopActiveWorker();
        listeningPanel.showListening();
        cards.show(content, LISTENING);
        captureWorker = new SwingWorker<>() {
            @Override
            protected SpeechRecognitionResult doInBackground() {
                return captureService.capture();
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    SpeechRecognitionResult result = get();
                    transcriptPanel.setTranscript(result.transcript());
                    parseCommand(result.transcript());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    listeningPanel.showStatus(
                            "Listening interrupted", "Retry or use manual entry.");
                } catch (ExecutionException exception) {
                    listeningPanel.showStatus(
                            "Speech unavailable", safeMessage(exception.getCause()));
                }
            }
        };
        captureWorker.execute();
    }

    private void stopListening() {
        captureService.stop();
        stopActiveWorker();
        listeningPanel.showStatus(
                "Listening stopped", "Retry or continue with manual entry.");
    }

    private void showManualEntry() {
        cards.show(content, TRANSCRIPT);
        SwingUtilities.invokeLater(transcriptPanel::focusTranscript);
    }

    private void parseCommand(String command) {
        VoiceTransactionParser parser = new VoiceTransactionParser(
                accounts, categories);
        VoiceParseResult result = parser.parse(command);
        reviewPanel.load(result, accounts, categories);
        cards.show(content, REVIEW);
    }

    private void confirm(VoiceTransactionDraft draft) {
        try {
            QuickEntryResult result = quickEntryService.confirmVoiceDraft(draft);
            successListener.run();
            setVisible(false);
            JOptionPane.showMessageDialog(getOwner(),
                    "Transaction added after review. Reference: "
                    + result.identifier(),
                    "Voice Quick Entry", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, safeMessage(exception),
                    "Unable to Add Transaction", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancel() {
        captureService.stop();
        stopActiveWorker();
        setVisible(false);
    }

    private void stopActiveWorker() {
        if (captureWorker != null && !captureWorker.isDone()) {
            captureWorker.cancel(true);
        }
        captureWorker = null;
    }

    private static String safeMessage(Throwable exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.isBlank()
                ? "Voice Quick Entry could not be completed safely."
                : message;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "VoiceQuickEntryDialog must be used on the Event Dispatch Thread.");
        }
    }
}
