# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY --chmod=0755 mvnw ./mvnw
COPY .mvn/ .mvn/
COPY pom.xml ./pom.xml
COPY config/ config/
COPY account-service/pom.xml account-service/pom.xml
COPY transfer_service/pom.xml transfer_service/pom.xml
COPY transfer_service/src/ transfer_service/src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -pl transfer_service -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN useradd --system --uid 10001 appuser
USER appuser
COPY --from=build /build/transfer_service/target/app.jar ./app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
