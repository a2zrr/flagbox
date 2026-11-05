# Build stage: compiles the jar with the Maven wrapper. Tests run in CI, not here.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests package \
 && cp target/flagbox-*.jar /workspace/app.jar

# Runtime stage: a JRE on Alpine, running as a non-root user. No keys are baked in;
# the bootstrap admin key and database settings come from the environment.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S flagbox && adduser -S -G flagbox flagbox
WORKDIR /app
COPY --from=build --chown=flagbox:flagbox /workspace/app.jar /app/app.jar
USER flagbox
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:${PORT}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
