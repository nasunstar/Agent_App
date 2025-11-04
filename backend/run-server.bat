@echo off
echo Starting HuenDongMin Backend Server...
cd /d %~dp0
cd ..
gradlew.bat :backend:run

