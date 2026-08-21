FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY src src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system nanbei \
    && useradd --system --gid nanbei --home-dir /app nanbei

WORKDIR /app
COPY --from=build /workspace/target/nanbei-backend-*.jar app.jar

USER nanbei
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
