# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom first so dependency resolution is cached across builds unless pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Runtime stage ----
# Slim JRE (not the full JDK) — smaller image, no compiler/build tools needed to just run the JAR.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as a non-root user — don't run the app as root inside the container.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Reasonable default JVM memory settings for a small container; override via JAVA_OPTS if needed.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
