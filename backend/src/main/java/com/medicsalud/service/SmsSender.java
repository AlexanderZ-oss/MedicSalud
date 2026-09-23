package com.medicsalud.service;

/** Sends one-time authentication codes through the configured SMS provider. */
public interface SmsSender {
    void send(String phoneNumber, String message);
}
