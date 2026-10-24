# syntax=docker/dockerfile:1

# --- Build: compile and package with the Maven wrapper ------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# Tests need Docker (Testcontainers), so they run on the host: see the README.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/mnp-backend-*.jar extract --layers --launcher --destination target/extracted

# --- Run: JRE only, non-root, dependencies in their own layers so code changes rebuild fast ----
FROM eclipse-temurin:21-jre
RUN groupadd --system mnp && useradd --system --gid mnp --no-create-home mnp
WORKDIR /app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER mnp
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
