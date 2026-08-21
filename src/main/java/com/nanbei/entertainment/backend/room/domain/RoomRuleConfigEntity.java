package com.nanbei.entertainment.backend.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "room_rule_configs")
public class RoomRuleConfigEntity {
    @EmbeddedId private RoomGameId id;

    @Column(name = "config_version", nullable = false)
    private int configVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String config;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomRuleConfigEntity() {}

    public RoomGameId getId() {
        return id;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public String getConfig() {
        return config;
    }
}
