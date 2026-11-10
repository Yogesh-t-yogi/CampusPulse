@echo off
rem CampusPulse - start AI service, backend and frontend (see start-dev.ps1).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1"
pause
