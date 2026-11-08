package com.myapp.transfer_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myapp.transfer_service.model.Status;
import com.myapp.transfer_service.model.Transfer;
import com.myapp.transfer_service.repository.TransferRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
        properties = {
            "clients.account-service.base-url=http://localhost",
            "spring.jpa.show-sql=false",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransferConcurrencyIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TransferRepository transfers;
    @Autowired EntityManagerFactory factory;

    @Test
    void staleResultCannotOverwriteCommittedTerminalStatus() {
        Transfer pending =
                transfers.saveAndFlush(
                        Transfer.builder()
                                .fromAccountId(UUID.randomUUID())
                                .toAccountId(UUID.randomUUID())
                                .status(Status.PENDING)
                                .amount(BigDecimal.TEN)
                                .build());
        try (var first = factory.createEntityManager();
                var second = factory.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Transfer completed = first.find(Transfer.class, pending.getId());
            Transfer failed = second.find(Transfer.class, pending.getId());
            completed.setStatus(Status.COMPLETED);
            failed.setStatus(Status.FAILED);
            first.getTransaction().commit();
            assertThatThrownBy(() -> second.getTransaction().commit())
                    .hasCauseInstanceOf(OptimisticLockException.class);
        }
        assertThat(transfers.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(Status.COMPLETED);
    }
}
