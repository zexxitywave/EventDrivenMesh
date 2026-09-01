# -- Stage 1: Build ----------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common-library/pom.xml common-library/
COPY common-library/src common-library/src
COPY order-service/pom.xml order-service/
COPY order-service/src order-service/src

RUN --mount=type=cache,target=/root/.m2 `
    ./mvnw -pl common-library,order-service -am clean package -DskipTests --no-transfer-progress

# -- Stage 2: Runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/order-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", `
  "-XX:+UseContainerSupport", `
  "-XX:MaxRAMPercentage=75.0", `
  "-Djava.security.egd=file:/dev/./urandom", `
  "-jar", "app.jar"]
