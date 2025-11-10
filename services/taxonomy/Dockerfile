# syntax=docker/dockerfile:1

############################################
# Build stage
############################################
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Leverage Docker layer caching where possible
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests clean package

############################################
# Runtime stage
############################################
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the fat jar produced by spring-boot-maven-plugin
COPY --from=build /build/target/*.jar /app/app.jar

EXPOSE 8080
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

