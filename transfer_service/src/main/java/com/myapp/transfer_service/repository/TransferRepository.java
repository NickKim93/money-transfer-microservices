package com.myapp.transfer_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.myapp.transfer_service.model.Transfer;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID>{
}
