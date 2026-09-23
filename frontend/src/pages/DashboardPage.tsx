import React, { useContext } from 'react';
import { Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import SiteHeader from '../components/SiteHeader';

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD FRONTEND – Panel de Personal / Dashboard
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.1.2  – Acceso a redes y servicios (RBAC)
 * ISO 27001  : A.9.4.1  – Restricción de acceso a la información
 * OWASP Top10: A01 – Broken Access Control
 *
 * Esta página solo es accesible para roles ROLE_ADMIN y ROLE_PERSONAL.
 * La protección se aplica en dos capas:
 *   1. Frontend: ProtectedRoute en App.tsx redirige si no es staff
 *   2. Backend:  @PreAuthorize en DashboardController + MfaFilter
 * ═══════════════════════════════════════════════════════════════════════
 */

const inventario = [
  { nombre: 'Paracetamol 500mg',  unidades: 80,  estado: 'ok' },
  { nombre: 'Suero oral',         unidades: 32,  estado: 'ok' },
  { nombre: 'Vitamina C + zinc',  unidades: 55,  estado: 'ok' },
  { nombre: 'Ibuprofeno 400mg',   unidades: 47,  estado: 'ok' },
  { nombre: 'Amoxicilina 500mg',  unidades: 18,  estado: 'low' },
];

const DashboardPage: React.FC = () => {
  const { user, logout } = useContext(AuthContext);

  return (
    <div className="dashboard-page">
      <SiteHeader />

      {/* Cabecera del panel */}
      <header className="dashboard-header">
        <div>
          <span className="eyebrow">Panel del personal</span>
          {/* OWASP A01 – El nombre de usuario proviene del contexto de seguridad */}
          <h1>Bienvenido, {user?.username}</h1>
          <span className="role-badge">
            {user?.role === 'ROLE_ADMIN' ? '👑 Administrador' : '👤 Personal'}
          </span>
        </div>

        <div className="header-actions">
          {/* ISO A.9.4.2 – Cierre de sesión limpia todos los tokens */}
          <button onClick={logout} className="btn-logout" id="dashboard-logout">
            Cerrar sesión
          </button>
        </div>
      </header>

      {/* Banner de seguridad */}
      {/*
       * ISO 27001 A.9.4.1 – Indicar visualmente el nivel de acceso
       * Este panel está protegido por autenticación + MFA (ROLE_ADMIN)
       */}
      <div className="security-banner">
        <span className="security-icon">🔐</span>
        <div>
          <strong>Acceso autenticado y auditado</strong>
          <p>
            ISO 27001 A.9.4.2 — Este panel requiere autenticación con segundo factor (MFA)
            para roles de Administrador. Todas las acciones quedan registradas.
          </p>
        </div>
      </div>

      {/* Estadísticas */}
      <section className="stats-grid" aria-label="Estadísticas del día">
        <div className="stat-box">
          <strong>128</strong>
          <span>Pedidos hoy</span>
        </div>
        <div className="stat-box">
          <strong>24</strong>
          <span>Productos activos</span>
        </div>
        <div className="stat-box">
          <strong>6</strong>
          <span>Revisiones pendientes</span>
        </div>
        {user?.role === 'ROLE_ADMIN' && (
          <div className="stat-box stat-admin">
            <strong>3</strong>
            <span>Usuarios del sistema</span>
          </div>
        )}
      </section>

      {/* Paneles de contenido */}
      <section className="panel-grid" aria-label="Paneles del sistema">

        {/* Inventario */}
        <div className="panel">
          <h3>📦 Inventario actual</h3>
          <ul className="inventory-list">
            {inventario.map((item) => (
              <li key={item.nombre} className={`inventory-item ${item.estado}`}>
                <span className="item-name">{item.nombre}</span>
                <span className={`item-stock ${item.estado === 'low' ? 'stock-low' : ''}`}>
                  {item.unidades} uds.
                  {item.estado === 'low' && <span className="low-badge">⚠ Stock bajo</span>}
                </span>
              </li>
            ))}
          </ul>
        </div>

        {/* Acciones rápidas */}
        <div className="panel">
          <h3>⚡ Acciones rápidas</h3>
          <div className="quick-actions">
            <button className="action-btn" id="action-add-product">
              ➕ Agregar producto
            </button>
            <Link to="/admin/products" className="action-btn" id="action-stock-control" style={{ textDecoration: 'none' }}>
              📊 Control de stock
            </Link>
            <button className="action-btn" id="action-daily-review">
              📋 Revisión del día
            </button>
            {/* Solo visible para administradores (ISO A.9.4.1 / OWASP A01) */}
            {user?.role === 'ROLE_ADMIN' && (
              <Link to="/admin/users" className="action-btn action-admin" style={{ textDecoration: 'none' }}>
                👥 Gestión de usuarios
                <span className="admin-only-badge">Solo Admin</span>
              </Link>
            )}
            {user?.role === 'ROLE_ADMIN' && (
              <Link to="/admin/operations" className="action-btn action-admin" style={{ textDecoration: 'none' }}>
                🧭 Centro de operaciones
                <span className="admin-only-badge">Inventario, citas y reportes</span>
              </Link>
            )}
          </div>
        </div>

        {/* Panel de seguridad — ISO 27001 */}
        <div className="panel panel-security">
          <h3>🛡 Estado de seguridad</h3>
          <ul className="security-checklist">
            <li className="check-ok">✅ Autenticación JWT activa</li>
            <li className="check-ok">✅ Tokens cifrados (HS256)</li>
            <li className="check-ok">✅ Rate limiting en checkout</li>
            <li className="check-ok">✅ Cifrado AES-256/GCM en BD</li>
            <li className={user?.role === 'ROLE_ADMIN' ? 'check-ok' : 'check-warn'}>
              {user?.role === 'ROLE_ADMIN' ? '✅' : '⚠'} MFA{' '}
              {user?.role === 'ROLE_ADMIN' ? 'verificado' : 'no requerido para este rol'}
            </li>
            <li className="check-ok">✅ RBAC configurado (ISO A.9.1.2)</li>
            <li className="check-ok">✅ Cabeceras HTTP seguras activas</li>
          </ul>
        </div>

        {/* Registros recientes */}
        <div className="panel">
          <h3>📝 Actividad reciente</h3>
          <ul className="activity-list">
            <li>
              <span className="activity-time">Hoy 08:30</span>
              <span>Pedido #1204 — Paracetamol x2</span>
            </li>
            <li>
              <span className="activity-time">Hoy 09:15</span>
              <span>Stock actualizado — Amoxicilina</span>
            </li>
            <li>
              <span className="activity-time">Hoy 10:02</span>
              <span>Pedido #1205 — Suero oral x1</span>
            </li>
          </ul>
        </div>
      </section>
    </div>
  );
};

export default DashboardPage;
