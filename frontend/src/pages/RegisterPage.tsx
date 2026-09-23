import React, { useContext, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const { register } = useContext(AuthContext);
  const [form, setForm] = useState({ username: '', email: '', password: '', phoneNumber: '' });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const update = (field: keyof typeof form) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await register(form);
      navigate('/login', { replace: true, state: { registered: true } });
    } catch (requestError: any) {
      setError(requestError.response?.data?.error ?? 'No se pudo crear la cuenta');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page register-page">
      <div className="login-card">
        <Link to="/" className="brand brand-link">
          <span className="brand-mark brand-mark-3d">M</span>
          <strong>MedicSalud</strong>
        </Link>
        <span className="eyebrow">Cuenta personal</span>
        <h2>Crear cuenta</h2>
        <p className="login-hint">Regístrate para comprar y consultar tus pedidos.</p>
        {error && <div className="error" role="alert">{error}</div>}
        <form className="login-form" onSubmit={submit}>
          <label htmlFor="register-username" className="field-label">Usuario</label>
          <input id="register-username" value={form.username} onChange={update('username')} required />
          <label htmlFor="register-email" className="field-label">Correo electrónico</label>
          <input id="register-email" type="email" value={form.email} onChange={update('email')} required />
          <label htmlFor="register-password" className="field-label">Contraseña</label>
          <input id="register-password" type="password" value={form.password} onChange={update('password')} minLength={8} required />
          <label htmlFor="register-phone" className="field-label">Teléfono SMS (opcional)</label>
          <input id="register-phone" type="tel" placeholder="+51999999999" value={form.phoneNumber} onChange={update('phoneNumber')} />
          <button type="submit" disabled={loading}>{loading ? 'Creando cuenta...' : 'Registrarme'}</button>
        </form>
        <div className="login-footer"><Link to="/login" className="back-link">Ya tengo una cuenta</Link></div>
      </div>
    </div>
  );
};

export default RegisterPage;