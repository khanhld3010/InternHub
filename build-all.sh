#!/bin/bash
set -e

echo "==================================================="
echo "[InternHub] Building all Microservices with Gradle"
echo "==================================================="

chmod +x ./gradlew
./gradlew bootJar -x test

echo ""
echo "==================================================="
echo "[InternHub] Build successful! All JARs are ready."
echo "To run all services with Docker Compose:"
echo "  docker compose up --build -d"
echo "==================================================="
