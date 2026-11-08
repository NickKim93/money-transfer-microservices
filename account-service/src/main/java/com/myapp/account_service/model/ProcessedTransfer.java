package com.myapp.account_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedTransfer {

    @Id
    @Column(name = "transfer_id", nullable = false, unique = true)
    private UUID transferId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
