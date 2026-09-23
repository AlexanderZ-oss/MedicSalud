import React, { useEffect, useState } from 'react';
import api from '../config/api';
import SiteHeader from '../components/SiteHeader';

type Product = { id: number; name: string; price: number; stock: number; batchNumber?: string; supplier?: string; prescriptionRequired: boolean };
type Doctor = { id: number; fullName: string; specialty: string; email: string; available: boolean };
type Service = { id: number; name: string; description: string; price: number; durationMinutes: number; active: boolean };
type Appointment = { id: number; patientName: string; patientDni: string; service: string; doctor: string; scheduledAt: string; status: string; notificationMessage?: string };

const emptyProduct = { name: '', price: 0, stock: 0, batchNumber: '', supplier: '', prescriptionRequired: false };
const emptyDoctor = { fullName: '', specialty: '', email: '', available: true };
const emptyService = { name: '', description: '', price: 0, durationMinutes: 30, active: true };

const AdminOperationsPage: React.FC = () => {
  const [tab, setTab] = useState('inventory');
  const [staff, setStaff] = useState({ username: '', email: '', password: '', phoneNumber: '', role: 'ROLE_PERSONAL' });
  const [products, setProducts] = useState<Product[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [report, setReport] = useState<Record<string, number>>({});
  const [product, setProduct] = useState(emptyProduct);
  const [doctor, setDoctor] = useState(emptyDoctor);
  const [service, setService] = useState(emptyService);
  const [message, setMessage] = useState('');

  const load = async () => {
    const [productsResponse, doctorsResponse, servicesResponse, appointmentsResponse, reportResponse] = await Promise.all([
      api.get('/admin/operations/products'), api.get('/admin/operations/doctors'), api.get('/admin/operations/services'),
      api.get('/admin/operations/appointments'), api.get('/admin/operations/reports/summary'),
    ]);
    setProducts(productsResponse.data); setDoctors(doctorsResponse.data); setServices(servicesResponse.data);
    setAppointments(appointmentsResponse.data); setReport(reportResponse.data);
  };

  useEffect(() => { load().catch(() => setMessage('No se pudieron cargar las operaciones.')); }, []);

  const submit = async (event: React.FormEvent, path: string, body: unknown, reset: () => void) => {
    event.preventDefault(); setMessage('');
    try { await api.post(path, body); reset(); await load(); setMessage('Cambios guardados correctamente.'); }
    catch { setMessage('No se pudo guardar el registro.'); }
  };

  return (
    <div className="dashboard-page">
      <SiteHeader />
      <header className="dashboard-header">
        <div><span className="eyebrow">Centro operativo</span><h1>Administración MedicSalud</h1><p>Inventario, agenda, personal y reportes en un solo lugar.</p></div>
      </header>
      <div className="admin-tabs" role="tablist">
        {['inventory', 'staff', 'doctors', 'services', 'appointments', 'reports'].map((item) => (
          <button key={item} className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>{item === 'inventory' ? 'Inventario' : item === 'staff' ? 'Nuevo personal' : item === 'doctors' ? 'Médicos' : item === 'services' ? 'Servicios' : item === 'appointments' ? 'Citas' : 'Reportes'}</button>
        ))}
      </div>
      {message && <div className="success-message" role="status">{message}</div>}

      {tab === 'inventory' && <section className="operations-grid"><div className="panel"><h2>Nuevo lote / mercancía</h2><form className="operation-form" onSubmit={(event) => submit(event, '/admin/operations/products', product, () => setProduct(emptyProduct))}>
        <input placeholder="Medicamento o producto" required value={product.name} onChange={(e) => setProduct({ ...product, name: e.target.value })} />
        <input type="number" min="0" step="0.01" placeholder="Precio" required value={product.price} onChange={(e) => setProduct({ ...product, price: Number(e.target.value) })} />
        <input type="number" min="0" placeholder="Stock" required value={product.stock} onChange={(e) => setProduct({ ...product, stock: Number(e.target.value) })} />
        <input placeholder="Número de lote" value={product.batchNumber} onChange={(e) => setProduct({ ...product, batchNumber: e.target.value })} />
        <input placeholder="Proveedor" value={product.supplier} onChange={(e) => setProduct({ ...product, supplier: e.target.value })} />
        <label><input type="checkbox" checked={product.prescriptionRequired} onChange={(e) => setProduct({ ...product, prescriptionRequired: e.target.checked })} /> Requiere receta</label>
        <button type="submit">Agregar al inventario</button>
      </form></div><div className="panel"><h2>Stock actual</h2><div className="operation-list">{products.map((item) => <div className="operation-row" key={item.id}><strong>{item.name}</strong><span>{item.stock} uds. | S/ {item.price}</span><small>{item.batchNumber || 'Sin lote'} · {item.supplier || 'Sin proveedor'}</small></div>)}</div></div></section>}

      {tab === 'staff' && <section className="operations-grid"><div className="panel"><h2>Registrar personal</h2><form className="operation-form" onSubmit={(event) => submit(event, '/admin/users/staff', staff, () => setStaff({ username: '', email: '', password: '', phoneNumber: '', role: 'ROLE_PERSONAL' }))}><input placeholder="Usuario" required value={staff.username} onChange={(e) => setStaff({ ...staff, username: e.target.value })} /><input type="email" placeholder="Correo institucional" required value={staff.email} onChange={(e) => setStaff({ ...staff, email: e.target.value })} /><input type="password" minLength={8} placeholder="Contraseña temporal" required value={staff.password} onChange={(e) => setStaff({ ...staff, password: e.target.value })} /><input placeholder="Teléfono E.164" value={staff.phoneNumber} onChange={(e) => setStaff({ ...staff, phoneNumber: e.target.value })} /><select value={staff.role} onChange={(e) => setStaff({ ...staff, role: e.target.value })}><option value="ROLE_PERSONAL">Personal</option><option value="ROLE_ADMIN">Administrador</option></select><button type="submit">Crear acceso</button></form></div><div className="panel"><h2>Control de acceso</h2><p>Los administradores nuevos deberán usar correo autorizado y segundo factor SMS. El registro público nunca puede crear personal.</p></div></section>}

      {tab === 'doctors' && <section className="operations-grid"><div className="panel"><h2>Registrar médico</h2><form className="operation-form" onSubmit={(event) => submit(event, '/admin/operations/doctors', doctor, () => setDoctor(emptyDoctor))}><input placeholder="Nombre completo" required value={doctor.fullName} onChange={(e) => setDoctor({ ...doctor, fullName: e.target.value })} /><input placeholder="Especialidad" required value={doctor.specialty} onChange={(e) => setDoctor({ ...doctor, specialty: e.target.value })} /><input type="email" placeholder="Correo" required value={doctor.email} onChange={(e) => setDoctor({ ...doctor, email: e.target.value })} /><button type="submit">Agregar médico</button></form></div><div className="panel"><h2>Disponibilidad</h2>{doctors.map((item) => <div className="operation-row" key={item.id}><strong>{item.fullName}</strong><span>{item.specialty}</span><button onClick={async () => { await api.put(`/admin/operations/doctors/${item.id}/availability?available=${!item.available}`); await load(); }}>{item.available ? 'Disponible' : 'No disponible'}</button></div>)}</div></section>}

      {tab === 'services' && <section className="operations-grid"><div className="panel"><h2>Nuevo servicio</h2><form className="operation-form" onSubmit={(event) => submit(event, '/admin/operations/services', service, () => setService(emptyService))}><input placeholder="Nombre" required value={service.name} onChange={(e) => setService({ ...service, name: e.target.value })} /><textarea placeholder="Descripción" required value={service.description} onChange={(e) => setService({ ...service, description: e.target.value })} /><input type="number" min="0" step="0.01" placeholder="Precio" value={service.price} onChange={(e) => setService({ ...service, price: Number(e.target.value) })} /><input type="number" min="15" placeholder="Duración en minutos" value={service.durationMinutes} onChange={(e) => setService({ ...service, durationMinutes: Number(e.target.value) })} /><button type="submit">Publicar servicio</button></form></div><div className="panel"><h2>Servicios publicados</h2>{services.map((item) => <div className="operation-row" key={item.id}><strong>{item.name}</strong><span>{item.description}</span><small>S/ {item.price} · {item.durationMinutes} min</small></div>)}</div></section>}

      {tab === 'appointments' && <section className="panel"><h2>Cronograma de citas</h2>{appointments.map((item) => <div className="operation-row" key={item.id}><strong>{item.patientName} · DNI {item.patientDni}</strong><span>{item.service} con {item.doctor}</span><small>{item.scheduledAt} · {item.status}</small>{item.notificationMessage && <small className="success-message">{item.notificationMessage}</small>}<button onClick={async () => { await api.put(`/admin/operations/appointments/${item.id}/status?status=COMPLETED`); await load(); }}>Marcar atendida</button></div>)}</section>}

      {tab === 'reports' && <section className="stats-grid"><div className="stat-box"><strong>{report.products || 0}</strong><span>Productos</span></div><div className="stat-box"><strong>{report.lowStock || 0}</strong><span>Stock bajo</span></div><div className="stat-box"><strong>{report.doctors || 0}</strong><span>Médicos</span></div><div className="stat-box"><strong>{report.appointments || 0}</strong><span>Citas registradas</span></div></section>}
    </div>
  );
};

export default AdminOperationsPage;
