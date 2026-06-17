# Stage 1: Build
FROM maven:3.9-eclipse-temurin-23 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn -DskipTests package -q

# Stage 2: Runtime
FROM eclipse-temurin:23-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /build/target/*.jar app.jar
RUN mkdir -p /app/uploads && chown appuser:appgroup /app/uploads
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
