# syntax=docker/dockerfile:1

# --- build stage ---
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q -DskipTests package

# --- runtime stage ---
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S uaiou && adduser -S uaiou -G uaiou
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER uaiou

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
