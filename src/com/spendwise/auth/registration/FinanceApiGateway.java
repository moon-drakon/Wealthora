package com.spendwise.auth.registration;

import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.CloudConnectionState;

public interface FinanceApiGateway {

    default String requestFinance(String method, String path, String body) {
        throw new AuthConfigurationException(
                "Cloud finance requires a configured authentication server.");
    }

    default CloudConnectionState getCloudConnectionState() {
        return CloudConnectionState.OFFLINE;
    }
}
