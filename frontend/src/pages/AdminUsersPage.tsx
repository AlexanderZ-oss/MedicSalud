import React, { useEffect, useState, useContext } from 'react';
import { AuthContext } from '../context/AuthContext';
import api from '../config/api';
import SiteHeader from '../components/SiteHeader';

interface User {
  id: number;
  username: string;
  role: string;
  mfaRequired: boolean;
}

const AdminUsersPage: React.FC = () => {
  const { logout } = useContext(AuthContext);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        // Usa el proxy de Vite — no es necesario especificar localhost:8080
        const response = await api.get('/admin/users');
        setUsers(response.data);
      } catch (err: any) {
        if (err.response?.status === 401 || err.response?.status === 403) {
          logout();
        } else {
          setError('No se pudo cargar la lista de usuarios.');
          console.error('Error al cargar usuarios:', err);
        }
      } finally {
        setLoading(false);
      }
    };

    fetchUsers();
  }, [logout]);

  return (
    <div className="dashboard-page">
      <SiteHeader />
      <header className="dashboard-header">
        <div>
          <span className="eyebrow">Panel de Administración</span>
          <h1>Gestión de Usuarios</h1>
        </div>
      </header>
      
      <section className="panel" style={{ marginTop: '2rem' }}>
        <h3>👥 Usuarios del Sistema</h3>
        {loading ? (
          <p>Cargando usuarios...</p>
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : users.length === 0 ? (
          <p>No hay usuarios registrados.</p>
        ) : (
          <table className="inventory-list" style={{ width: '100%', textAlign: 'left', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #ddd' }}>
                <th style={{ padding: '0.5rem' }}>ID</th>
                <th style={{ padding: '0.5rem' }}>Usuario</th>
                <th style={{ padding: '0.5rem' }}>Rol</th>
                <th style={{ padding: '0.5rem' }}>MFA Requerido</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id} style={{ borderBottom: '1px solid #eee' }}>
                  <td style={{ padding: '0.5rem' }}>{user.id}</td>
                  <td style={{ padding: '0.5rem' }}><strong>{user.username}</strong></td>
                  <td style={{ padding: '0.5rem' }}>
                    <span className={`role-badge ${user.role === 'ROLE_ADMIN' ? 'admin' : ''}`}>
                      {user.role}
                    </span>
                  </td>
                  <td style={{ padding: '0.5rem' }}>{user.mfaRequired ? '✅ Sí' : '❌ No'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
};

export default AdminUsersPage;
