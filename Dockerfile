# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd -r app && useradd -r -g app app
# SerialGC + TieredStopAtLevel=1 + CDS archive (below) shorten Render free-tier cold starts.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Dspring.jmx.enabled=false -Djava.security.egd=file:/dev/./urandom"
COPY --from=build /app/target/*.jar app.jar
# Render's free plan has ~0.1 CPU, where a cold JVM start takes minutes. A class data sharing
# archive recorded at build time (context refresh only, no database needed) cuts that sharply.
RUN java -Djarmode=tools -jar app.jar extract --destination extracted \
  && java $JAVA_OPTS -XX:ArchiveClassesAtExit=extracted/app.jsa \
    -Dspring.context.exit=onRefresh \
    -Dspring.flyway.enabled=false \
    -Dspring.jpa.hibernate.ddl-auto=none \
    -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
    -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
    -Dspring.datasource.hikari.initialization-fail-timeout=-1 \
    -Dapp.seed-demo=false \
    -jar extracted/app.jar \
  && chown -R app:app extracted
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=300s --retries=5 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=extracted/app.jsa -jar extracted/app.jar"]
