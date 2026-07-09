
package com.myapp.transfer_service.client;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clients.account-service")
public record AccountClientProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {}