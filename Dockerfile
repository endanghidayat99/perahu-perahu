# Image tunggal: Vue (Vuetify) dibundel ke dalam Spring Boot.
# Build: ./deploy/build.sh   (lihat README.md)
ARG NODE_IMAGE=node:20-alpine
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy

# ---------- 1. build frontend ----------
FROM ${NODE_IMAGE} AS ui
WORKDIR /ui
ARG NPM_REGISTRY=
COPY frontend/package.json frontend/package-lock.json* ./
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi \
 && (npm ci --no-audit --no-fund || npm install --no-audit --no-fund)
COPY frontend/ ./
# vite.config.js menulis ke ../backend (untuk pengembangan lokal); di image tulis ke dist
RUN npx vite build --outDir dist --emptyOutDir

# ---------- 2. build backend ----------
FROM ${MAVEN_IMAGE} AS api
WORKDIR /src
ARG MAVEN_MIRROR_URL=
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; fi
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY backend/src ./src
# ganti hasil build frontend lama dengan yang baru
RUN rm -rf src/main/resources/static
COPY --from=ui /ui/dist ./src/main/resources/static
RUN mvn -q -B package

# ---------- 3. runtime ----------
FROM ${RUNTIME_IMAGE}
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 -g root app
WORKDIR /app
COPY --from=api /src/target/perahu-perahu.jar /app/app.jar
USER 1001
EXPOSE 8080
ENV JAVA_OPTS="-Xms128m -Xmx512m" TZ=Asia/Jakarta SERVER_PORT=8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:8080/ >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
