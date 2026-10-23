FROM maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp package -DskipTests
FROM eclipse-temurin:17-jre-jammy@sha256:b3054a20e1a851574cdb1692dd0db5310c647e62214a2a7abecbec1b9e3ae8f1
RUN groupadd --system flow && useradd --system --gid flow flow
WORKDIR /app
RUN mkdir -p /app/uploads && chown flow:flow /app/uploads
COPY --from=build --chown=flow:flow /src/target/flow-policy.jar app.jar
USER flow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
