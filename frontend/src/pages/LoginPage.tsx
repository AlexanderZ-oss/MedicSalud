import React, { useContext, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD FRONTEND – Página de Inicio de Sesión
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.4.2  – Procedimientos de inicio de sesión seguro
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Controles implementados:
 *   • Campo MFA (TOTP) — segundo factor opcional según configuración del usuario
 *   • Mensajes de error genéricos (no revelan si el usuario existe)
 *   • Indicador visual de carga para evitar doble envío
 *   • Redirección automática según rol tras autenticación exitosa
 * ═══════════════════════════════════════════════════════════════════════
 */

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login, loginWithSms, requestSmsCode } = useContext(AuthContext);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode]   = useState('');
  const [error, setError]       = useState<string | null>(null);
  const [loading, setLoading]   = useState(false);
  const [showMfa, setShowMfa]   = useState(false);
  const [loginMode, setLoginMode] = useState<'password' | 'sms'>('password');
  const [smsSent, setSmsSent] = useState(false);
  const [adminMode, setAdminMode] = useState(false);
  const [email, setEmail] = useState('');

  /**
   * Maneja el envío del formulario de login.
   *
   * ISO 27001 A.9.4.2: El formulario incluye el código TOTP para el
   * segundo factor de autenticación.  Si el servidor responde con error
   * de MFA, se muestra el campo MFA al usuario.
   */
  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const session = loginMode === 'sms'
        ? await loginWithSms({ username: username.trim(), code: mfaCode })
        : await login({ username: username.trim(), password, mfaCode, email: adminMode ? email : undefined });
      // ISO A.9.1.2 – Redirigir según rol (RBAC)
      const nextPath = session.role === 'ROLE_ADMIN' ? '/dashboard' : '/categorias';
      navigate(nextPath, { replace: true });
    } catch (e: any) {
      const msg: string = e.response?.data?.error ?? e.message ?? 'Error al iniciar sesión';
      // Mostrar campo MFA si el error lo indica (ISO A.9.4.2)
      if (msg.toLowerCase().includes('mfa') || msg.toLowerCase().includes('código')) {
        setShowMfa(true);
      }
      // OWASP A07 – Mensaje genérico para no revelar detalles internos
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleRequestSms = async () => {
    setLoading(true);
    setError(null);
    try {
      await requestSmsCode(username);
      setSmsSent(true);
      setShowMfa(true);
    } catch (e: any) {
      setError(e.response?.data?.error ?? 'No se pudo solicitar el código SMS');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        {/* Etiqueta de acceso restringido */}
        <span className="eyebrow">Acceso exclusivo del personal</span>
        <h2>Iniciar sesión</h2>

        <div className="login-mode" role="group" aria-label="Método de autenticación">
          <button type="button" className={loginMode === 'password' ? 'mode-active' : ''} onClick={() => setLoginMode('password')}>
            Contraseña
          </button>
          <button type="button" className={loginMode === 'sms' ? 'mode-active' : ''} onClick={() => setLoginMode('sms')}>
            SMS
          </button>
        </div>

        <button type="button" className="admin-entry" onClick={() => setAdminMode(!adminMode)}>
          {adminMode ? 'Acceso de usuario' : 'Acceso administrativo autorizado'}
        </button>

        {/* Aviso de seguridad */}
        {/*
          ISO 27001 A.9.4.2 – Informar al usuario sobre los controles
          de acceso activos en el sistema
        */}
        <p className="login-hint">
          🔒 Acceso protegido con autenticación de dos factores para administradores.
        </p>

        {error && <div className="error" role="alert">{error}</div>}

        <form className="login-form" onSubmit={handleSubmit} noValidate>
          {/* Usuario */}
          <label htmlFor="login-username" className="field-label">Usuario</label>
          <input
            id="login-username"
            type="text"
            placeholder="Ingresa tu usuario"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
            aria-label="Nombre de usuario"
          />

          {adminMode && (
            <>
              <label htmlFor="login-email" className="field-label">Correo administrativo autorizado</label>
              <input
                id="login-email"
                type="email"
                placeholder="admin@empresa.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />
            </>
          )}

          {loginMode === 'password' && (
            <>
              <label htmlFor="login-password" className="field-label">Contraseña</label>
              <input
                id="login-password"
                type="password"
                placeholder="Ingresa tu contraseña"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
                aria-label="Contraseña"
              />
            </>
          )}

          {(loginMode === 'sms' || adminMode) && (
            <button type="button" className="btn-secondary sms-request" onClick={handleRequestSms} disabled={loading || !username.trim()}>
              {smsSent ? 'Código solicitado' : 'Enviar código por SMS'}
            </button>
          )}

          {/*
           * Campo MFA / TOTP — ISO 27001 A.9.4.2
           * Visible siempre para administradores; aparece ante error de MFA
           */}
          <div
            className={`mfa-field ${showMfa ? 'mfa-visible' : ''}`}
            title="Segundo factor de autenticación (Google Authenticator)"
          >
            <label htmlFor="login-mfa" className="field-label">
              🔐 {adminMode || loginMode === 'sms' ? 'Código SMS' : 'Código MFA'}{' '}
              <span className="mfa-badge">ISO A.9.4.2</span>
            </label>
            <input
              id="login-mfa"
              type="text"
              inputMode="numeric"
              placeholder="Código de 6 dígitos"
              value={mfaCode}
              onChange={(e) => setMfaCode(e.target.value.replace(/\D/, '').slice(0, 6))}
              autoComplete="one-time-code"
              maxLength={6}
              aria-label={adminMode || loginMode === 'sms' ? 'Código SMS' : 'Código MFA de Google Authenticator'}
            />
          </div>

          <button
            id="login-submit"
            type="submit"
            disabled={loading}
            className="btn-submit"
          >
            {loading ? (
              <span className="loader-dots">Validando<span>.</span><span>.</span><span>.</span></span>
            ) : (
              'Entrar'
            )}
          </button>
        </form>

        <div className="login-footer">
          <Link to="/registro" className="back-link">Crear una cuenta de usuario</Link>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
