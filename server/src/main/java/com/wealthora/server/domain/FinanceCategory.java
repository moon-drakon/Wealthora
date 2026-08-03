package com.wealthora.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class FinanceCategory {

    @Id private UUID id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "category_type", nullable = false, length = 40)
    private String categoryType;
    @Column(name = "built_in", nullable = false) private boolean builtIn;
    @Column(nullable = false) private boolean archived;
    @Column(name = "parent_id") private UUID parentId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FinanceCategory() {
    }

    public FinanceCategory(
            UUID id, UUID userId, String externalId, String name,
            String categoryType, boolean builtIn, boolean archived,
            UUID parentId, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.name = name;
        this.categoryType = categoryType;
        this.builtIn = builtIn;
        this.archived = archived;
        this.parentId = parentId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getCategoryType() { return categoryType; }
    public boolean isBuiltIn() { return builtIn; }
    public boolean isArchived() { return archived; }
    public UUID getParentId() { return parentId; }

    public void update(String name, boolean archived, UUID parentId, Instant now) {
        this.name = name;
        this.archived = archived;
        this.parentId = parentId;
        this.updatedAt = now;
    }
}
