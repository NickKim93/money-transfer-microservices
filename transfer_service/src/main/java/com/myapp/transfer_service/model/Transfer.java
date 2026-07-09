package com.myapp.transfer_service.model;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.CreationTimestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Transfer {

    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;

    private UUID fromAccountId;

    private UUID toAccountId;

    @Enumerated(EnumType.STRING)
    private Status status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private String failureReason;

    @Column(precision=19, scale=4)
    private BigDecimal amount;

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setFailureReason(String reason) {
        this.failureReason = reason;
    }
}
