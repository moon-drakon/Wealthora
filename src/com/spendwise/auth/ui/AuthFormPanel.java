package com.spendwise.auth.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.spendwise.config.AppBrand;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;

abstract class AuthFormPanel extends JPanel {

    protected final JPanel fields = new JPanel(new GridBagLayout());
    private final JLabel statusLabel = new JLabel(" ");
    private int nextRow;

    AuthFormPanel(String titleText, String descriptionText) {
        super(new GridBagLayout());
        AppTheme.mark(this, AppTheme.PAGE_ROLE);

        JPanel card = new JPanel(new BorderLayout(0, 18));
        AppTheme.mark(card, AppTheme.CARD_ROLE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.border()),
                BorderFactory.createEmptyBorder(24, 28, 24, 28)));

        JPanel heading = new JPanel(new BorderLayout(0, 14));
        heading.setOpaque(false);
        JLabel brand = new JLabel(AppBrand.APP_NAME);
        brand.setFont(AppFonts.pageTitle().deriveFont(28f));
        AppTheme.mark(brand, AppTheme.PRIMARY_TEXT_ROLE);
        JLabel tagline = new JLabel(AppBrand.TAGLINE);
        tagline.setFont(AppFonts.sectionTitle());
        tagline.setForeground(AppColors.accent());
        JLabel productDescription = new JLabel(AppBrand.DESCRIPTION);
        productDescription.setFont(AppFonts.caption());
        AppTheme.mark(productDescription, AppTheme.SECONDARY_TEXT_ROLE);
        JPanel product = new JPanel(new GridBagLayout());
        product.setOpaque(false);
        GridBagConstraints productConstraints = constraints(0);
        productConstraints.insets = new Insets(0, 0, 2, 0);
        product.add(brand, productConstraints);
        productConstraints = constraints(1);
        productConstraints.insets = new Insets(0, 0, 2, 0);
        product.add(tagline, productConstraints);
        product.add(productDescription, constraints(2));
        JLabel title = new JLabel(titleText);
        title.setFont(AppFonts.pageTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        JLabel description = new JLabel(
                "<html><body style='width:360px'>" + descriptionText
                        + "</body></html>");
        description.setFont(AppFonts.body());
        AppTheme.mark(description, AppTheme.SECONDARY_TEXT_ROLE);
        JPanel titleArea = new JPanel(new BorderLayout(0, 5));
        titleArea.setOpaque(false);
        titleArea.add(title, BorderLayout.NORTH);
        titleArea.add(description, BorderLayout.CENTER);
        heading.add(product, BorderLayout.NORTH);
        heading.add(titleArea, BorderLayout.CENTER);
        card.add(heading, BorderLayout.NORTH);

        fields.setOpaque(false);
        card.add(fields, BorderLayout.CENTER);
        statusLabel.setFont(AppFonts.caption());
        AppTheme.mark(statusLabel, AppTheme.SECONDARY_TEXT_ROLE);
        card.add(statusLabel, BorderLayout.SOUTH);

        GridBagConstraints cardConstraints = new GridBagConstraints();
        cardConstraints.insets = new Insets(24, 18, 24, 18);
        cardConstraints.fill = GridBagConstraints.HORIZONTAL;
        cardConstraints.weightx = 1;
        cardConstraints.gridx = 0;
        cardConstraints.gridy = 0;
        add(card, cardConstraints);
    }

    protected final StyledTextField textField(String accessibleName) {
        return new StyledTextField(accessibleName, 28);
    }

    protected final JPasswordField passwordField(String accessibleName) {
        JPasswordField field = new JPasswordField(28);
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; margin: 6,9,6,9; showRevealButton: true");
        field.getAccessibleContext().setAccessibleName(accessibleName);
        return field;
    }

    protected final void addField(String labelText, Component component) {
        JLabel label = new JLabel(labelText);
        label.setFont(AppFonts.button());
        label.setLabelFor(component);
        AppTheme.mark(label, AppTheme.PRIMARY_TEXT_ROLE);
        GridBagConstraints labelConstraints = constraints(nextRow++);
        labelConstraints.insets = new Insets(3, 0, 4, 0);
        fields.add(label, labelConstraints);
        GridBagConstraints fieldConstraints = constraints(nextRow++);
        fieldConstraints.insets = new Insets(0, 0, 10, 0);
        fields.add(component, fieldConstraints);
    }

    protected final void addWide(Component component) {
        GridBagConstraints constraints = constraints(nextRow++);
        constraints.insets = new Insets(4, 0, 5, 0);
        fields.add(component, constraints);
    }

    protected final JPanel buttonRow(Component... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        for (Component button : buttons) {
            row.add(button);
        }
        return row;
    }

    protected final JButton primary(String text, Runnable action) {
        PrimaryButton button = new PrimaryButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    protected final JButton secondary(String text, Runnable action) {
        SecondaryButton button = new SecondaryButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    protected final JLabel policyLabel() {
        JLabel policy = new JLabel(
                "<html>Email and password access requires an official "
                + "<b>@northsouth.edu</b> account.</html>");
        policy.setFont(AppFonts.caption());
        AppTheme.mark(policy, AppTheme.SECONDARY_TEXT_ROLE);
        policy.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return policy;
    }

    protected final JLabel helperLabel(String text) {
        JLabel helper = new JLabel("<html><body style='width:350px'>"
                + text + "</body></html>");
        helper.setFont(AppFonts.caption());
        AppTheme.mark(helper, AppTheme.SECONDARY_TEXT_ROLE);
        return helper;
    }

    protected final JPanel sectionHeading(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setOpaque(false);
        JLabel heading = new JLabel(title);
        heading.setFont(AppFonts.sectionTitle());
        AppTheme.mark(heading, AppTheme.PRIMARY_TEXT_ROLE);
        JLabel detail = new JLabel(subtitle);
        detail.setFont(AppFonts.caption());
        AppTheme.mark(detail, AppTheme.SECONDARY_TEXT_ROLE);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(detail, BorderLayout.CENTER);
        return panel;
    }

    protected final JPanel orDivider() {
        JPanel divider = new JPanel(new BorderLayout(10, 0));
        divider.setOpaque(false);
        JLabel or = new JLabel("OR");
        or.setFont(AppFonts.caption().deriveFont(java.awt.Font.BOLD));
        AppTheme.mark(or, AppTheme.SECONDARY_TEXT_ROLE);
        divider.add(new JSeparator(), BorderLayout.WEST);
        divider.add(or, BorderLayout.CENTER);
        divider.add(new JSeparator(), BorderLayout.EAST);
        divider.getComponent(0).setPreferredSize(
                new java.awt.Dimension(150, 1));
        divider.getComponent(2).setPreferredSize(
                new java.awt.Dimension(150, 1));
        return divider;
    }

    protected final void showSuccess(String message) {
        statusLabel.setForeground(AppColors.income());
        statusLabel.setText(message);
    }

    protected final void showStatus(String message) {
        statusLabel.setForeground(AppColors.secondaryText());
        statusLabel.setText(message);
    }

    protected final void showFailure(RuntimeException exception) {
        statusLabel.setForeground(AppColors.expense());
        String message = exception.getMessage();
        statusLabel.setText(message == null || message.isBlank()
                ? "Authentication could not be completed." : message);
    }

    protected static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static GridBagConstraints constraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }
}
