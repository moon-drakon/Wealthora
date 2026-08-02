package com.spendwise.ui.voice;

import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public final class VoiceTranscriptPanel extends JPanel {

    private final JTextArea transcript = new JTextArea(7, 54);
    private final JLabel providerStatus = new JLabel(" ");

    public VoiceTranscriptPanel(
            Runnable listenAction,
            Consumer<String> parseAction,
            Runnable cancelAction) {
        super(new BorderLayout(0, 14));
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

        JPanel input = new JPanel(new BorderLayout(0, 7));
        input.setOpaque(false);
        JLabel label = new JLabel("Type or paste a transaction command.");
        label.setFont(AppFonts.button());
        AppTheme.mark(label, AppTheme.PRIMARY_TEXT_ROLE);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.getAccessibleContext().setAccessibleName(
                "Transaction voice command transcript");
        input.add(label, BorderLayout.NORTH);
        input.add(new JScrollPane(transcript), BorderLayout.CENTER);
        add(input, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        SecondaryButton listen = new SecondaryButton("Start Listening");
        listen.addActionListener(event -> listenAction.run());
        PrimaryButton parse = new PrimaryButton("Parse Command");
        parse.addActionListener(event -> parseAction.accept(transcript.getText()));
        SecondaryButton cancel = new SecondaryButton("Cancel");
        cancel.addActionListener(event -> cancelAction.run());
        actions.add(listen);
        actions.add(parse);
        actions.add(cancel);
        add(actions, BorderLayout.SOUTH);
    }

    public void setProviderStatus(String status) {
        providerStatus.setText(status == null ? " " : status);
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
