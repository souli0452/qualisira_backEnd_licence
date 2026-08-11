# Étape 1 : Build avec Maven et Java 21
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Étape 2 : Image finale légère avec JRE 21
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar licences-service.jar

# Port d'écoute
EXPOSE 8099

# Lancement de l'application
ENTRYPOINT ["java", "-jar", "licences-service.jar"]
