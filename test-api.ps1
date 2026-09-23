# ═══════════════════════════════════════════════════════════════════════
# PRUEBAS DEL SISTEMA — MedicSalud Backend API
# ───────────────────────────────────────────────────────────────────────
# ISO 27001 : A.9.4.2 / OWASP Top10 A01, A07
#
# Ejecutar con:
#   .\test-api.ps1
#
# Prerequisito: backend corriendo en http://localhost:8080
# ═══════════════════════════════════════════════════════════════════════

$BASE = "http://localhost:8080/api/v1"
$pass = 0
$fail = 0
$results = @()

function Test-Case {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus,
        [string]$ExpectedBodyContains = $null
    )

    try {
        $params = @{
            Method  = $Method
            Uri     = $Url
            Headers = @{ "Content-Type" = "application/json" } + $Headers
        }
        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 5)
        }

        $response = Invoke-WebRequest @params -ErrorAction SilentlyContinue -SkipHttpErrorCheck
        $statusOk  = $response.StatusCode -eq $ExpectedStatus
        $bodyOk    = $true
        if ($ExpectedBodyContains) {
            $bodyOk = $response.Content.Contains($ExpectedBodyContains)
        }
        $ok = $statusOk -and $bodyOk

        if ($ok) {
            Write-Host "  [PASS] $Name" -ForegroundColor Green
            $script:pass++
        } else {
            Write-Host "  [FAIL] $Name  (esperado $ExpectedStatus, obtenido $($response.StatusCode))" -ForegroundColor Red
            if ($ExpectedBodyContains -and -not $bodyOk) {
                Write-Host "         Body esperaba contener: '$ExpectedBodyContains'" -ForegroundColor Yellow
                Write-Host "         Body recibido: $($response.Content.Substring(0, [Math]::Min(200, $response.Content.Length)))" -ForegroundColor Yellow
            }
            $script:fail++
        }
        return $response
    } catch {
        Write-Host "  [ERROR] $Name  — excepcion: $_" -ForegroundColor Red
        $script:fail++
        return $null
    }
}

Write-Host ""
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  PRUEBAS DEL SISTEMA — MedicSalud API" -ForegroundColor Cyan
Write-Host "  ISO 27001 / OWASP Top10 — Suite de verificacion" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# ── Bloque 1: Endpoints Públicos ─────────────────────────────────────
Write-Host "[ BLOQUE 1 ] Endpoints Publicos" -ForegroundColor Yellow

Test-Case -Name "TC-01: Health check publico" `
    -Method GET -Url "$BASE/health" `
    -ExpectedStatus 200 -ExpectedBodyContains "ok"

# ── Bloque 2: Autenticación ───────────────────────────────────────────
Write-Host ""
Write-Host "[ BLOQUE 2 ] Autenticacion" -ForegroundColor Yellow

# Login PERSONAL exitoso
$loginPersonal = Test-Case -Name "TC-02: Login PERSONAL exitoso" `
    -Method POST -Url "$BASE/auth/login" `
    -Body @{ username = "personal"; password = "Personal@2024"; totpCode = "" } `
    -ExpectedStatus 200 -ExpectedBodyContains "accessToken"

$tokenPersonal = $null
if ($loginPersonal -and $loginPersonal.StatusCode -eq 200) {
    $data = $loginPersonal.Content | ConvertFrom-Json
    $tokenPersonal = $data.accessToken
    Write-Host "         Token obtenido: $($tokenPersonal.Substring(0,30))..." -ForegroundColor DarkGray
}

# Login ADMIN exitoso (mfaRequired=false en pruebas)
$loginAdmin = Test-Case -Name "TC-03: Login ADMIN exitoso" `
    -Method POST -Url "$BASE/auth/login" `
    -Body @{ username = "admin"; email = "admin@medicsalud.local"; password = "Admin@2024!"; totpCode = "" } `
    -ExpectedStatus 200 -ExpectedBodyContains "accessToken"

$tokenAdmin = $null
if ($loginAdmin -and $loginAdmin.StatusCode -eq 200) {
    $data = $loginAdmin.Content | ConvertFrom-Json
    $tokenAdmin = $data.accessToken
    Write-Host "         Token obtenido: $($tokenAdmin.Substring(0,30))..." -ForegroundColor DarkGray
}

# Login credenciales incorrectas
Test-Case -Name "TC-04: Login credenciales invalidas" `
    -Method POST -Url "$BASE/auth/login" `
    -Body @{ username = "admin"; password = "clave_erronea"; totpCode = "" } `
    -ExpectedStatus 401 -ExpectedBodyContains "error"

# Login usuario inexistente
Test-Case -Name "TC-05: Login usuario inexistente (proteccion enumeracion)" `
    -Method POST -Url "$BASE/auth/login" `
    -Body @{ username = "noexisto"; password = "cualquiera"; totpCode = "" } `
    -ExpectedStatus 401 -ExpectedBodyContains "error"

# ── Bloque 3: RBAC — Dashboard ────────────────────────────────────────
Write-Host ""
Write-Host "[ BLOQUE 3 ] Control de Acceso RBAC" -ForegroundColor Yellow

Test-Case -Name "TC-06: Dashboard sin token (debe rechazar)" `
    -Method GET -Url "$BASE/dashboard" `
    -ExpectedStatus 403

if ($tokenPersonal) {
    Test-Case -Name "TC-07: Dashboard con token PERSONAL (debe permitir)" `
        -Method GET -Url "$BASE/dashboard" `
        -Headers @{ Authorization = "Bearer $tokenPersonal" } `
        -ExpectedStatus 200 -ExpectedBodyContains "ok"
} else {
    Write-Host "  [SKIP] TC-07: No se obtuvo token PERSONAL en TC-02" -ForegroundColor DarkYellow
}

Test-Case -Name "TC-08: Admin/users sin token (debe rechazar)" `
    -Method GET -Url "$BASE/admin/users" `
    -ExpectedStatus 403

if ($tokenPersonal) {
    Test-Case -Name "TC-09: Admin/users con token PERSONAL (debe rechazar — solo ADMIN)" `
        -Method GET -Url "$BASE/admin/users" `
        -Headers @{ Authorization = "Bearer $tokenPersonal" } `
        -ExpectedStatus 403
} else {
    Write-Host "  [SKIP] TC-09: No se obtuvo token PERSONAL" -ForegroundColor DarkYellow
}

if ($tokenAdmin) {
    Test-Case -Name "TC-10: Admin/users con token ADMIN (debe listar usuarios)" `
        -Method GET -Url "$BASE/admin/users" `
        -Headers @{ Authorization = "Bearer $tokenAdmin" } `
        -ExpectedStatus 200 -ExpectedBodyContains "username"
} else {
    Write-Host "  [SKIP] TC-10: No se obtuvo token ADMIN" -ForegroundColor DarkYellow
}

if ($tokenAdmin) {
    Test-Case -Name "TC-11: Admin/products con token ADMIN (inventario)" `
        -Method GET -Url "$BASE/admin/products" `
        -Headers @{ Authorization = "Bearer $tokenAdmin" } `
        -ExpectedStatus 200 -ExpectedBodyContains "Paracetamol"
} else {
    Write-Host "  [SKIP] TC-11: No se obtuvo token ADMIN" -ForegroundColor DarkYellow
}

# ── Bloque 4: Checkout ────────────────────────────────────────────────
Write-Host ""
Write-Host "[ BLOQUE 4 ] Endpoint de Checkout" -ForegroundColor Yellow

Test-Case -Name "TC-12: Checkout sin autenticacion (debe rechazar)" `
    -Method POST -Url "$BASE/checkout" `
    -Body @{ captchaToken = "tok-123" } `
    -ExpectedStatus 403

if ($tokenPersonal) {
    Test-Case -Name "TC-13: Checkout autenticado sin captchaToken (debe rechazar)" `
        -Method POST -Url "$BASE/checkout" `
        -Body @{ captchaToken = "" } `
        -Headers @{ Authorization = "Bearer $tokenPersonal" } `
        -ExpectedStatus 400 -ExpectedBodyContains "error"

    Test-Case -Name "TC-14: Checkout autenticado con captchaToken (debe aceptar)" `
        -Method POST -Url "$BASE/checkout" `
        -Body @{ captchaToken = "test-token-simulado-123" } `
        -Headers @{ Authorization = "Bearer $tokenPersonal" } `
        -ExpectedStatus 200 -ExpectedBodyContains "success"
} else {
    Write-Host "  [SKIP] TC-13/14: No se obtuvo token PERSONAL" -ForegroundColor DarkYellow
}

# ── Bloque 5: Registro de Usuarios ────────────────────────────────────
Write-Host ""
Write-Host "[ BLOQUE 5 ] Registro de Usuarios" -ForegroundColor Yellow

# Usuario nuevo
$randomUser = "test_user_$(Get-Random -Maximum 9999)"
Test-Case -Name "TC-15: Registro de usuario nuevo" `
    -Method POST -Url "$BASE/auth/register" `
    -Body @{ username = $randomUser; email = "$randomUser@medicsalud.local"; password = "Test@12345"; role = "ROLE_CLIENT" } `
    -ExpectedStatus 200 -ExpectedBodyContains "registrado"

# Registro duplicado
Test-Case -Name "TC-16: Registro usuario duplicado (debe rechazar)" `
    -Method POST -Url "$BASE/auth/register" `
    -Body @{ username = "admin"; password = "cualquier"; role = "ROLE_PERSONAL" } `
    -ExpectedStatus 400 -ExpectedBodyContains "error"

# Registro sin contraseña
Test-Case -Name "TC-17: Registro sin contrasena (debe rechazar)" `
    -Method POST -Url "$BASE/auth/register" `
    -Body @{ username = "sinpass"; password = ""; role = "ROLE_PERSONAL" } `
    -ExpectedStatus 400 -ExpectedBodyContains "error"

# ── Bloque 6: Refresco de Token ────────────────────────────────────────
Write-Host ""
Write-Host "[ BLOQUE 6 ] Refresco de Token JWT" -ForegroundColor Yellow

if ($loginPersonal -and $loginPersonal.StatusCode -eq 200) {
    $data = $loginPersonal.Content | ConvertFrom-Json
    $refreshToken = $data.refreshToken
    if ($refreshToken) {
        Test-Case -Name "TC-18: Refresco de token valido" `
            -Method POST -Url "$BASE/auth/refresh" `
            -Headers @{ Authorization = "Bearer $refreshToken" } `
            -ExpectedStatus 200 -ExpectedBodyContains "accessToken"
    } else {
        Write-Host "  [SKIP] TC-18: refreshToken no incluido en respuesta de login" -ForegroundColor DarkYellow
    }
} else {
    Write-Host "  [SKIP] TC-18: No hubo login exitoso previo" -ForegroundColor DarkYellow
}

Test-Case -Name "TC-19: Refresco con token invalido (debe rechazar)" `
    -Method POST -Url "$BASE/auth/refresh" `
    -Headers @{ Authorization = "Bearer token.falso.invalido" } `
    -ExpectedStatus 401

# ── Bloque 7: Seguridad — Datos sensibles ────────────────────────────
Write-Host ""
Write-Host "[ BLOQUE 7 ] Verificacion de Datos Sensibles" -ForegroundColor Yellow

if ($tokenAdmin) {
    $usersRes = Invoke-WebRequest -Method GET -Uri "$BASE/admin/users" `
        -Headers @{ Authorization = "Bearer $tokenAdmin"; "Content-Type" = "application/json" } `
        -SkipHttpErrorCheck -ErrorAction SilentlyContinue

    if ($usersRes -and $usersRes.StatusCode -eq 200) {
        $bodyStr = $usersRes.Content
        $noPassword  = -not $bodyStr.Contains('"password"')
        $noMfaSecret = -not $bodyStr.Contains('"mfaSecret"')

        if ($noPassword) {
            Write-Host "  [PASS] TC-20: Campo 'password' NO expuesto en /admin/users" -ForegroundColor Green
            $pass++
        } else {
            Write-Host "  [FAIL] TC-20: Campo 'password' SI aparece en respuesta (OWASP A03!)" -ForegroundColor Red
            $fail++
        }

        if ($noMfaSecret) {
            Write-Host "  [PASS] TC-21: Campo 'mfaSecret' NO expuesto en /admin/users" -ForegroundColor Green
            $pass++
        } else {
            Write-Host "  [FAIL] TC-21: Campo 'mfaSecret' SI aparece en respuesta (ISO A.10.1.1!)" -ForegroundColor Red
            $fail++
        }
    }
} else {
    Write-Host "  [SKIP] TC-20/21: No se obtuvo token ADMIN" -ForegroundColor DarkYellow
}

# ── Resumen Final ─────────────────────────────────────────────────────
Write-Host ""
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  RESUMEN DE PRUEBAS" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Cyan
$total = $pass + $fail
Write-Host "  Total de pruebas : $total"
Write-Host "  PASS             : $pass" -ForegroundColor Green
Write-Host "  FAIL             : $fail" -ForegroundColor $(if ($fail -gt 0) { "Red" } else { "Green" })
$pct = if ($total -gt 0) { [Math]::Round(($pass / $total) * 100, 1) } else { 0 }
Write-Host "  Tasa de exito    : $pct%"
Write-Host ""
if ($fail -eq 0) {
    Write-Host "  ✅ TODAS LAS PRUEBAS PASARON" -ForegroundColor Green
} else {
    Write-Host "  ⚠  HAY $fail PRUEBA(S) FALLIDA(S) — Revisar los resultados arriba" -ForegroundColor Red
}
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
