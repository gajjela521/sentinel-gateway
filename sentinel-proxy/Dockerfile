FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY target/sentinel-proxy-0.1.0-SNAPSHOT.jar sentinel-proxy.jar

EXPOSE 8080

ENTRYPOINT ["java", "--enable-preview", "-jar", "sentinel-proxy.jar", \
            "--port=8080", \
            "--upstream=${SENTINEL_UPSTREAM}", \
            "--policy=${SENTINEL_POLICY_FILE}"]
