# Stage 1: Build the application using Maven and Java 17
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image using a stable JRE
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Node.js and NPM (required for MCP npx command)
RUN apt-get update && apt-get install -y curl \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Verify installations
RUN java -version && node -v && npm -v

# Copies the jar file generated in the build stage
COPY --from=build /app/target/*.jar app.jar

# Environment variable for port ensures Render can bind correctly
ENTRYPOINT ["java", "-Dserver.port=${PORT:10000}", "-jar", "app.jar"]