# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw ./
COPY .mvn .mvn

# Copy parent POM and module POMs first for dependency caching
COPY pom.xml ./
COPY contracts/pom.xml contracts/
COPY gateway/pom.xml gateway/
COPY auth-service/pom.xml auth-service/
COPY config-service/pom.xml config-service/
COPY rollout-service/pom.xml rollout-service/
COPY evaluation-service/pom.xml evaluation-service/
COPY audit-service/pom.xml audit-service/

RUN chmod +x mvnw \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

# Download dependencies (cached unless POMs change)
RUN ./mvnw dependency:go-offline -pl gateway -am -B -q

# Copy source code
COPY contracts/src contracts/src
COPY gateway/src gateway/src

# Build the module
RUN ./mvnw package -pl gateway -am -B -q -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
WORKDIR /app
COPY --from=build /workspace/gateway/target/cloudscale-gateway-0.1.0-SNAPSHOT.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
