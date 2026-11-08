package com.myapp.transfer_service.dto.external;

import java.math.BigDecimal;
import java.util.UUID;

public record ExternalAccountResponse(
        UUID id, String ownerName, String currency, BigDecimal balance) {}
