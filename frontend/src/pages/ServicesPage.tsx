import React, { useEffect, useState } from 'react';
import SiteHeader from '../components/SiteHeader';
import api from '../config/api';

type Service = { id: number; name: string; description: string; price: number; durationMinutes: number };

const ServicesPage: React.FC = () => {
  const [services, setServices] = useState<Service[]>([]);
  const [form, setForm] = useState({ patientName: '', patientDni: '', patientAge: '', symptoms: '', serviceId: '', date: '' });
  const [message, setMessage] = useState('');

  useEffect(() => { api.get('/services').then((response) => setServices(response.data)).catch(() => setMessage('No se pudieron cargar los servicios.')); }, []);

  const schedule = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const response = await api.post('/appointments', { ...form, patientAge: Number(form.patientAge), serviceId: Number(form.serviceId) });
      setMessage(`Cita agendada con ${response.data.doctor} el ${response.data.scheduledAt}.`);
    } catch (error: any) { setMessage(error.response?.data?.error ?? 'No se pudo agendar la cita.'); }
  };

  return <div className="app-shell"><SiteHeader /><header className="page-header"><div><span className="eyebrow">Atención MedicSalud</span><h1>Servicios y agenda</h1><p>Elige un servicio, completa tus datos y el sistema asignará un médico disponible.</p></div></header>
    <main className="services page-section"><div className="service-catalog">{services.map((service) => <article key={service.id} className="service-item"><strong>{service.name}</strong><span>{service.description}</span><small>S/ {service.price} · {service.durationMinutes} minutos</small></article>)}</div>
      <section className="panel appointment-panel"><h2>Agendar atención</h2><form className="operation-form" onSubmit={schedule}><input placeholder="Nombres y apellidos" required value={form.patientName} onChange={(e) => setForm({ ...form, patientName: e.target.value })} /><input placeholder="DNI" required value={form.patientDni} onChange={(e) => setForm({ ...form, patientDni: e.target.value })} /><input type="number" min="0" max="120" placeholder="Edad" required value={form.patientAge} onChange={(e) => setForm({ ...form, patientAge: e.target.value })} /><textarea placeholder="Síntomas o motivo de consulta" required value={form.symptoms} onChange={(e) => setForm({ ...form, symptoms: e.target.value })} /><select required value={form.serviceId} onChange={(e) => setForm({ ...form, serviceId: e.target.value })}><option value="">Selecciona un servicio</option>{services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select><input type="date" required value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} /><button type="submit">Agendar cita</button></form>{message && <p className="success-message">{message}</p>}</section></main></div>;
};

export default ServicesPage;
