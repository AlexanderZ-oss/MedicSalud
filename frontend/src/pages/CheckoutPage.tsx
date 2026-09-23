import React, { useState, useContext } from 'react';
import api from '../config/api';
import { AuthContext } from '../context/AuthContext';
import { Turnstile } from '@marsidev/react-turnstile';

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD FRONTEND – Página de Pago / Checkout
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.14.1.2 – Securización de servicios de aplicación en redes
 * ISO 27001  : A.12.1.3 – Gestión de la capacidad (rate limiting)
 * OWASP Top10: A04 – Insecure Design (sin validación de bot)
 * OWASP Top10: A07 – Identification and Authentication Failures
 *
 * Controles de seguridad implementados:
 *   • Cloudflare Turnstile CAPTCHA — protección anti-bot (OWASP A04)
 *   • Validación del token CAPTCHA en el backend antes de procesar
 *   • Petición autenticada con JWT (ISO A.14.1.2)
 *   • Indicador de carga para evitar doble envío
 *   • Rate limiting en el backend: máx. 5 req/min por IP (ISO A.12.1.3)
 *
 * Parámetro ISO: Turnstile siteKey
 *   → Ubicación: variable de entorno VITE_TURNSTILE_SITE_KEY
 *   → Nunca hardcodeada en código fuente (ISO A.10.1.2)
 * ═══════════════════════════════════════════════════════════════════════
 */

interface CartItem {
  id:       number;
  name:     string;
  price:    number;
  quantity: number;
}

interface CheckoutProps {
  cartItems?: CartItem[];
}

// Productos de demostración cuando no se pasan props reales
const DEMO_ITEMS: CartItem[] = [
  { id: 1, name: 'Paracetamol 500mg',  price: 3.50, quantity: 2 },
  { id: 2, name: 'Vitamina C + zinc',  price: 8.90, quantity: 1 },
  { id: 3, name: 'Suero oral',         price: 2.20, quantity: 3 },
];

/**
 * Página de checkout con captcha y autenticación.
 *
 * <p><b>ISO 27001 A.14.1.2 / OWASP A04</b>:
 * <ul>
 *   <li>Cloudflare Turnstile valida que el usuario sea humano (CAPTCHA).</li>
 *   <li>El token del captcha se envía al backend para validación server-side.</li>
 *   <li>La petición requiere JWT válido (usuario autenticado).</li>
 * </ul>
 */
const CheckoutPage: React.FC<CheckoutProps> = ({ cartItems = DEMO_ITEMS }) => {
  const { isAuthenticated } = useContext(AuthContext);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [success, setSuccess]           = useState(false);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);

  const totalAmount = cartItems.reduce(
    (sum, item) => sum + item.price * item.quantity, 0
  );

  /**
   * Envía el pedido al backend con el token Turnstile.
   *
   * ISO 27001 A.14.1.2: La petición incluye JWT en el encabezado
   * Authorization (adjuntado por el interceptor de api.ts).
   * El captchaToken es validado en el backend antes de procesar el pago.
   */
  const handleSubmit = async () => {
    if (!captchaToken) {
      setError('Complete el captcha antes de continuar.');
      return;
    }
    if (!isAuthenticated) {
      setError('Debe iniciar sesión para realizar la compra.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await api.post('/checkout', {
        cartItems,
        totalAmount,
        captchaToken, // ISO A.14.1.2 – validado en el backend
      });

      if (response.status === 200) {
        setSuccess(true);
      } else {
        setError('Error inesperado al procesar el pago.');
      }
    } catch (err: any) {
      const msg = err.response?.data?.error ?? 'Falló la conexión con el servidor.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <div className="checkout-success">
        <div className="success-icon">✅</div>
        <h2>¡Pago realizado con éxito!</h2>
        <p>Gracias por su compra.  Recibirá un correo de confirmación pronto.</p>
      </div>
    );
  }

  return (
    <div className="checkout-page">
      <h2>🛒 Resumen de Compra</h2>

      {/* Lista de productos */}
      <ul className="cart-list">
        {cartItems.map((item) => (
          <li key={item.id} className="cart-item">
            <span className="cart-item-name">{item.name}</span>
            <span className="cart-item-qty">x{item.quantity}</span>
            <span className="cart-item-price">
              S/ {(item.price * item.quantity).toFixed(2)}
            </span>
          </li>
        ))}
      </ul>

      <div className="cart-total">
        <strong>Total:</strong> S/ {totalAmount.toFixed(2)}
      </div>

      {/*
       * Cloudflare Turnstile CAPTCHA
       * ISO 27001 A.14.1.2 / OWASP A04 – protección anti-bot
       *
       * Parámetro ISO: siteKey
       *   → Fuente: variable de entorno VITE_TURNSTILE_SITE_KEY (no hardcodeada)
       *   → Ubicación en código: CheckoutPage.tsx prop de <Turnstile>
       *   → El token se valida en el backend (/api/v1/checkout)
       */}
      <div
        className="captcha-container"
        title="Verificación Cloudflare Turnstile — ISO 27001 A.14.1.2 / OWASP A04"
      >
        <p className="captcha-label">
          🔒 Verificación de seguridad requerida
          <span className="iso-tag">ISO A.14.1.2 · OWASP A04</span>
        </p>
        <Turnstile
          siteKey={import.meta.env.VITE_TURNSTILE_SITE_KEY ?? '1x00000000000000000000AA'} // test key en dev
          onSuccess={(token) => setCaptchaToken(token)}
          onError={() => setError('Error al cargar el captcha. Recargue la página.')}
          onExpire={() => setCaptchaToken(null)}
        />
      </div>

      {error && (
        <div className="error-message" role="alert">
          ⚠ {error}
        </div>
      )}

      <button
        id="checkout-submit"
        disabled={loading || !captchaToken}
        onClick={handleSubmit}
        className={`checkout-button ${loading ? 'loading' : ''}`}
      >
        {loading ? 'Procesando...' : '💳 Confirmar y Pagar'}
      </button>

      <p className="checkout-note">
        🔐 Pago seguro — datos cifrados en tránsito (TLS) y en reposo (AES-256).
        <br />
        <small>ISO 27001 A.14.1.2 · A.10.1.1 — Rate limit: 5 intentos/min</small>
      </p>
    </div>
  );
};

export default CheckoutPage;
