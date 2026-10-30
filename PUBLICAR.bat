@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === SX CAR: enviando pro GitHub (o APK e gerado la automaticamente) ===
where git >nul 2>&1 || (echo Git nao encontrado. Instale em https://git-scm.com/download/win e rode de novo. & pause & exit /b 1)
if not exist .git git init
git remote remove origin >nul 2>&1
git remote add origin https://github.com/semxorah/sx-cara.git
git config core.autocrlf false
git config user.name "semxorah"
git config user.email "chapeleirolive@gmail.com"
git checkout -B main
git add -A
git commit -m "SX CAR %date% %time%"
git push -u origin main --force
echo.
echo Se nao apareceu erro em vermelho acima, em uns 5 minutos o APK estara em:
echo https://github.com/semxorah/sx-cara/raw/apk/SX-CAR.apk
echo (acompanhe em https://github.com/semxorah/sx-cara/actions)
pause