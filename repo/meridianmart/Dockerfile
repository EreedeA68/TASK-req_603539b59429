# Build stage
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y maven && mvn package -DskipTests -q

# Test stage: extends builder with headless Chrome, curl, and Node.js (for JS unit tests)
FROM builder AS tester
RUN apt-get update && apt-get install -y \
    chromium \
    chromium-driver \
    curl \
    nodejs \
    npm \
    && rm -rf /var/lib/apt/lists/*
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
