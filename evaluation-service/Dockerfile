# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw ./
COPY .mvn .mvn

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
RUN ./mvnw dependency:go-offline -pl evaluation-service -am -B -q

COPY contracts/src contracts/src
COPY evaluation-service/src evaluation-service/src

RUN ./mvnw package -pl evaluation-service -am -B -q -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
WORKDIR /app
COPY --from=build /workspace/evaluation-service/target/cloudscale-evaluation-service-0.1.0-SNAPSHOT.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
