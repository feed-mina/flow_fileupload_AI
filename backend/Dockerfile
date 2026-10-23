FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp package -DskipTests
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system flow && useradd --system --gid flow flow
WORKDIR /app
RUN mkdir -p /app/uploads && chown flow:flow /app/uploads
COPY --from=build --chown=flow:flow /src/target/flow-policy.jar app.jar
USER flow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
