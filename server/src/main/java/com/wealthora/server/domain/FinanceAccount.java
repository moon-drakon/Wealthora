package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class FinanceAccount {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "account_type", nullable = false, length = 40)
    private String accountType;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;
    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance;
    @Column(name = "icon_name", nullable = false, length = 30)
    private String iconName;
    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;
    @Column(name = "institution_name", nullable = false, length = 160)
    private String institutionName;
    @Column(name = "opened_on") private LocalDate openedOn;
    @Column(nullable = false) private boolean archived;
    @Column(name = "default_account", nullable = false)
    private boolean defaultAccount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FinanceAccount() {
    }

    public FinanceAccount(
            UUID id, UUID userId, String externalId, String name,
            String accountType, String currencyCode, BigDecimal openingBalance,
            String iconName, String colorHex, String institutionName,
            LocalDate openedOn, boolean archived, boolean defaultAccount,
            Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.name = name;
        this.accountType = accountType;
        this.currencyCode = currencyCode;
        this.openingBalance = openingBalance;
        this.currentBalance = openingBalance;
        this.iconName = iconName;
        this.colorHex = colorHex;
        this.institutionName = institutionName;
        this.openedOn = openedOn;
        this.archived = archived;
        this.defaultAccount = defaultAccount;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getAccountType() { return accountType; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public String getIconName() { return iconName; }
    public String getColorHex() { return colorHex; }
    public String getInstitutionName() { return institutionName; }
    public LocalDate getOpenedOn() { return openedOn; }
    public boolean isArchived() { return archived; }
    public boolean isDefaultAccount() { return defaultAccount; }

    public void update(
            String name, String accountType, String currencyCode,
            BigDecimal newOpeningBalance, String iconName, String colorHex,
            String institutionName, LocalDate openedOn, boolean archived,
            Instant now) {
        currentBalance = currentBalance.add(
                newOpeningBalance.subtract(openingBalance));
        openingBalance = newOpeningBalance;
        this.name = name;
        this.accountType = accountType;
        this.currencyCode = currencyCode;
        this.iconName = iconName;
        this.colorHex = colorHex;
        this.institutionName = institutionName;
        this.openedOn = openedOn;
        this.archived = archived;
        this.updatedAt = now;
    }

    public void changeBalance(BigDecimal delta, Instant now) {
        currentBalance = currentBalance.add(delta);
        updatedAt = now;
    }
}
