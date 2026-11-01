FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:resolve -B

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx350m", "-Xms200m", "-XX:+UseSerialGC", "-Dspring.jpa.hibernate.ddl-auto=none", "-Dhibernate.temp.use_jdbc_metadata_defaults=false", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]