package com.myapp.transfer_service.client;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.myapp.transfer_service.client.exception.AccountClientException;
import com.myapp.transfer_service.client.exception.AccountClientIllegalArgumentException;
import com.myapp.transfer_service.client.exception.AccountClientInsufficientFundsException;
import com.myapp.transfer_service.client.exception.AccountClientNotFoundException;
import com.myapp.transfer_service.client.exception.AccountClientResourceAccessException;
import com.myapp.transfer_service.dto.external.ExternalAccountResponse;
import com.myapp.transfer_service.dto.external.ExternalDepositRequestDto;
import com.myapp.transfer_service.dto.external.ExternalWithdrawRequestDto;


@Component
public class AccountClient {

    private final RestClient restClient;


    public AccountClient(RestClient.Builder builder, AccountClientProperties accountClientProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(accountClientProperties.connectTimeout());
        requestFactory.setReadTimeout(accountClientProperties.readTimeout());
        this.restClient = builder
                .baseUrl(accountClientProperties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public ExternalAccountResponse getAccountById(UUID accountId) {
        try {
            return restClient.get()
            .uri("/accounts/{id}", accountId)
            .retrieve()
            .onStatus(
                status -> status.value() == 404,
                (request, response) -> {
                    throw new AccountClientNotFoundException(
                        "Account not found in account-service: " + accountId
                    );
                }
            )
            .onStatus(
                status -> status.value() == 422,
                (request, response) -> {
                    throw new AccountClientInsufficientFundsException(
                        "Insufficient funds in account-service"
                    );
                }
            )
            .onStatus(
                status -> status.is5xxServerError(),
                (request, response) -> {
                    throw new AccountClientException(
                        "Account-service returned server error: "
                            + response.getStatusCode()
                    );
                }
            )
            .body(ExternalAccountResponse.class);
        }  catch (ResourceAccessException ex) {
            throw new AccountClientResourceAccessException(
                "Account-service is unavailable"
            );
        }
    }

    public ExternalAccountResponse withdraw(UUID accountId, BigDecimal amount) {
        try {
            return restClient.post()
                .uri("/accounts/{id}/withdraw", accountId)
                .body(new ExternalWithdrawRequestDto(amount))
                .retrieve()
                .onStatus(
                    status -> status.value() == 400,
                    (request, response) -> {
                        throw new AccountClientIllegalArgumentException(
                            "Account-service illegal argument exception"
                        );
                    }
                )
                .onStatus(
                    status -> status.value() == 422,
                    (request, response) -> {
                        throw new AccountClientInsufficientFundsException(
                            "Account-service: Not enough funds on the account"
                        );
                    }
                )
                .onStatus(
                    status -> status.is5xxServerError(),
                    (request, response) -> {
                        throw new AccountClientException(
                            "Account-service returned server error: "
                                + response.getStatusCode()
                        );
                    }
                )
                .body(ExternalAccountResponse.class);
        } catch (ResourceAccessException ex) {
            throw new AccountClientResourceAccessException(
                "Account-service is unavailable"
            );
        }
    }

        public ExternalAccountResponse deposit(
            UUID accountId,
            BigDecimal amount) {
        try {
            return restClient.post()
                .uri("/accounts/{id}/deposit", accountId)
                .body(new ExternalDepositRequestDto(amount))
                .retrieve()
                .onStatus(
                    status -> status.value() == 400,
                    (request, response) -> {
                        throw new AccountClientIllegalArgumentException(
                            "Account-service illegal argument exception"
                        );
                    }
                )
                .onStatus(
                    status -> status.value() == 422,
                    (request, response) -> {
                        throw new AccountClientInsufficientFundsException(
                            "Account-service: Not enough funds on the account"
                        );
                    }
                )
                .onStatus(
                    status -> status.is5xxServerError(),
                    (request, response) -> {
                        throw new AccountClientException(
                            "Account-service returned server error: "
                                + response.getStatusCode()
                        );
                    }
                )
                .body(ExternalAccountResponse.class);
        } catch (ResourceAccessException ex) {
            throw new AccountClientResourceAccessException(
                "Account-service is unavailable"
            );
        }
    }
   
}