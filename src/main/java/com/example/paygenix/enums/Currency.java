package com.example.paygenix.enums;

public enum Currency {
    AZN("₼"),
    USD("$"),
    EUR("€");

    private final String symbol;

    Currency(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
