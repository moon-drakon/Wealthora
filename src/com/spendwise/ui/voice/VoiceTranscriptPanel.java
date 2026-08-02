package com.spendwise.ui.voice;

import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledComboBox;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import com.spendwise.voice.VoiceEntrySettings;
import com.spendwise.voice.VoiceInputLanguage;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public final class VoiceTranscriptPanel extends JPanel {

    private final JTextArea transcript = new JTextArea(5, 54);
    private final JLabel providerStatus = new JLabel(" ");
    private final SecondaryButton listen = new SecondaryButton("Start Listening");

    public VoiceTranscriptPanel(
            VoiceEntrySettings settings,
            Runnable listenAction,
            Consumer<String> parseAction,
            Runnable cancelAction) {
        super(new BorderLayout(0, 14));
        Objects.requireNonNull(settings);
        Objects.requireNonNull(listenAction);
        Objects.requireNonNull(parseAction);
        Objects.requireNonNull(cancelAction);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        AppTheme.mark(this, AppTheme.PAGE_ROLE);

        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        JLabel title = new JLabel("Voice Quick Entry");
        title.setFont(AppFonts.pageTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        JLabel detail = new JLabel(
                "Speak a command or use the same safe parser manually.");
        detail.setFont(AppFonts.body());
        AppTheme.mark(detail, AppTheme.SECONDARY_TEXT_ROLE);
        providerStatus.setFont(AppFonts.caption());
        AppTheme.mark(providerStatus, AppTheme.SECONDARY_TEXT_ROLE);
        heading.add(title, BorderLayout.NORTH);
        heading.add(detail, BorderLayout.CENTER);
        heading.add(providerStatus, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        JPanel input = new JPanel(new BorderLayout(0, 9));
        input.setOpaque(false);
        JPanel languageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        languageRow.setOpaque(false);
        JLabel languageLabel = new JLabel("Language");
        languageLabel.setFont(AppFonts.button());
        StyledComboBox<VoiceInputLanguage> language =
                new StyledComboBox<>(VoiceInputLanguage.values());
        language.setFont(AppFonts.multilingualBody());
        language.setSelectedItem(settings.getPreferredLanguage());
        language.addActionListener(event -> settings.setPreferredLanguage(
                (VoiceInputLanguage) language.getSelectedItem()));
        languageRow.add(languageLabel);
        languageRow.add(language);

        JLabel label = new JLabel(
                "Type or paste a command in English, বাংলা, or Banglish.");
        label.setFont(AppFonts.multilingualButton());
        AppTheme.mark(label, AppTheme.PRIMARY_TEXT_ROLE);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(AppFonts.multilingualBody());
        transcript.getAccessibleContext().setAccessibleName(
                "Transaction voice command transcript");
        JPanel inputHeading = new JPanel(new GridLayout(0, 1, 0, 7));
        inputHeading.setOpaque(false);
        inputHeading.add(languageRow);
        inputHeading.add(label);
        input.add(inputHeading, BorderLayout.NORTH);
        input.add(new JScrollPane(transcript), BorderLayout.CENTER);
        JLabel examples = new JLabel("<html><b>Examples</b><br>"
                + "Spent 500 taka on food from bKash today<br>"
                + "আজ বিকাশ থেকে খাবারে ৫০০ টাকা খরচ<br>"
                + "aj bkash theke food e 500 taka expense</html>");
        examples.setFont(AppFonts.multilingualCaption());
        AppTheme.mark(examples, AppTheme.SECONDARY_TEXT_ROLE);
        examples.setBorder(BorderFactory.createEmptyBorder(5, 2, 0, 2));
        input.add(examples, BorderLayout.SOUTH);
        add(input, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        listen.addActionListener(event -> listenAction.run());
        SecondaryButton clear = new SecondaryButton("Clear Transcript");
        clear.addActionListener(event -> {
            setTranscript("");
            focusTranscript();
        });
        PrimaryButton parse = new PrimaryButton("Parse Command");
        parse.addActionListener(event -> parseAction.accept(transcript.getText()));
        SecondaryButton cancel = new SecondaryButton("Cancel");
        cancel.addActionListener(event -> cancelAction.run());
        actions.add(clear);
        actions.add(listen);
        actions.add(parse);
        actions.add(cancel);
        add(actions, BorderLayout.SOUTH);
    }

    public void setProviderStatus(String status) {
        providerStatus.setText(status == null ? " " : status);
    }

    public void setListeningAvailable(boolean available) {
        listen.setEnabled(available);
        listen.setToolTipText(available ? "Start microphone recognition"
                : "Configure a real speech provider in Settings; manual parsing remains available.");
    }

    public void setTranscript(String value) {
        transcript.setText(value == null ? "" : value);
        transcript.setCaretPosition(0);
    }

    public String getTranscript() {
        return transcript.getText();
    }

    public void focusTranscript() {
        transcript.requestFocusInWindow();
    }
}
