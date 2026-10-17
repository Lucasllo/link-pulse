# syntax=docker/dockerfile:1
# Imagem do Link Pulse: build com o ./mvnw do repositório e runtime só com o JRE, em camadas.

# ---------- build: compila com o mvnw do repositório (mesmo Maven do CI) ----------
FROM eclipse-temurin:24-jdk-noble AS build
# unzip: sem ele o mvnw troca o .zip pelo .tar.gz do Maven, cujo SHA-256 não bate com o
# distributionSha256Sum fixado no maven-wrapper.properties (que é do .zip).
RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src
# --chmod: o bit executável não sobrevive a um checkout no Windows.
COPY --chmod=0755 mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
# O Checkstyle roda na fase validate do package e lê a config daqui.
COPY config/ config/
COPY src/ src/
# Cache do ~/.m2 (dependências e a distribuição do Maven baixada pelo wrapper) entre builds.
# O glob não casa o .jar.original.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package \
 && cp target/link-pulse-*.jar /src/app.jar
# Camadas: dependencies, spring-boot-loader, snapshot-dependencies e application.
RUN java -Djarmode=tools -jar /src/app.jar extract --layers --launcher --destination /src/extracted

# ---------- runtime: JRE com curl (para o HEALTHCHECK), usuário numérico ----------
FROM eclipse-temurin:24-jre-noble
LABEL org.opencontainers.image.source="https://github.com/Lucasllo/link-pulse"
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
# Da camada que menos muda para a que mais muda.
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER 10001:10001
# 8080: API. 8081: actuator (health e métricas), que não é publicada no host.
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --start-interval=2s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1
# Forma exec: o java é o PID 1 e recebe o SIGTERM (graceful shutdown do Boot).
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
