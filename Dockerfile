# Stage 1: Build
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Runtime (Java + Node.js)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Node.js (Required for npx @modelcontextprotocol/server-github)
RUN apt-get update && apt-get install -y curl \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g @modelcontextprotocol/server-github

# Copy the jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Render needs to bind to the $PORT variable
ENTRYPOINT ["java", "-Dserver.port=${PORT:10000}", "-jar", "app.jar"]