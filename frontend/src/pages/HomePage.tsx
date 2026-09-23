import React, { useContext } from 'react';
import { Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import SiteHeader from '../components/SiteHeader';

const HomePage: React.FC = () => {
  const { isAuthenticated } = useContext(AuthContext);

  return (
    <div className="app-shell">
      <SiteHeader />

      <main className="hero" id="inicio">
        <div className="hero-copy">
          <span className="eyebrow">Tu salud, en buenas manos</span>
          <h1>Productos confiables para el cuidado diario.</h1>
          <p>
            Encontrá medicamentos, suplementos y artículos de cuidado personal con atención segura,
            rápida y centrada en la salud.
          </p>
          <div className="cta-row">
            <Link to="/categorias" className="btn-primary">Ver categorías</Link>
            {!isAuthenticated && <Link to="/login" className="btn-secondary">Acceso personal</Link>}
          </div>
        </div>

        <div className="hero-visual">
          <img src="https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=900&q=85" alt="Productos de farmacia organizados" />
          <div className="hero-visual-caption">
            <span>Selección del día</span>
            <strong>Bienestar que se siente</strong>
          </div>
        </div>
      </main>

    </div>
  );
};

export default HomePage;
