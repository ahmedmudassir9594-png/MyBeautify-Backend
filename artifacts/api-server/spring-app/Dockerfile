
FROM maven:3.9-eclipse-temurin-19 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:19-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-Xmx350m", "-Xms200m", "-XX:+UseSerialGC", "-Dspring.jpa.hibernate.ddl-auto=none", "-Dhibernate.temp.use_jdbc_metadata_defaults=false", "-jar", "app.jar"]
