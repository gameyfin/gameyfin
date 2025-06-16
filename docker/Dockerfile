# syntax=docker/dockerfile:1.4
FROM eclipse-temurin:21-jre-alpine

ENV USER=gameyfin

RUN addgroup gameyfin && \
    adduser --disabled-password --gecos "" --ingroup "$USER" --no-create-home "$USER"

USER gameyfin:gameyfin

WORKDIR /opt/gameyfin

# Copy application jar (not ending with -plain.jar)
COPY --chown=gameyfin:gameyfin ./app/build/libs/ /tmp/app-libs/
RUN find /tmp/app-libs -type f -name "*.jar" ! -name "*-plain.jar" -exec cp {} /opt/gameyfin/gameyfin.jar \; && \
    rm -rf /tmp/app-libs

# Copy all plugin jars
COPY --chown=gameyfin:gameyfin ./plugins/ /tmp/plugins/
RUN mkdir -p /opt/gameyfin/plugins && \
    find /tmp/plugins -type f -path "*/build/libs/*.jar" -exec cp {} /opt/gameyfin/plugins/ \; && \
    rm -rf /tmp/plugins

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "gameyfin.jar"]