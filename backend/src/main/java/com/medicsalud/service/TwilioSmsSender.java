package com.medicsalud.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {
    private final String fromNumber;

    public TwilioSmsSender(
            @Value("${sms.twilio.account-sid}") String accountSid,
            @Value("${sms.twilio.auth-token}") String authToken,
            @Value("${sms.twilio.from-number}") String fromNumber) {
        Twilio.init(accountSid, authToken);
        this.fromNumber = fromNumber;
    }

    @Override
    public void send(String phoneNumber, String message) {
        Message.creator(new PhoneNumber(phoneNumber), new PhoneNumber(fromNumber), message).create();
    }
}
