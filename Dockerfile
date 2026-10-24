# syntax=docker/dockerfile:1

# ==============================================================================
# Stage 1: Build JAR from source
# ==============================================================================
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and configuration
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN ./mvnw -B dependency:go-offline || true

# Copy source code and build production executable JAR
COPY src src
RUN ./mvnw -B package -DskipTests

# ==============================================================================
# Stage 2: Spring Boot Layer Extraction
# ==============================================================================
FROM eclipse-temurin:25-jre-alpine AS extractor
WORKDIR /extracted

COPY --from=builder /workspace/target/*.jar /tmp/app.jar

# Extract Spring Boot layers (dependencies, loader, snapshot-dependencies, application)
RUN java -Djarmode=tools -jar /tmp/app.jar extract --layers --launcher --destination /extracted

# ==============================================================================
# Stage 3: Production Runtime
# ==============================================================================
FROM eclipse-temurin:25-jre-alpine AS runner

# Security: Create non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy Spring Boot layers in order of change frequency (best Docker layer caching)
COPY --from=extractor --chown=appuser:appgroup /extracted/dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=appuser:appgroup /extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /extracted/application/ ./

# Drop privileges to non-root user
USER appuser:appgroup

# Production JVM flags optimized for container environments:
# - UseContainerSupport: Adapts JVM memory to Docker/K8s cgroups
# - MaxRAMPercentage=75: Allocates up to 75% of container RAM to heap
# - ExitOnOutOfMemoryError: Crashes fast so container orchestrator can restart the pod
# - Non-blocking random source
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV SERVER_PORT=8080

EXPOSE 8080

# Health check via Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

# Launch using Spring Boot's modern JarLauncher (handles layered classpath)
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
