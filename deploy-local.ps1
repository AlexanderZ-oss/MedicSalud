# deploy-local.ps1
# Script para iniciar backend y frontend simultáneamente en local

Write-Host "Iniciando despliegue local de MedicSalud System..." -ForegroundColor Green

# 1. Iniciar el Backend (Spring Boot) en segundo plano
Write-Host "Levantando el Backend (Spring Boot)..." -ForegroundColor Cyan
$backendPath = Join-Path $PSScriptRoot "backend"
if (Test-Path (Join-Path $backendPath "mvnw.cmd")) {
	Start-Process -NoNewWindow -FilePath "cmd.exe" -ArgumentList "/c cd backend && mvnw.cmd spring-boot:run"
} elseif (Test-Path (Join-Path $backendPath "target\medicsalud-system-0.0.1-SNAPSHOT.jar")) {
	Write-Host "Maven Wrapper no encontrado; usando el JAR generado." -ForegroundColor Yellow
	Start-Process -NoNewWindow -FilePath "java.exe" -WorkingDirectory $backendPath -ArgumentList "-jar", "target\medicsalud-system-0.0.1-SNAPSHOT.jar"
} else {
	throw "No se encontró mvnw.cmd ni el JAR del backend en $backendPath"
}

# 2. Iniciar el Frontend (Vite)
Write-Host "Levantando el Frontend (Vite)..." -ForegroundColor Cyan
Start-Process -NoNewWindow -FilePath "cmd.exe" -ArgumentList "/c cd frontend && npm run dev"

Write-Host "Despliegue iniciado." -ForegroundColor Green
Write-Host "El Backend estará disponible en http://localhost:8080"
Write-Host "El Frontend estará disponible en http://localhost:5173"
Write-Host "Usa 'Ctrl+C' en las ventanas respectivas o mata los procesos si deseas detenerlos."
