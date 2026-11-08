package com.myapp.account_service.repository;

import com.myapp.account_service.model.ProcessedTransfer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedTransferRepository extends JpaRepository<ProcessedTransfer, UUID> {
    @Modifying
    @Query(
            value =
                    "INSERT INTO processed_transfer (transfer_id, processed_at) VALUES (:transferId, :processedAt) ON CONFLICT (transfer_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(
            @Param("transferId") UUID transferId, @Param("processedAt") Instant processedAt);
}
