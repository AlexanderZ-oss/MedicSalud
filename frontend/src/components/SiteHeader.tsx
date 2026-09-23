import React, { useContext } from 'react';
import { Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

const SiteHeader: React.FC = () => {
  const { user, logout, isAuthenticated, isStaff } = useContext(AuthContext);

  return (
    <header className="topbar">
      <Link to="/" className="brand brand-link" aria-label="Volver al inicio de MedicSalud">
        <span className="brand-mark brand-mark-3d">M</span>
        <span>
          <strong>MedicSalud</strong>
          <small>Farmacia y bienestar</small>
        </span>
      </Link>
      <nav className="nav" aria-label="Navegación principal">
        <Link className="nav-tab" to="/">Inicio</Link>
        <Link className="nav-tab" to="/categorias">Categorías</Link>
        <Link className="nav-tab" to="/servicios">Servicios</Link>
        {isStaff && <Link className="nav-tab" to="/dashboard">Panel</Link>}
      </nav>
      <div className="user-actions">
        {isAuthenticated ? (
          <>
            <span>Hola, {user?.username}</span>
            <button onClick={logout}>Cerrar sesión</button>
          </>
        ) : (
          <>
            <Link to="/registro" className="register-link">Crear cuenta</Link>
            <Link to="/login" className="btn-primary">Ingresar</Link>
          </>
        )}
      </div>
    </header>
  );
};

export default SiteHeader;
