package com.myapp.transfer_service.client;

import com.myapp.transfer_service.client.exception.AccountClientException;
import com.myapp.transfer_service.client.exception.AccountClientIllegalArgumentException;
import com.myapp.transfer_service.client.exception.AccountClientInsufficientFundsException;
import com.myapp.transfer_service.client.exception.AccountClientNotFoundException;
import com.myapp.transfer_service.client.exception.AccountClientResourceAccessException;
import com.myapp.transfer_service.dto.external.ExternalAccountResponse;
import com.myapp.transfer_service.dto.external.ExternalDepositRequestDto;
import com.myapp.transfer_service.dto.external.ExternalWithdrawRequestDto;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AccountClient {
    private final RestClient restClient;

    public AccountClient(RestClient.Builder builder, AccountClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient =
                builder.baseUrl(properties.baseUrl())
                        .requestFactory(factory)
                        .defaultStatusHandler(
                                HttpStatusCode::isError,
                                (request, response) -> {
                                    switch (response.getStatusCode().value()) {
                                        case 400 ->
                                                throw new AccountClientIllegalArgumentException(
                                                        "Account service rejected the request");
                                        case 404 ->
                                                throw new AccountClientNotFoundException(
                                                        "Account not found in account service");
                                        case 422 ->
                                                throw new AccountClientInsufficientFundsException(
                                                        "Insufficient funds in account service");
                                        default ->
                                                throw new AccountClientException(
                                                        "Account service returned HTTP "
                                                                + response.getStatusCode().value());
                                    }
                                })
                        .build();
    }

    public ExternalAccountResponse getAccountById(UUID accountId) {
        return execute(restClient.get().uri("/accounts/{id}", accountId));
    }

    public ExternalAccountResponse withdraw(UUID accountId, BigDecimal amount) {
        return execute(
                restClient
                        .post()
                        .uri("/accounts/{id}/withdraw", accountId)
                        .body(new ExternalWithdrawRequestDto(amount)));
    }

    public ExternalAccountResponse deposit(UUID accountId, BigDecimal amount) {
        return execute(
                restClient
                        .post()
                        .uri("/accounts/{id}/deposit", accountId)
                        .body(new ExternalDepositRequestDto(amount)));
    }

    private ExternalAccountResponse execute(RestClient.RequestHeadersSpec<?> request) {
        try {
            ExternalAccountResponse response =
                    request.retrieve().body(ExternalAccountResponse.class);
            if (response == null) {
                throw new AccountClientException("Account service returned an empty response");
            }
            return response;
        } catch (ResourceAccessException ex) {
            throw new AccountClientResourceAccessException("Account service is unavailable", ex);
        } catch (RestClientException ex) {
            throw new AccountClientException("Invalid response from account service", ex);
        }
    }
}
