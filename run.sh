#!/usr/bin/env sh
# Builds and starts City Car Services on http://localhost:8080 (embedded H2 database in ./data)
set -e
mvn -q -DskipTests package
exec java -jar target/city-car-services.jar "$@"
