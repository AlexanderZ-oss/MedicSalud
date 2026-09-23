import React, { useContext } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthContext, AuthProvider } from './context/AuthContext';
import LoginPage    from './pages/LoginPage';
import HomePage     from './pages/HomePage';
import DashboardPage from './pages/DashboardPage';
import CheckoutPage from './pages/CheckoutPage';
import AdminUsersPage from './pages/AdminUsersPage';
import AdminProductsPage from './pages/AdminProductsPage';
import AdminOperationsPage from './pages/AdminOperationsPage';
import CategoriesPage from './pages/CategoriesPage';
import ServicesPage from './pages/ServicesPage';
import RegisterPage from './pages/RegisterPage';

/*
 * ═══════════════════════════════════════════════════════════════════════
 * SEGURIDAD FRONTEND – Enrutamiento y Protección de Rutas
 * ───────────────────────────────────────────────────────────────────────
 * ISO 27001  : A.9.1.2  – Acceso a redes y servicios de red (RBAC)
 * ISO 27001  : A.9.4.1  – Restricción del acceso a la información
 * OWASP Top10: A01 – Broken Access Control
 *
 * El componente ProtectedRoute implementa la primera capa de control
 * de acceso en el frontend (RBAC).  La segunda capa y definitiva es
 * el backend (SecurityConfig + @PreAuthorize + MfaFilter).
 *
 * Rutas protegidas:
 *   /dashboard → requiere isAuthenticated + isStaff (ROLE_ADMIN o ROLE_PERSONAL)
 *   /checkout  → requiere isAuthenticated
 * ═══════════════════════════════════════════════════════════════════════
 */

/**
 * Componente de ruta protegida.
 *
 * <p><b>ISO 27001 A.9.1.2 / OWASP A01</b>:
 * <ul>
 *   <li>Si el usuario no está autenticado → redirige a /login.</li>
 *   <li>Si la ruta requiere staff y el usuario no lo es → redirige a /.</li>
 * </ul>
 * La protección real ocurre en el backend; esta es una capa de UX.
 */
const ProtectedRoute: React.FC<{
  children: React.ReactNode;
  staffOnly?: boolean;
  adminOnly?: boolean;
  authRequired?: boolean;
}> = ({ children, staffOnly = false, adminOnly = false, authRequired = true }) => {
  const { isAuthenticated, isStaff, isAdmin } = useContext(AuthContext);

  // ISO A.9.1.2 – Redirigir si no autenticado
  if (authRequired && !isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // ISO A.9.4.1 – Redirigir si no tiene rol de staff
  if (staffOnly && !isStaff) {
    return <Navigate to="/" replace />;
  }

  if (adminOnly && !isAdmin) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};

const AppRoutes: React.FC = () => {
  const { isAuthenticated, isStaff, isAdmin } = useContext(AuthContext);

  return (
    <Routes>
      {/* Pública */}
      <Route path="/" element={<HomePage />} />
      <Route path="/categorias" element={<CategoriesPage />} />
      <Route path="/servicios" element={<ServicesPage />} />
      <Route path="/registro" element={<RegisterPage />} />

      {/* Login — redirige si ya está autenticado */}
      <Route
        path="/login"
        element={
          isAuthenticated
            ? <Navigate to={isAdmin ? '/dashboard' : '/categorias'} replace />
            : <LoginPage />
        }
      />

      {/*
       * Dashboard — protegido: solo ADMIN y PERSONAL
       * ISO 27001 A.9.4.1 / OWASP A01
       */}
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute staffOnly>
            <DashboardPage />
          </ProtectedRoute>
        }
      />

      {/*
       * Checkout — requiere autenticación
       * ISO 27001 A.14.1.2 / OWASP A07
       */}
      <Route
        path="/checkout"
        element={
          <ProtectedRoute authRequired>
            <CheckoutPage />
          </ProtectedRoute>
        }
      />

      {/* Ruta no encontrada → inicio */}
      <Route path="*" element={<Navigate to="/" replace />} />

      {/* Admin Panel Routes */}
      <Route
        path="/admin/users"
        element={
          <ProtectedRoute adminOnly authRequired>
            <AdminUsersPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/products"
        element={
          <ProtectedRoute adminOnly authRequired>
            <AdminProductsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/operations"
        element={<ProtectedRoute adminOnly authRequired><AdminOperationsPage /></ProtectedRoute>}
      />
    </Routes>
  );
};

const App: React.FC = () => (
  <AuthProvider>
    <AppRoutes />
  </AuthProvider>
);

export default App;
