package com.spendwise.auth.registration;

import java.net.URI;

@FunctionalInterface
interface BrowserLauncher {

    void open(URI uri);
}
