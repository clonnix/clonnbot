# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src src

# Build the jar
RUN mvn package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Render provides PORT automatically; expose a default
ENV PORT=8080

# Start the bot
CMD ["java", "-jar", "app.jar"]