# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Run
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home garage
WORKDIR /app
COPY --from=build /app/target/city-car-services.jar app.jar
RUN mkdir -p /app/data && chown -R garage:garage /app
USER garage
ENV TZ=Asia/Kolkata UPLOAD_DIR=/app/data/uploads
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
