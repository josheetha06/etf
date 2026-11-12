# syntax=docker/dockerfile:1

# ---- Build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/ett-hr-portal-*.jar extract --layers --launcher --destination /layers

# ---- Run ----
FROM eclipse-temurin:25-jre
RUN groupadd --system ett && useradd --system --gid ett --home-dir /app ett \
    && mkdir -p /var/lib/ett/files && chown -R ett:ett /var/lib/ett
WORKDIR /app
# Dependencies change least often, so they go first for faster rebuilds.
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER ett
ENV ETT_STORAGE_DIR=/var/lib/ett/files \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME /var/lib/ett/files
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
