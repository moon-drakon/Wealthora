package com.spendwise.service;

public final class FinanceNotFoundException extends RuntimeException {

    public FinanceNotFoundException(String message) {
        super(message);
    }
}
