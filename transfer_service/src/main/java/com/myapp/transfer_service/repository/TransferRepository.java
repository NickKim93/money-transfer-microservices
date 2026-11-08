package com.myapp.transfer_service.repository;

import com.myapp.transfer_service.model.Transfer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {}
