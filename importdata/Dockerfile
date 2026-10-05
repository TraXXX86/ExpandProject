# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY commons/pom.xml commons/pom.xml
COPY importdata/pom.xml importdata/pom.xml

COPY commons/src commons/src
COPY importdata/src importdata/src

RUN --mount=type=secret,id=proxy_ca,target=/run/secrets/proxy_ca,required=false \
    --mount=type=secret,id=maven_settings,target=/run/secrets/maven_settings,required=false \
    set -eu; \
    if [ -f /run/secrets/proxy_ca ]; then \
      cp "${JAVA_HOME}/lib/security/cacerts" /tmp/maven-cacerts; \
      keytool -importcert -trustcacerts -noprompt -alias build-proxy-ca \
        -file /run/secrets/proxy_ca -keystore /tmp/maven-cacerts -storepass changeit; \
      export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/tmp/maven-cacerts -Djavax.net.ssl.trustStorePassword=changeit"; \
    fi; \
    if [ -f /run/secrets/maven_settings ]; then \
      mvn -s /run/secrets/maven_settings -B -ntp -pl importdata -am package -DskipTests; \
    else \
      mvn -B -ntp -pl importdata -am package -DskipTests; \
    fi; \
    rm -f /tmp/maven-cacerts

FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl passwd \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 api \
    && useradd --system --uid 10001 --gid api --home-dir /app --shell /usr/sbin/nologin api \
    && mkdir -p /app/logs /data \
    && chown -R api:api /app /data
WORKDIR /app
COPY --from=build /workspace/importdata/target/expandproject-importdata.jar /app/expandproject-importdata.jar

USER api
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=12 \
  CMD curl --fail --silent 'http://127.0.0.1:8080/api/health?deep=true' || exit 1

ENTRYPOINT ["java", "-jar", "/app/expandproject-importdata.jar", "--api", "8080"]
