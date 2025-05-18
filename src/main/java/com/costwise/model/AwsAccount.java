package com.costwise.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "aws_accounts")
public class AwsAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String accountName;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String accessKey;

    @Column(nullable = false)
    private String secretKey;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime lastAnalysisRun;

    @Transient
    private static final String ENCRYPTION_PASSWORD = System.getenv("ENCRYPTION_PASSWORD");
    @Transient
    private static final String ENCRYPTION_SALT = System.getenv("ENCRYPTION_SALT");

    @PrePersist
    @PreUpdate
    public void encryptSensitiveData() {
        if (ENCRYPTION_PASSWORD != null && ENCRYPTION_SALT != null) {
            TextEncryptor encryptor = Encryptors.text(ENCRYPTION_PASSWORD, ENCRYPTION_SALT);
            this.accessKey = encryptor.encrypt(this.accessKey);
            this.secretKey = encryptor.encrypt(this.secretKey);
        }
    }

    @PostLoad
    public void decryptSensitiveData() {
        if (ENCRYPTION_PASSWORD != null && ENCRYPTION_SALT != null) {
            TextEncryptor encryptor = Encryptors.text(ENCRYPTION_PASSWORD, ENCRYPTION_SALT);
            this.accessKey = encryptor.decrypt(this.accessKey);
            this.secretKey = encryptor.decrypt(this.secretKey);
        }
    }
} 