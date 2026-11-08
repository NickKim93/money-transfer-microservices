package com.myapp.account_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myapp.account_service.exception.AccountNotFoundException;
import com.myapp.account_service.exception.DuplicateTransferMessageException;
import com.myapp.account_service.exception.InvalidAccountOperationException;
import com.myapp.account_service.model.Account;
import com.myapp.account_service.repository.AccountRepository;
import com.myapp.account_service.repository.ProcessedTransferRepository;
import com.myapp.account_service.service.AccountService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountTransferIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AccountService service;
    @Autowired AccountRepository accounts;
    @Autowired ProcessedTransferRepository processed;
    private Account sender;
    private Account recipient;

    @BeforeEach
    void setUp() {
        processed.deleteAll();
        accounts.deleteAll();
        sender =
                accounts.saveAndFlush(
                        Account.builder()
                                .ownerName("Sender")
                                .currency("USD")
                                .balance(new BigDecimal("1000"))
                                .build());
        recipient =
                accounts.saveAndFlush(
                        Account.builder()
                                .ownerName("Recipient")
                                .currency("USD")
                                .balance(BigDecimal.ZERO)
                                .build());
    }

    @Test
    void redeliveryMovesMoneyOnlyOnce() {
        UUID id = UUID.randomUUID();
        service.transfer(id, sender.getId(), recipient.getId(), new BigDecimal("100"));
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        id,
                                        sender.getId(),
                                        recipient.getId(),
                                        new BigDecimal("100")))
                .isInstanceOf(DuplicateTransferMessageException.class);
        assertBalances();
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test
    void rollbackRemovesIdempotencyMarker() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        id, sender.getId(), UUID.randomUUID(), BigDecimal.TEN))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(processed.existsById(id)).isFalse();
        assertThat(accounts.findById(sender.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("1000");
        service.transfer(id, sender.getId(), recipient.getId(), new BigDecimal("100"));
        assertBalances();
    }

    @Test
    void concurrentRedeliveryMovesMoneyOnlyOnce() throws Exception {
        UUID id = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt =
                    () -> {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Timed out waiting for concurrent attempts");
                        }
                        try {
                            service.transfer(
                                    id, sender.getId(), recipient.getId(), new BigDecimal("100"));
                            return true;
                        } catch (DuplicateTransferMessageException expected) {
                            return false;
                        }
                    };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS))
                    .isNotEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertBalances();
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test
    void sameAccountIsRejectedBeforePersistence() {
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        UUID.randomUUID(),
                                        sender.getId(),
                                        sender.getId(),
                                        BigDecimal.TEN))
                .isInstanceOf(InvalidAccountOperationException.class);
        assertThat(processed.count()).isZero();
    }

    private void assertBalances() {
        assertThat(accounts.findById(sender.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("900");
        assertThat(accounts.findById(recipient.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100");
    }
}
