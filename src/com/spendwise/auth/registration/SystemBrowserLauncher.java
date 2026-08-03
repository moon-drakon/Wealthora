package com.spendwise.auth.registration;

import com.spendwise.auth.AuthConfigurationException;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

final class SystemBrowserLauncher implements BrowserLauncher {

    @Override
    public void open(URI uri) {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new AuthConfigurationException(
                    "The system browser is unavailable. Google Sign-In was not started.");
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (IOException | SecurityException exception) {
            throw new AuthConfigurationException(
                    "The system browser could not open Google Sign-In.",
                    exception);
        }
    }
}
