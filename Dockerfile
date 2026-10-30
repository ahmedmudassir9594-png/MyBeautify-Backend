# Stage 1: Build JAR using Java 21 & Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml and source code from nested path
COPY artifacts/api-server/spring-app/pom.xml .
COPY artifacts/api-server/spring-app/src ./src

# Compile and package application
RUN mvn clean package -DskipTests

# Stage 2: Lightweight Java 21 Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

# Execute with memory limits optimized for 512MB RAM
ENTRYPOINT ["java", "-Xmx350m", "-Xms200m", "-XX:+UseSerialGC", "-jar", "app.jar"]
