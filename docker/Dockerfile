FROM eclipse-temurin:21-jre-alpine

ENV GAMEYFIN_SOURCES=/opt/gameyfin-library

RUN groupadd gameyfin && useradd gameyfin -g gameyfin && \
    mkdir -p /opt/gameyfin ${GAMEYFIN_SOURCES} && \
    chown -R gameyfin:gameyfin /opt/gameyfin ${GAMEYFIN_SOURCES}

USER gameyfin:gameyfin

ARG JAR_FILE=./backend/target/*.jar
COPY ${JAR_FILE} /opt/gameyfin/gameyfin.jar

WORKDIR /opt/gameyfin

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "gameyfin.jar"]
