package com.spendwise.ui.voice;

import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class VoiceListeningPanel extends JPanel {

    private final JLabel state = new JLabel("Listening…", JLabel.CENTER);
    private final JLabel detail = new JLabel(
            "Speak naturally. No transaction will be saved automatically.",
            JLabel.CENTER);

    public VoiceListeningPanel(
            Runnable stopAction,
            Runnable retryAction,
            Runnable manualAction,
            Runnable cancelAction) {
        super(new BorderLayout(0, 18));
        Objects.requireNonNull(stopAction);
        Objects.requireNonNull(retryAction);
        Objects.requireNonNull(manualAction);
        Objects.requireNonNull(cancelAction);
        setBorder(BorderFactory.createEmptyBorder(42, 30, 30, 30));
        AppTheme.mark(this, AppTheme.PAGE_ROLE);
        state.setFont(AppFonts.pageTitle().deriveFont(30f));
        AppTheme.mark(state, AppTheme.PRIMARY_TEXT_ROLE);
        detail.setFont(AppFonts.body());
        AppTheme.mark(detail, AppTheme.SECONDARY_TEXT_ROLE);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(state, BorderLayout.CENTER);
        center.add(detail, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        actions.setOpaque(false);
        PrimaryButton stop = new PrimaryButton("Stop");
        stop.addActionListener(event -> stopAction.run());
        SecondaryButton retry = new SecondaryButton("Retry");
        retry.addActionListener(event -> retryAction.run());
        SecondaryButton manual = new SecondaryButton("Manual Entry");
        manual.addActionListener(event -> manualAction.run());
        SecondaryButton cancel = new SecondaryButton("Cancel");
        cancel.addActionListener(event -> cancelAction.run());
        actions.add(stop);
        actions.add(retry);
        actions.add(manual);
        actions.add(cancel);
        add(actions, BorderLayout.SOUTH);
    }

    public void showListening() {
        state.setText("Listening…");
        detail.setText(
                "Speak naturally. No transaction will be saved automatically.");
    }

    public void showStatus(String title, String message) {
        state.setText(title);
        detail.setText(message);
    }
}
