FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -DskipTests package

# noble (24.04) pin: Playwright does not support newer Ubuntu bases yet
FROM eclipse-temurin:25-jre-noble
# curl is used by the docker-compose healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Full Chromium + system deps for Playwright (Answear, Intertop, Stradivarius, Zara are behind
# Cloudflare/Akamai). Not the lighter headless shell: Akamai on Zara denies it.
# The CLI is run from inside the fat jar.
RUN java -cp app.jar -Dloader.main=com.microsoft.playwright.CLI \
    org.springframework.boot.loader.launch.PropertiesLauncher install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/*
# Playwright.create() otherwise downloads full Chromium, Firefox and WebKit (~500 MB) on the
# first fetch in every new container, which blocks the bot for minutes after each deploy
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
