# -- Stage 1: Build ----------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common-library/pom.xml common-library/
COPY common-library/src common-library/src
COPY notification-service/pom.xml notification-service/
COPY notification-service/src notification-service/src

RUN --mount=type=cache,target=/root/.m2 `
    ./mvnw -pl common-library,notification-service -am clean package -DskipTests --no-transfer-progress

# -- Stage 2: Runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/notification-service/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", `
  "-XX:+UseContainerSupport", `
  "-XX:MaxRAMPercentage=75.0", `
  "-Djava.security.egd=file:/dev/./urandom", `
  "-jar", "app.jar"]
