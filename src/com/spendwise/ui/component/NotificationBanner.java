package com.spendwise.ui.component;

import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

public final class NotificationBanner extends JPanel {

    public enum Level {
        INFORMATION, SUCCESS, WARNING, ERROR
    }

    private final JLabel messageLabel = new JLabel();
    private final Timer hideTimer = new Timer(4500, event -> setVisible(false));

    public NotificationBanner() {
        super(new BorderLayout());
        AppTheme.mark(this, AppTheme.CARD_ROLE);
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        messageLabel.setFont(AppFonts.body());
        add(messageLabel, BorderLayout.CENTER);
        hideTimer.setRepeats(false);
        setVisible(false);
    }

    public void showMessage(String message, Level level) {
        messageLabel.setText(message);
        messageLabel.setForeground(switch (level) {
            case SUCCESS -> AppColors.income();
            case WARNING -> AppColors.warning();
            case ERROR -> AppColors.expense();
            case INFORMATION -> AppColors.transfer();
        });
        setVisible(true);
        hideTimer.restart();
    }
}
