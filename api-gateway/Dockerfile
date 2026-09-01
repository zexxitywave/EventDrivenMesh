# -- Stage 1: Build ----------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common-library/pom.xml common-library/
COPY common-library/src common-library/src
COPY api-gateway/pom.xml api-gateway/
COPY api-gateway/src api-gateway/src

RUN --mount=type=cache,target=/root/.m2 `
    ./mvnw -pl common-library,api-gateway -am clean package -DskipTests --no-transfer-progress

# -- Stage 2: Runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", `
  "-XX:+UseContainerSupport", `
  "-XX:MaxRAMPercentage=75.0", `
  "-Djava.security.egd=file:/dev/./urandom", `
  "-jar", "app.jar"]
