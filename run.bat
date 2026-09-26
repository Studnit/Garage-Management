@echo off
call mvn -q -DskipTests package || exit /b 1
java -jar target\city-car-services.jar %*
