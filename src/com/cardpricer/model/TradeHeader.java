package com.cardpricer.model;

import java.math.BigDecimal;

/** Parameter object for trade export methods, grouping all header/payment fields. */
public record TradeHeader(
        String traderName,
        String customerName,
        String driversLicense,
        String checkNumber,
        String paymentType,
        BigDecimal partialCredit,
        BigDecimal partialCheck) {}
