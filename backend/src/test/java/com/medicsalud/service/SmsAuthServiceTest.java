package com.medicsalud.service;

import com.medicsalud.model.User;
import com.medicsalud.repository.UserRepository;
import com.medicsalud.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsAuthServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SmsSender smsSender;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private User user;
    private SmsAuthService service;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("admin");
        user.setRole("ROLE_ADMIN");
        user.setPhoneNumber("+15550000002");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        service = new SmsAuthService(userRepository, passwordEncoder, smsSender, jwtTokenProvider);
    }

    @Test
    void requestCodeStoresHashAndSendsSmsWithoutPersistingPlainCode() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

        service.requestCode(" admin ");

        assertEquals("hashed-code", user.getSmsCodeHash());
        assertNotNull(user.getSmsCodeExpiresAt());
        assertEquals(0, user.getSmsCodeAttempts());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(smsSender).send(eq("+15550000002"), message.capture());
        assertTrue(message.getValue().matches("Tu código de acceso MedicSalud es: \\d{6}"));
        verify(userRepository).save(user);
    }

    @Test
    void validCodeIssuesTokensAndClearsOneTimeCode() {
        user.setSmsCodeHash("hashed-code");
        service.requestCode("admin");
        user.setSmsCodeHash("hashed-code");
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken("admin", "ROLE_ADMIN", true)).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken("admin")).thenReturn("refresh");

        AuthService.AuthResponse response = service.authenticateWithSms("admin", "123456");

        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
        assertNull(user.getSmsCodeHash());
        assertNull(user.getSmsCodeExpiresAt());
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    void invalidCodeIsRejectedAndCountsAttempt() {
        user.setSmsCodeHash("hashed-code");
        user.setSmsCodeExpiresAt(java.time.Instant.now().plusSeconds(60));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                    () -> service.authenticateWithSms("admin", "123456"));

        assertEquals(1, user.getSmsCodeAttempts());
        verify(userRepository).save(user);
        verifyNoInteractions(jwtTokenProvider);
    }
}
