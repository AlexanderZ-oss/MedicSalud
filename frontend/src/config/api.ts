import axios from 'axios';

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD FRONTEND – Cliente HTTP Axios centralizado
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Esta instancia de axios incluye:
 *   • baseURL centralizada (evita dispersión de URLs en el código)
 *   • withCredentials: true (envía cookies HttpOnly si el backend las usa)
 *   • Interceptor de respuesta: intenta refrescar el access token ante 401
 *     sin interrumpir la experiencia del usuario (ISO A.9.4.2)
 *
 * FLUJO DE REFRESCO DE TOKEN (ISO A.9.4.2):
 *   1. Petición → 401 Unauthorized
 *   2. Interceptor detecta 401 y no es un retry
 *   3. POST /auth/refresh con el refresh token
 *   4. Backend valida refresh token y emite nuevo access token
 *   5. Se reintenta la petición original con el nuevo token
 *   6. Si el refresco falla → propagar error (logout en AuthContext)
 * ═══════════════════════════════════════════════════════════════════════
 */

/**
 * Instancia axios configurada para todas las peticiones al backend.
 *
 * Parámetros de configuración:
 *   • baseURL: '/api/v1' — proxy de Vite redirige al backend (vite.config.ts)
 *   • withCredentials: true — necesario para cookies HttpOnly (ISO A.9.4.2)
 */
const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true, // ISO A.9.4.2 – necesario para refresh token en cookie
});

/**
 * Interceptor de respuesta para refresco transparente de tokens JWT.
 *
 * ISO 27001 A.9.4.2: El refresco automático evita que el usuario tenga
 * que iniciar sesión de nuevo cuando el access token expira durante su
 * sesión activa.
 */
api.interceptors.response.use(
  // Respuestas exitosas pasan sin modificación
  (response) => response,

  async (error) => {
    const originalRequest = error.config;

    // Detectar 401 en petición no retried (evitar bucle infinito)
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem('refreshToken');
        if (!refreshToken) {
          return Promise.reject(error); // Sin refresh token → logout
        }

        // ISO A.9.4.2 – Solicitar nuevo access token con el refresh token
        const refreshRes = await axios.post(
          '/api/v1/auth/refresh',
          {},
          {
            withCredentials: true,
            headers: { Authorization: `Bearer ${refreshToken}` },
          }
        );

        const newToken = refreshRes.data.accessToken;
        if (newToken) {
          // Actualizar token en localStorage y en las cabeceras de axios
          localStorage.setItem('accessToken', newToken);
          axios.defaults.headers.common.Authorization = `Bearer ${newToken}`;
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
        }

        // Reintentar la petición original con el nuevo token
        return api(originalRequest);
      } catch (refreshError) {
        // Refresh fallido → el usuario debe volver a iniciar sesión
        // OWASP A07: limpiar tokens inválidos del almacenamiento
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        delete axios.defaults.headers.common.Authorization;
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
