package com.myapp.account_service.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myapp.account_service.controller.AccountController;
import com.myapp.account_service.service.AccountService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {
    private final AccountService service = mock(AccountService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc =
                MockMvcBuilders.standaloneSetup(new AccountController(service))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .build();
    }

    @Test
    void validationHasUsefulMessagesWithoutRejectedValues() throws Exception {
        mvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"ownerName\":\"Alice\",\"balance\":-1,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("status").value(400))
                .andExpect(jsonPath("message").value(containsString("balance")))
                .andExpect(content().string(not(containsString("rejected value"))));
    }

    @Test
    void malformedJsonUsesTheSameErrorContract() throws Exception {
        mvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("message").value("Malformed JSON or invalid field type"))
                .andExpect(jsonPath("path").value("/api/v1/accounts"));
    }

    @Test
    void invalidUuidIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/accounts/bad-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("status").value(400));
    }

    @Test
    void preservesMethodNotAllowedStatusAndHeader() throws Exception {
        mvc.perform(delete("/api/v1/accounts/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("status").value(405));
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getAccountById(id))
                .thenThrow(new IllegalStateException("password=secret SQL failure"));
        mvc.perform(get("/api/v1/accounts/" + id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("message").value("Internal server error"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void optimisticConflictDoesNotExposeSql() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getAccountById(id))
                .thenThrow(new OptimisticLockingFailureException("UPDATE secret_table"));
        mvc.perform(get("/api/v1/accounts/" + id))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("secret_table"))));
    }
}
