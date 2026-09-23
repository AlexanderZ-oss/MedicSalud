package com.medicsalud.config;

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD – Limitación de Tasa de Peticiones (Rate Limiting)
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.12.1.3 – Gestión de la capacidad
 * ISO 27001  : A.14.2.5 – Principios de ingeniería de sistemas seguros
 * OWASP Top10: A04 – Insecure Design (ausencia de límites de recursos)
 * OWASP Top10: A07 – Identification and Authentication Failures (brute force)
 *
 * Aplica un límite de 5 peticiones/minuto por IP en endpoints sensibles
 * (/api/v1/checkout, /api/v1/auth/login) usando Bucket4j (token bucket).
 *
 * Para entornos distribuidos (múltiples instancias), reemplazar el mapa
 * ConcurrentHashMap por un store compartido como Redis + Bucket4j-Redis.
 * ═══════════════════════════════════════════════════════════════════════
 */

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro de limitación de tasa basado en IP del cliente (Bucket4j).
 *
 * <p><b>Endpoints protegidos</b>:
 * <ul>
 *   <li>{@code POST /api/v1/checkout} — máx. 5 req/min por IP</li>
 *   <li>{@code POST /api/v1/auth/login} — máx. 10 req/min por IP
 *       (protección anti-fuerza-bruta, OWASP A07)</li>
 * </ul>
 *
 * <p><b>ISO 27001 A.12.1.3 / OWASP A04</b>: Evita agotamiento de recursos
 * (DoS) y ataques de fuerza bruta contra el endpoint de login.
 *
 * <p><b>Nota de producción</b>: Para despliegues con balanceador de carga
 * usar {@code X-Forwarded-For} o un store distribuido (Redis) en lugar del
 * mapa en memoria.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    // ── Endpoints y límites ───────────────────────────────────────────
    private static final String CHECKOUT_PATH = "/api/v1/checkout";
    private static final String LOGIN_PATH    = "/api/v1/auth/login";
    private static final String SMS_REQUEST_PATH = "/api/v1/auth/sms/request";
    private static final String SMS_VERIFY_PATH = "/api/v1/auth/sms/verify";

    // ISO 27001 A.12.1.3 – límites de capacidad configurados explícitamente
    private static final int      CHECKOUT_REQ_PER_MIN = 5;
    private static final int      LOGIN_REQ_PER_MIN    = 10;
    private static final Duration REFILL_PERIOD        = Duration.ofMinutes(1);

    // Paths que activan el filtro
        private static final List<String> RATE_LIMITED_PATHS = List.of(
            CHECKOUT_PATH, LOGIN_PATH, SMS_REQUEST_PATH, SMS_VERIFY_PATH);

    // Mapa IP → historial de peticiones en memoria (en producción usar Redis)
    private final Map<String, Deque<Long>> checkoutBuckets = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> loginBuckets    = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return RATE_LIMITED_PATHS.stream().noneMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain) throws ServletException, IOException {

        // ISO 27001 A.12.1.3 – identificar cliente por IP
        // Nota: en producción, usar X-Forwarded-For si hay proxy inverso
        String clientIp = getClientIp(request);
        String uri      = request.getRequestURI();

        boolean authenticationRequest = isAuthenticationRequest(uri);
        Deque<Long> requestHistory = authenticationRequest
                ? loginBuckets.computeIfAbsent(clientIp, k -> new ArrayDeque<>())
                : checkoutBuckets.computeIfAbsent(clientIp, k -> new ArrayDeque<>());

        if (allowRequest(requestHistory, authenticationRequest ? LOGIN_REQ_PER_MIN : CHECKOUT_REQ_PER_MIN)) {
            filterChain.doFilter(request, response);
        } else {
            // HTTP 429 Too Many Requests (OWASP A07 – anti-brute-force)
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"Demasiadas peticiones. Intente nuevamente en 1 minuto.\"}");
        }
    }

    // ── Métodos auxiliares ────────────────────────────────────────────

    private boolean allowRequest(Deque<Long> requestHistory, int requestsPerMinute) {
        long now = System.currentTimeMillis();
        long windowMs = REFILL_PERIOD.toMillis();

        synchronized (requestHistory) {
            while (!requestHistory.isEmpty() && now - requestHistory.peekFirst() > windowMs) {
                requestHistory.pollFirst();
            }

            if (requestHistory.size() >= requestsPerMinute) {
                return false;
            }

            requestHistory.addLast(now);
            return true;
        }
    }

    private boolean isAuthenticationRequest(String uri) {
        return uri.startsWith(LOGIN_PATH)
                || uri.startsWith(SMS_REQUEST_PATH)
                || uri.startsWith(SMS_VERIFY_PATH);
    }

    @Scheduled(fixedDelayString = "${security.rate-limit.cleanup-ms:60000}")
    void cleanupBuckets() {
        long cutoff = System.currentTimeMillis() - REFILL_PERIOD.toMillis();
        removeExpiredBuckets(loginBuckets, cutoff);
        removeExpiredBuckets(checkoutBuckets, cutoff);
    }

    private void removeExpiredBuckets(Map<String, Deque<Long>> buckets, long cutoff) {
        buckets.entrySet().removeIf(entry -> {
            Deque<Long> history = entry.getValue();
            synchronized (history) {
                while (!history.isEmpty() && history.peekFirst() < cutoff) {
                    history.pollFirst();
                }
                return history.isEmpty();
            }
        });
    }

    /**
     * Extrae la IP real del cliente considerando proxies de confianza.
     *
     * <p><b>ISO 27001 A.12.1.3</b>: En producción con proxy inverso,
     * usar {@code X-Forwarded-For} (validado) para obtener la IP real.
     */
    private String getClientIp(HttpServletRequest request) {
        // No confiar en X-Forwarded-For directamente: el cliente puede falsificarlo.
        // En producción, resolverlo solo en el proxy confiable antes de llegar aquí.
        return request.getRemoteAddr();
    }
}
