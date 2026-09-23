import React from 'react';
import SiteHeader from '../components/SiteHeader';

const categorias = [
  { name: 'Medicamentos', image: 'https://images.unsplash.com/photo-1585435557343-3b092031a831?auto=format&fit=crop&w=700&q=85' },
  { name: 'Cuidado personal', image: 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=700&q=85' },
  { name: 'Vitaminas', image: 'https://images.unsplash.com/photo-1607619056574-7b8d3ee536b2?auto=format&fit=crop&w=700&q=85' },
  { name: 'Bebés', image: 'https://images.unsplash.com/photo-1519689680058-324335c77eba?auto=format&fit=crop&w=700&q=85' },
];

const CategoriesPage: React.FC = () => (
  <div className="app-shell">
    <SiteHeader />
    <header className="page-header">
      <div>
        <span className="eyebrow">Catálogo</span>
        <h1>Categorías</h1>
        <p>Explora productos organizados para encontrar lo que necesitas.</p>
      </div>
    </header>

    <main className="categories page-section">
      <div className="cards-grid">
        {categorias.map((item) => (
          <article key={item.name} className="category-card">
            <img className="category-image" src={item.image} alt={item.name} />
            <h2>{item.name}</h2>
            <p>Productos de calidad para uso diario y control profesional.</p>
          </article>
        ))}
      </div>
    </main>
  </div>
);

export default CategoriesPage;
