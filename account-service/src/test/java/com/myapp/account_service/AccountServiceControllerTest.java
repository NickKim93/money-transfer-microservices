package com.myapp.account_service;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;


import com.myapp.account_service.model.Account;
import com.myapp.account_service.repository.AccountRepository;
import com.myapp.account_service.dto.request.AccountCreateRequestDTO;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class AccountServiceControllerTest {
    @LocalServerPort
    private Integer port;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:16-alpine"
    );

    @Autowired
    AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost:" + port;
        accountRepository.deleteAll();
    }

    @Test
    void shouldCreateAccount() {
        given()
            .contentType(ContentType.JSON)
            .body(new AccountCreateRequestDTO("Alice", BigDecimal.ZERO, "rub"))
            .when()
            .post("api/v1/accounts")
            .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("ownerName", equalTo("Alice"))
            .body("balance", equalTo(0))
            .body("currency", equalTo("rub"));
            
    }

    @Test
    void shouldGetAccountById() {
        Account account = Account.builder().ownerName( "Alice").balance(BigDecimal.ZERO).currency("rub").build();
        accountRepository.save(account);
        given()
            .contentType(ContentType.JSON)
            .when()
            .get("api/v1/accounts/" + account.getId())
            .then()
            .statusCode(200)
            .body("id", notNullValue());
    }

    @Test
    void shouldNotFindAccountById() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get("api/v1/accounts/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            ;
    }

    @Test
    void shouldNotReturnAccount() {
        given()
            .contentType(ContentType.JSON)
            .body(new AccountCreateRequestDTO("Alice", null, null))
            .when()
            .post("api/v1/accounts")
            .then()
            .statusCode(400);
    }
}
