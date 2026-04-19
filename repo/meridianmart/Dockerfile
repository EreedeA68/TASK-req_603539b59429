# Build stage
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y maven && mvn package -DskipTests -q

# Test stage: extends builder with headless Chrome, curl, and Node.js (for JS unit tests)
FROM builder AS tester
# Install google-chrome-stable (real .deb) — ubuntu 22.04's 'chromium' is a snap stub that
# cannot run inside Docker. Then pre-download the matching chromedriver so tests need no
# network access at runtime.
RUN apt-get update && apt-get install -y curl wget gnupg unzip nodejs npm \
    && wget -q -O - https://dl.google.com/linux/linux_signing_key.pub \
       | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
       > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y google-chrome-stable \
    && CHROME_MAJOR=$(google-chrome --version | grep -oP '\d+' | head -1) \
    && DRIVER_VER=$(curl -fsSL "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_${CHROME_MAJOR}") \
    && wget -q "https://storage.googleapis.com/chrome-for-testing-public/${DRIVER_VER}/linux64/chromedriver-linux64.zip" -O /tmp/cd.zip \
    && unzip /tmp/cd.zip -d /tmp \
    && mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/chromedriver \
    && chmod +x /usr/local/bin/chromedriver \
    && rm -rf /tmp/cd.zip /tmp/chromedriver-linux64 /var/lib/apt/lists/*
COPY package*.json ./
RUN npm install
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "target/meridianmart-1.0.0.jar"]

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update && apt-get install -y \
    curl \
    mariadb-client \
    cron \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/*.jar app.jar

# Backup script
RUN mkdir -p /backup /scripts

COPY scripts/backup.sh /scripts/backup.sh
COPY scripts/restore.sh /scripts/restore.sh
RUN chmod +x /scripts/backup.sh /scripts/restore.sh 2>/dev/null || true

# Cron for daily backup at 1:00 AM
RUN echo "0 1 * * * /scripts/backup.sh >> /var/log/backup.log 2>&1" | crontab -

EXPOSE 8080

# Start cron daemon before the application so scheduled backups run
ENTRYPOINT ["sh", "-c", "cron && exec java -jar app.jar"]
