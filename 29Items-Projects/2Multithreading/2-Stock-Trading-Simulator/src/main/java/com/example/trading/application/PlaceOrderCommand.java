package com.example.trading.application;

import com.example.trading.domain.OrderSide;
import com.example.trading.domain.DomainValidation;
import java.math.BigDecimal;
import java.util.Objects;

public record PlaceOrderCommand(
        String traderId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal limitPrice) {

    public PlaceOrderCommand {
        traderId = DomainValidation.identifier(traderId, "traderId");
        symbol = DomainValidation.symbol(symbol, "symbol");
        side = Objects.requireNonNull(side, "side");
        DomainValidation.quantity(quantity, "quantity");
        limitPrice = DomainValidation.money(limitPrice, "limitPrice", false);
    }
}
