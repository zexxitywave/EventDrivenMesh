# -- Stage 1: Build ----------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common-library/pom.xml common-library/
COPY common-library/src common-library/src
COPY seller-service/pom.xml seller-service/
COPY seller-service/src seller-service/src

RUN --mount=type=cache,target=/root/.m2 `
    ./mvnw -pl common-library,seller-service -am clean package -DskipTests --no-transfer-progress

# -- Stage 2: Runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/seller-service/target/*.jar app.jar

EXPOSE 8091

ENTRYPOINT ["java", `
  "-XX:+UseContainerSupport", `
  "-XX:MaxRAMPercentage=75.0", `
  "-Djava.security.egd=file:/dev/./urandom", `
  "-jar", "app.jar"]
