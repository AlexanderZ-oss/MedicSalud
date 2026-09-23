import React, { useEffect, useState, useContext } from 'react';
import { AuthContext } from '../context/AuthContext';
import api from '../config/api';
import SiteHeader from '../components/SiteHeader';

interface Product {
  nombre: string;
  unidades: number;
  estado: string;
}

const AdminProductsPage: React.FC = () => {
  const { logout } = useContext(AuthContext);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        // Usa el proxy de Vite — no es necesario especificar localhost:8080
        const response = await api.get('/admin/products');
        setProducts(response.data);
      } catch (err: any) {
        if (err.response?.status === 401 || err.response?.status === 403) {
          logout();
        } else {
          setError('No se pudo cargar el inventario.');
          console.error('Error al cargar productos:', err);
        }
      } finally {
        setLoading(false);
      }
    };

    fetchProducts();
  }, [logout]);

  return (
    <div className="dashboard-page">
      <SiteHeader />
      <header className="dashboard-header">
        <div>
          <span className="eyebrow">Panel de Administración</span>
          <h1>Control de Stock (Productos)</h1>
        </div>
      </header>
      
      <section className="panel" style={{ marginTop: '2rem' }}>
        <h3>📦 Inventario del Sistema</h3>
        {loading ? (
          <p>Cargando inventario...</p>
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : products.length === 0 ? (
          <p>No hay productos en inventario.</p>
        ) : (
          <ul className="inventory-list">
            {products.map((item) => (
              <li key={item.nombre} className={`inventory-item ${item.estado}`}>
                <span className="item-name">{item.nombre}</span>
                <span className={`item-stock ${item.estado === 'low' ? 'stock-low' : ''}`}>
                  {item.unidades} uds.
                  {item.estado === 'low' && <span className="low-badge">⚠ Stock bajo</span>}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
};

export default AdminProductsPage;
