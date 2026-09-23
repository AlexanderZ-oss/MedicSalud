package com.medicsalud.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Development fallback. Production must provide a real SMS provider bean. */
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsSender implements SmsSender {
    @Override
    public void send(String phoneNumber, String message) {
        System.out.println("[SMS DEV] Código para " + phoneNumber + ": " + message);
    }
}
