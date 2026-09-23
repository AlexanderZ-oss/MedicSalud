import React, { createContext, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import api from '../config/api';

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD FRONTEND – Contexto de Autenticación
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * OWASP Top10: A02 – Cryptographic Failures (manejo de tokens)
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Gestiona el estado de autenticación global de la SPA:
 *   • Persiste la sesión en localStorage (access token + datos de usuario)
 *   • El interceptor de axios en api.ts intenta refrescar el token
 *     automáticamente ante un 401 (ISO A.9.4.2)
 *   • El logout limpia todos los datos de sesión del almacenamiento local
 *
 * NOTA DE SEGURIDAD (OWASP A02):
 *   Los tokens JWT en localStorage son vulnerables a XSS.
 *   En producción con alto nivel de riesgo, considerar HttpOnly cookies
 *   para el access token (requiere ajuste en el backend).
 * ═══════════════════════════════════════════════════════════════════════
 */

interface UserSession {
  username: string;
  role: 'ROLE_ADMIN' | 'ROLE_PERSONAL' | 'ROLE_CLIENT';
}

interface AuthContextType {
  user: UserSession | null;
  /** Token JWT de acceso — para adjuntar en cabeceras manuales */
  token: string | null;
  /** ISO A.9.4.2 – login con credenciales + MFA opcional */
  login: (payload: { username: string; password: string; mfaCode?: string; email?: string }) => Promise<UserSession>;
  requestSmsCode: (username: string) => Promise<void>;
  loginWithSms: (payload: { username: string; code: string }) => Promise<UserSession>;
  register: (payload: { username: string; email: string; password: string; phoneNumber?: string }) => Promise<void>;
  /** ISO A.9.4.2 – cierra sesión y limpia tokens del almacenamiento */
  logout: () => void;
  isAuthenticated: boolean;
  /** true si el usuario tiene rol ADMIN o PERSONAL (acceso al panel interno) */
  isStaff: boolean;
  isAdmin: boolean;
}

export const AuthContext = createContext<AuthContextType>({
  user: null,
  token: null,
  login: async () => ({ username: '', role: 'ROLE_PERSONAL' }),
  requestSmsCode: async () => undefined,
  loginWithSms: async () => ({ username: '', role: 'ROLE_PERSONAL' }),
  register: async () => undefined,
  logout: () => undefined,
  isAuthenticated: false,
  isStaff: false,
  isAdmin: false,
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserSession | null>(null);
  const [token, setToken] = useState<string | null>(null);

  /**
   * Restaura la sesión al recargar la página desde localStorage.
   *
   * OWASP A07: Se verifica que exista tanto el token como los datos de
   * usuario antes de restaurar; si alguno falta, se hace logout implícito.
   */
  useEffect(() => {
    const savedToken = localStorage.getItem('accessToken');
    const savedUser  = localStorage.getItem('user');
    if (savedToken && savedUser) {
      try {
        const parsed = JSON.parse(savedUser) as UserSession;
        setUser(parsed);
        setToken(savedToken);
        // ISO A.9.4.2 – Adjuntar token a todas las peticiones axios
        axios.defaults.headers.common.Authorization = `Bearer ${savedToken}`;
      } catch {
        // Datos corruptos → limpiar sesión (OWASP A07)
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
      }
    }
  }, []);

  /**
   * Autentica al usuario contra el backend y persiste la sesión.
   *
   * ISO 27001 A.9.4.2: El token de acceso se adjunta al encabezado
   * Authorization de axios para todas las peticiones subsiguientes.
   *
   * OWASP A07: El rol se obtiene de la respuesta del servidor (nunca
   * hardcodeado en el cliente).
   */
  const login = async ({
    username,
    password,
    mfaCode = '',
    email,
  }: {
    username: string;
    password: string;
    mfaCode?: string;
    email?: string;
  }) => {
    const response = await api.post('/auth/login', {
      username,
      password,
      totpCode: mfaCode,
      email: email?.trim().toLowerCase() || undefined,
    });

    const accessToken  = response.data.accessToken;
    const refreshToken = response.data.refreshToken;
    // Rol real devuelto por el servidor (OWASP A07 – no hardcodeado)
    const role = response.data.role ?? 'ROLE_PERSONAL';
    const session: UserSession = { username, role };

    // ISO A.9.4.2 – Persistencia de sesión (ambos tokens)
    localStorage.setItem('accessToken',  accessToken);
    localStorage.setItem('refreshToken', refreshToken);  // ← fix: permite refresco automático
    localStorage.setItem('user', JSON.stringify(session));
    axios.defaults.headers.common.Authorization = `Bearer ${accessToken}`;
    setToken(accessToken);
    setUser(session);

    return session;
  };

  const requestSmsCode = async (username: string) => {
    await api.post('/auth/sms/request', { username: username.trim() });
  };

  const loginWithSms = async ({ username, code }: { username: string; code: string }) => {
    const response = await api.post('/auth/sms/verify', {
      username: username.trim(),
      code,
    });
    const session: UserSession = {
      username: username.trim(),
      role: response.data.role ?? 'ROLE_PERSONAL',
    };
    localStorage.setItem('accessToken', response.data.accessToken);
    localStorage.setItem('refreshToken', response.data.refreshToken);
    localStorage.setItem('user', JSON.stringify(session));
    axios.defaults.headers.common.Authorization = `Bearer ${response.data.accessToken}`;
    setToken(response.data.accessToken);
    setUser(session);
    return session;
  };

  const register = async ({ username, email, password, phoneNumber }: {
    username: string;
    email: string;
    password: string;
    phoneNumber?: string;
  }) => {
    await api.post('/auth/register', {
      username: username.trim(),
      email: email.trim().toLowerCase(),
      password,
      phoneNumber: phoneNumber?.trim() || undefined,
    });
  };

  /**
   * Cierra sesión del usuario y limpia todos los datos de sesión.
   *
   * ISO 27001 A.9.4.2: Se eliminan los tokens y datos del almacenamiento
   * local para prevenir acceso no autorizado tras el cierre de sesión.
   */
  const logout = () => {
    localStorage.removeItem('accessToken');   // ISO A.9.4.2
    localStorage.removeItem('refreshToken');  // ISO A.9.4.2
    localStorage.removeItem('user');
    delete axios.defaults.headers.common.Authorization;
    setToken(null);
    setUser(null);
  };

  const value = useMemo<AuthContextType>(
    () => ({
      user,
      token,
      login,
      requestSmsCode,
      loginWithSms,
      register,
      logout,
      isAuthenticated: !!user,
      // ISO A.9.1.2 – isStaff determina acceso al panel interno (RBAC)
      isStaff: !!user && (user.role === 'ROLE_ADMIN' || user.role === 'ROLE_PERSONAL'),
      isAdmin: !!user && user.role === 'ROLE_ADMIN',
    }),
    [user, token]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
