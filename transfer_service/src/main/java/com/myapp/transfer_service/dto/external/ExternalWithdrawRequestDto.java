package com.myapp.transfer_service.dto.external;

import java.math.BigDecimal;

public record ExternalWithdrawRequestDto (
    BigDecimal amount
){}
