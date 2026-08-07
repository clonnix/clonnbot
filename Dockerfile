# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom first for better layer caching
COPY pom.xml .

# Copy source code
COPY src src

# Build the jar (this resolves dependencies itself, correctly
# respecting exclusions/dependencyManagement — unlike
# `mvn dependency:go-offline`, which over-eagerly touches excluded
# branches of the raw dependency tree and was hitting a broken
# upstream Jackson SNAPSHOT that the real build never needs)
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
