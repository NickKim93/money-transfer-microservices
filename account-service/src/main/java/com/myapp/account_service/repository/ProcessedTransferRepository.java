package com.myapp.account_service.repository;

import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import com.myapp.account_service.model.ProcessedTransfer;

@Repository
public interface ProcessedTransferRepository extends JpaRepository<ProcessedTransfer, UUID> {
    
}
