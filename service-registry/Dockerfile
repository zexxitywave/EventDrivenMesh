# -- Stage 1: Build ----------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common-library/pom.xml common-library/
COPY common-library/src common-library/src
COPY service-registry/pom.xml service-registry/
COPY service-registry/src service-registry/src

RUN --mount=type=cache,target=/root/.m2 `
    ./mvnw -pl common-library,service-registry -am clean package -DskipTests --no-transfer-progress

# -- Stage 2: Runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/service-registry/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", `
  "-XX:+UseContainerSupport", `
  "-XX:MaxRAMPercentage=75.0", `
  "-Djava.security.egd=file:/dev/./urandom", `
  "-jar", "app.jar"]
