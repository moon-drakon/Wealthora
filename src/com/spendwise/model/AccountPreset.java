package com.spendwise.model;

public enum AccountPreset {
    CASH("Cash", "Cash Wallet", AccountType.CASH, "", "cash", "#1F7E60"),
    BKASH("bKash", "bKash", AccountType.MOBILE_BANKING,
            "bKash", "mobile", "#B33A62"),
    NAGAD("Nagad", "Nagad", AccountType.MOBILE_BANKING,
            "Nagad", "mobile", "#D66A2C"),
    ROCKET("Rocket", "Rocket", AccountType.MOBILE_BANKING,
            "Rocket", "mobile", "#704C9F"),
    BANK_ACCOUNT("Bank account", "Bank Account", AccountType.BANK,
            "", "bank", "#356FA8"),
    DEBIT_CARD("Debit card", "Debit Card", AccountType.DEBIT_CARD,
            "", "card", "#356FA8"),
    CREDIT_CARD("Credit card", "Credit Card", AccountType.CREDIT_CARD,
            "", "card", "#8B5B3E");

    private final String label;
    private final String suggestedName;
    private final AccountType accountType;
    private final String institutionName;
    private final String iconName;
    private final String colorHex;

    AccountPreset(
            String label,
            String suggestedName,
            AccountType accountType,
            String institutionName,
            String iconName,
            String colorHex) {
        this.label = label;
        this.suggestedName = suggestedName;
        this.accountType = accountType;
        this.institutionName = institutionName;
        this.iconName = iconName;
        this.colorHex = colorHex;
    }

    public String getSuggestedName() {
        return suggestedName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getIconName() {
        return iconName;
    }

    public String getColorHex() {
        return colorHex;
    }

    @Override
    public String toString() {
        return label;
    }
}
