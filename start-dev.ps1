# CampusPulse — start everything for local development with one command.
#
#   .\start-dev.ps1          (or double-click start-dev.cmd)
#
# Opens three windows: AI service (:8000), backend (:8081), frontend (:5173),
# then opens the app in your browser when the backend is ready.
# Settings come from .env in this folder (copy .env.example to .env once and fill it in).
# Close a window (or press Ctrl+C in it) to stop that server.

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Write-Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Warn($text) { Write-Host "    $text" -ForegroundColor Yellow }

# ---------- 1. Settings from .env ----------
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    Write-Host "No .env file found. Create it once:" -ForegroundColor Red
    Write-Host "    Copy-Item .env.example .env   (then fill in DB_PASSWORD, GROQ_API_KEY, ...)"
    exit 1
}
Write-Step "Reading settings from .env"
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $key, $value = $line -split '=', 2
    $key = $key.Trim()
    $value = $value.Trim().Trim('"')
    if ($key) { [Environment]::SetEnvironmentVariable($key, $value, "Process") }
}

if (-not $env:DB_PASSWORD) {
    Write-Host "DB_PASSWORD is empty in .env (your PostgreSQL password)." -ForegroundColor Red
    exit 1
}
if (-not $env:JWT_SECRET -or $env:JWT_SECRET.Length -lt 32 -or $env:JWT_SECRET -like "change-me*") {
    # A temporary secret works, but everyone is signed out on every restart.
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    Write-Warn "JWT_SECRET missing or too short in .env - using a temporary one (sign-ins reset on restart)."
}
if (-not $env:GROQ_API_KEY) { Write-Warn "GROQ_API_KEY is empty - AI extraction and recommendations won't work." }
if ($env:DEMO_USERS_PASSWORD) { Write-Warn "DEMO_USERS_PASSWORD is set - demo accounts will be created (local use only)." }

# ---------- 2. First-time setup (only when missing) ----------
$aiDir = Join-Path $root "ai-service"
$python = Join-Path $aiDir "venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    Write-Step "Creating the AI service's Python environment (first run only)"
    Push-Location $aiDir
    py -3.12 -m venv venv
    & $python -m pip install --upgrade pip
    & $python -m pip install -r requirements.txt
    Pop-Location
}

$frontendDir = Join-Path $root "frontend"
if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
    Write-Step "Installing frontend packages (first run only)"
    Push-Location $frontendDir
    npm install
    Pop-Location
}

# ---------- 3. Start the three servers in their own windows ----------
# Child windows inherit the settings loaded above.
function Start-Server($title, $dir, $command) {
    $script = "`$Host.UI.RawUI.WindowTitle = 'CampusPulse - $title'; Set-Location '$dir'; $command"
    Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $script | Out-Null
}

Write-Step "Starting AI service on http://127.0.0.1:8000"
Start-Server "AI service" $aiDir ".\venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000"

Write-Step "Starting backend on http://localhost:8081 (first start downloads Maven packages)"
Start-Server "Backend" (Join-Path $root "backend") ".\mvnw.cmd spring-boot:run"

Write-Step "Starting frontend on http://localhost:5173"
Start-Server "Frontend" $frontendDir "npm run dev"

# ---------- 4. Wait for the backend, then open the app ----------
Write-Step "Waiting for the backend to be ready (up to 4 minutes)"
$deadline = (Get-Date).AddMinutes(4)
$ready = $false
while ((Get-Date) -lt $deadline) {
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8081/api/health" -TimeoutSec 3
        if ($health.status -eq "UP") { $ready = $true; break }
    } catch { Start-Sleep -Seconds 3 }
}

if ($ready) {
    Write-Host ""
    Write-Host "CampusPulse is running:  http://localhost:5173" -ForegroundColor Green
    Write-Host "Check the Backend window for 'Database schema at version N' (no red 'DATABASE NOT MIGRATED' box)."
    Start-Process "http://localhost:5173"
} else {
    Write-Host "The backend did not answer in time. Look at the 'CampusPulse - Backend' window for errors." -ForegroundColor Red
}
