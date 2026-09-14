@echo off
echo ===================================================
echo [InternHub] Building all Microservices with Gradle
echo ===================================================

call gradlew.bat bootJar -x test

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Gradle build failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo [InternHub] Build successful! All JARs are ready.
echo To run all services with Docker Compose:
echo   docker compose up --build -d
echo ===================================================
