package com.myapp.transfer_service.client;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clients.account-service")
public record AccountClientProperties(
        @NotNull URI baseUrl, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {}
