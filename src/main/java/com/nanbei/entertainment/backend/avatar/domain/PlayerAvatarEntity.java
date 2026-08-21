package com.nanbei.entertainment.backend.avatar.domain;

import com.nanbei.entertainment.backend.avatar.application.NormalizedAvatar;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "player_avatars")
public class PlayerAvatarEntity {
    @Id
    @Column(name = "avatar_key", length = 120)
    private String avatarKey;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "image_bytes", nullable = false, columnDefinition = "bytea")
    private byte[] imageBytes;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PlayerAvatarEntity() {}

    public PlayerAvatarEntity(String avatarKey, UUID userId, NormalizedAvatar avatar) {
        this.avatarKey = avatarKey;
        this.userId = userId;
        this.createdAt = Instant.now();
        update(avatar);
    }

    public void update(NormalizedAvatar avatar) {
        imageBytes = avatar.bytes();
        contentType = avatar.contentType();
        byteSize = imageBytes.length;
        width = avatar.width();
        height = avatar.height();
        sha256 = avatar.sha256();
        updatedAt = Instant.now();
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getImageBytes() {
        return imageBytes.clone();
    }

    public int getByteSize() {
        return byteSize;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
