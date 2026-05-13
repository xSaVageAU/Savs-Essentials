@echo off
echo Building NATS-Fabric and publishing to Maven Local...
cd NATS-Fabric
call gradlew.bat publishToMavenLocal
if %ERRORLEVEL% neq 0 (
    echo Failed to build NATS-Fabric!
    ping 127.0.0.1 -n 3 > nul
    exit /b %ERRORLEVEL%
)
cd ..

echo Building Savs-Essentials...
call gradlew.bat build
if %ERRORLEVEL% neq 0 (
    echo Failed to build Savs-Essentials!
    ping 127.0.0.1 -n 3 > nul
    exit /b %ERRORLEVEL%
)

echo Build successful!
ping 127.0.0.1 -n 3 > nul
