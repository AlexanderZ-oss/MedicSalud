# MedicSalud

Sistema web para farmacia, servicios médicos, agenda de citas e inventario.

Repositorio: https://github.com/AlexanderZ-oss/MedicSalud.git

## Estado actual

El proyecto incluye:

- Inicio de sesión de usuarios con usuario y contraseña.
- Segundo factor SMS obligatorio para administradores autorizados.
- Registro público de usuarios normales.
- Control administrativo por correo autorizado y rol `ROLE_ADMIN`.
- Inventario de medicamentos y mercancías.
- Lotes, stock, precios y proveedores.
- Médicos y disponibilidad.
- Servicios médicos con descripción, precio y duración.
- Agenda con nombre, DNI, edad y síntomas.
- Asignación automática de médico disponible.
- Historial de citas por DNI.
- Estados y notificaciones internas de citas.
- Reasignación administrativa de médico.
- Compra web y generación de factura por DNI.
- Control de medicamentos con receta y recogida presencial.
- Reporte administrativo de productos, stock bajo, médicos, servicios y citas.
- Configuración preparada para Azure SQL.
- Pruebas unitarias, pruebas HTTP y prueba de carga para 100 solicitudes concurrentes.

## Estructura

```text
medicsalud-system/
  backend/
    pom.xml
    src/main/java/com/medicsalud/
      config/          Seguridad, rate limiting y datos iniciales
      controller/      API REST
      model/           Entidades JPA
      repository/      Repositorios Spring Data
      security/        JWT, MFA, cifrado y filtros
      service/         Autenticación, SMS y operaciones
    src/main/resources/
      application.properties
      application-azure.properties
    src/test/          Pruebas unitarias
  frontend/
    package.json
    src/
      components/      Encabezado y componentes compartidos
      context/         Sesión y autenticación
      pages/           Inicio, login, registro y paneles
      config/          Cliente Axios y proxy API
  load-test.mjs
  test-api.ps1
  deploy-local.ps1
```

## Tecnologías y librerías

### Backend

- Java 21.
- Spring Boot 3.2.5.
- Spring Web para API REST.
- Spring Security para autenticación y autorización.
- Spring Data JPA/Hibernate para persistencia.
- H2 para desarrollo local.
- Microsoft SQL Server JDBC para Azure SQL.
- PostgreSQL JDBC disponible como alternativa.
- JJWT 0.12.5 para tokens JWT.
- BCrypt para contraseñas.
- TOTP `de.taimos:totp`.
- Twilio SDK 10.6.0 para SMS real.
- Lombok.
- JUnit 5, Mockito y Spring Boot Test.
- Maven.

### Frontend

- React 18.3.
- TypeScript.
- Vite 5.
- React Router 6.
- Axios.
- Cloudflare Turnstile mediante `@marsidev/react-turnstile`.

## Conexión de base de datos

La conexión se configura en:

- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-azure.properties`
- `backend/.env.azure.example`

### Desarrollo local: H2

Por defecto se usa H2 en memoria:

```properties
spring.datasource.url=jdbc:h2:mem:medicsalud;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
```

Esta base se reinicia al detener el backend. Es adecuada para pruebas, no para producción.

### Producción: Azure SQL

Se utiliza el perfil `azure` y el driver Microsoft SQL Server:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect
spring.jpa.hibernate.ddl-auto=${SPRING_JPA_DDL_AUTO:validate}
```

Variables necesarias:

```env
SPRING_PROFILES_ACTIVE=azure
SPRING_DATASOURCE_URL=jdbc:sqlserver://servidor.database.windows.net:1433;database=MedicSalud;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;
SPRING_DATASOURCE_USERNAME=usuario
SPRING_DATASOURCE_PASSWORD=clave
SPRING_JPA_DDL_AUTO=validate
```

La plantilla completa está en `backend/.env.azure.example`. No se deben guardar credenciales reales en Git.

Para arrancar con Azure SQL:

```powershell
$env:SPRING_PROFILES_ACTIVE="azure"
$env:SPRING_DATASOURCE_URL="jdbc:sqlserver://servidor.database.windows.net:1433;database=MedicSalud;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;"
$env:SPRING_DATASOURCE_USERNAME="usuario"
$env:SPRING_DATASOURCE_PASSWORD="clave"
java -jar backend/target/medicsalud-system-0.0.1-SNAPSHOT.jar
```

En Azure se recomienda usar Azure Key Vault o variables protegidas del servicio de despliegue.

## Flujo de autenticación

### Usuario normal

1. Abre `/login`.
2. Ingresa usuario y contraseña.
3. El backend valida BCrypt.
4. Se emite JWT de acceso y refresh token.
5. El usuario es redirigido a `/categorias`.
6. Puede consultar servicios, agendar citas y comprar.
7. El usuario puede consultar su agenda y facturas usando su DNI.

### Administrador

1. Selecciona `Acceso administrativo autorizado`.
2. Ingresa usuario, contraseña y correo.
3. El backend valida que el correo coincida con el usuario.
4. El correo debe estar incluido en `SECURITY_ADMIN_ALLOWED_EMAILS`.
5. Solicita el código SMS.
6. El código tiene expiración y límite de intentos.
7. Solo después del SMS válido se emite el JWT con acceso administrativo.
8. El administrador entra al dashboard y al centro operativo.

Configuración:

```env
SECURITY_ADMIN_ALLOWED_EMAILS=admin@empresa.com,otro-admin@empresa.com
SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_FROM_NUMBER=+...
```

En desarrollo `SMS_PROVIDER=console` y el código se muestra en la terminal del backend. En producción debe usarse Twilio u otro proveedor real.

## Flujo de citas

1. El usuario abre `/servicios`.
2. El frontend carga los servicios activos desde `GET /api/v1/services`.
3. El usuario completa nombre, DNI, edad, síntomas, servicio y fecha.
4. El frontend envía `POST /api/v1/appointments`.
5. El backend busca médicos disponibles.
6. Se asigna automáticamente el primer médico sin cita ese día.
7. La cita queda en estado `SCHEDULED`.
8. El usuario puede consultar el historial por DNI.
9. El administrador puede cambiar el estado o reasignar médico.
10. La reasignación queda marcada como `RESCHEDULED` con mensaje de notificación.

## Flujo de compra y factura

1. El usuario inicia sesión.
2. Envía productos, cantidades, nombre y DNI a `POST /api/v1/orders`.
3. El backend valida existencia y stock antes de modificar inventario.
4. Si un producto requiere receta, la entrega debe usar `PICKUP_PRESCRIPTION`.
5. Se descuenta el stock solo cuando la orden es válida.
6. Se genera una factura asociada al DNI.
7. La factura se consulta en `GET /api/v1/invoices?dni=...`.

## API principal

### Públicos

```text
GET  /api/v1/health
GET  /api/v1/services
GET  /api/v1/doctors/available
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/sms/request
POST /api/v1/auth/sms/verify
POST /api/v1/auth/refresh
```

### Usuario autenticado

```text
GET  /api/v1/appointments?dni=...
POST /api/v1/appointments
GET  /api/v1/invoices?dni=...
POST /api/v1/orders
POST /api/v1/checkout
GET  /api/v1/dashboard
```

### Administrador

```text
GET  /api/v1/admin/users
POST /api/v1/admin/users/staff
GET  /api/v1/admin/products
GET  /api/v1/admin/operations/products
POST /api/v1/admin/operations/products
PUT  /api/v1/admin/operations/products/{id}
GET  /api/v1/admin/operations/doctors
POST /api/v1/admin/operations/doctors
PUT  /api/v1/admin/operations/doctors/{id}/availability
GET  /api/v1/admin/operations/services
POST /api/v1/admin/operations/services
PUT  /api/v1/admin/operations/services/{id}
GET  /api/v1/admin/operations/appointments
PUT  /api/v1/admin/operations/appointments/{id}/status
PUT  /api/v1/admin/operations/appointments/{id}/doctor
GET  /api/v1/admin/operations/reports/summary
```

Todas las rutas administrativas exigen `ROLE_ADMIN`. El backend es la autoridad definitiva; las restricciones del frontend son solo una capa de experiencia.

## Seguridad

- JWT firmado con HMAC-SHA256.
- Access token y refresh token con expiración configurable.
- Contraseñas con BCrypt.
- MFA SMS para administradores.
- Códigos SMS almacenados como hash, no como texto plano.
- Expiración y límite de intentos para códigos SMS.
- Secreto TOTP cifrado con AES-GCM.
- Rate limiting para login, SMS y checkout.
- Limpieza periódica de buckets de rate limiting.
- No se confía directamente en `X-Forwarded-For`.
- CORS configurado para desarrollo local.
- DTO administrativo sin contraseña ni secretos MFA.
- Registro público no permite crear roles administrativos.
- Medicamentos con receta requieren recogida presencial.

## Capacidad y escalabilidad

Configuración inicial:

```properties
server.tomcat.threads.max=200
server.tomcat.threads.min-spare=20
server.tomcat.accept-count=100
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

Estos valores se pueden cambiar mediante variables de entorno. La prueba incluida ejecuta 100 solicitudes concurrentes:

```powershell
node load-test.mjs
```

La prueba valida `health` y `dashboard` autenticado.

Para varias instancias en producción, el rate limiting debe migrarse del mapa local a Redis u otro almacenamiento compartido.

## Ejecución local

### Backend

```powershell
cd backend
mvn clean test package
java -jar target/medicsalud-system-0.0.1-SNAPSHOT.jar
```

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

URLs:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
```

También se puede usar:

```powershell
./deploy-local.ps1
```

### Usuarios de desarrollo

```text
admin    / Admin@2024!       / admin@medicsalud.local
personal / Personal@2024     / personal@medicsalud.local
cliente  / Cliente@2024      / cliente@medicsalud.local
```

El usuario administrador requiere código SMS cuando se usa el proveedor local de consola.

## Pruebas

Pruebas unitarias:

```powershell
cd backend
mvn test
```

Pruebas de API:

```powershell
cd ..
./test-api.ps1
```

Prueba de carga:

```powershell
node load-test.mjs
```

Validaciones realizadas durante el desarrollo:

- Backend compilado y empaquetado con Maven.
- Pruebas SMS exitosas.
- Health check exitoso.
- Login de usuario exitoso.
- MFA administrativo rechazado sin SMS.
- Acceso administrativo rechazado para personal.
- Cita con médico asignado automáticamente.
- Factura generada por DNI.
- Compra con descuento de stock.
- Receta con modalidad de entrega inválida rechazada sin descontar stock.
- Frontend compilado con Vite.
- Carga concurrente de 100 solicitudes sin errores.

## Notas de producción

- Cambiar todos los secretos por valores generados aleatoriamente.
- Usar Azure Key Vault para credenciales y claves.
- Usar `SPRING_JPA_DDL_AUTO=validate` en Azure SQL.
- Aplicar migraciones versionadas con Flyway o Liquibase antes de producción.
- Configurar HTTPS en el proxy o servicio Azure.
- Reemplazar H2 por Azure SQL.
- Configurar Twilio real para SMS.
- Validar Turnstile server-side con el token secreto antes de procesar pagos reales.
- Integrar un proveedor de pagos real; el checkout actual es un flujo de demostración.
- Configurar correo o push notifications para notificaciones externas.
