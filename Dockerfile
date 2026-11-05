# Build stage: compiles the jar and builds a trimmed Java runtime with only the modules
# the app needs. Alpine is used here too, so the runtime matches the final image's C library.
# Tests run in CI, not here.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests package \
 && cp target/flagbox-*.jar app.jar \
 # jdeps only reads the app's own classes, not the nested Spring Boot jars, so the
 # modules the libraries need (java.sql for JDBC, and others) are listed explicitly below.
 && jdeps --ignore-missing-deps --print-module-deps app.jar > modules.txt \
 && jlink --add-modules "$(cat modules.txt),java.desktop,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.naming,java.management,java.instrument,java.logging,java.xml,jdk.unsupported,jdk.crypto.ec" \
      --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output /runtime

# Runtime stage: Alpine with a trimmed Java runtime, running as a non-root user.
# No keys are baked in; the bootstrap admin key and database settings come from the environment.
FROM alpine:3.22
RUN apk add --no-cache ca-certificates tzdata \
 && addgroup -S flagbox && adduser -S -G flagbox flagbox
ENV JAVA_HOME=/opt/java PATH=/opt/java/bin:$PATH
COPY --from=build /runtime /opt/java
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
