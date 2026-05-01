# Backend — Spring Boot 3.2
# Multi-stage build: compile with Maven, run with JRE

# ── Stage 1: Build ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first for layer caching
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -q

# Copy source and build (skip tests — tests run in CI, not Docker build)
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Run ───────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create storage directory
RUN mkdir -p /app/storage/recordings /app/storage/documents \
    /app/storage/audio_chunks /app/storage/minutes

# Copy built JAR
COPY --from=builder /app/target/*.jar app.jar

# Timezone
RUN apk add --no-cache tzdata
ENV TZ=Asia/Ho_Chi_Minh

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Ho_Chi_Minh", "app.jar"]
