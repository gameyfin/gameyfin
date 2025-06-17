# syntax=docker/dockerfile:1.4
FROM eclipse-temurin:21-jre-alpine

MAINTAINER grimsi

# Install necessary packages
RUN apk add --no-cache su-exec tini shadow

ENV USER=gameyfin

RUN addgroup gameyfin && \
    adduser --disabled-password --gecos "" --ingroup "$USER" --no-create-home "$USER"

WORKDIR /opt/gameyfin

# Create necessary directories with appropriate permissions
RUN mkdir -p plugins db data logs && \
    chown -R gameyfin:gameyfin .

# Copy entrypoint script and set permissions
COPY --chown=gameyfin:gameyfin ./docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Copy application jar (not ending with -plain.jar)
COPY --chown=gameyfin:gameyfin ./app/build/libs/ /tmp/app-libs/
RUN find /tmp/app-libs -type f -name "*.jar" ! -name "*-plain.jar" -exec cp {} gameyfin.jar \; && \
    rm -rf /tmp/app-libs

# Copy all plugin jars
COPY --chown=gameyfin:gameyfin ./plugins/ /tmp/plugins/
RUN find /tmp/plugins -type f -path "*/build/libs/*.jar" -exec cp {} plugins/ \; && \
    rm -rf /tmp/plugins

EXPOSE 8080

ENTRYPOINT ["/sbin/tini", "--", "/entrypoint.sh"]