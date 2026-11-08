package com.myapp.transfer_service.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myapp.transfer_service.client.exception.AccountClientException;
import com.myapp.transfer_service.client.exception.AccountClientNotFoundException;
import com.myapp.transfer_service.client.exception.AccountClientResourceAccessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AccountClientTest {
    private HttpServer server;
    private AccountClient client;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client =
                new AccountClient(
                        RestClient.builder(),
                        new AccountClientProperties(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + server.getAddress().getPort()
                                                + "/api/v1"),
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(1)));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void mapsNotFound() {
        respond(404);
        assertThatThrownBy(() -> client.getAccountById(id))
                .isInstanceOf(AccountClientNotFoundException.class);
    }

    @Test
    void rejectsEmptyResponse() {
        respond(200);
        assertThatThrownBy(() -> client.getAccountById(id))
                .isInstanceOf(AccountClientException.class)
                .hasMessage("Account service returned an empty response");
    }

    @Test
    void preservesConnectionFailureCause() {
        server.stop(0);
        assertThatThrownBy(() -> client.getAccountById(id))
                .isInstanceOf(AccountClientResourceAccessException.class)
                .hasCauseInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }

    private void respond(int status) {
        server.createContext(
                "/api/v1/accounts/" + id,
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, -1);
                    exchange.close();
                });
    }
}
