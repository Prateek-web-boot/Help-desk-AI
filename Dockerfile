# Stage 1: Build the application using Maven and Java 17
FROM maven:3.8.7-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image using a stable JRE
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copies the jar file generated in the build stage
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Environment variable for port ensures Render can bind correctly
ENTRYPOINT ["java", "-jar", "app.jar"]