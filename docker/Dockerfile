FROM openjdk:18

ENV GAMEYFIN_ROOT=/opt/gameyfin-library

RUN groupadd gameyfin && useradd gameyfin -g gameyfin && \
    mkdir -p /opt/gameyfin ${GAMEYFIN_ROOT} && \
    chown -R gameyfin:gameyfin /opt/gameyfin ${GAMEYFIN_ROOT}

USER gameyfin:gameyfin

ARG JAR_FILE=./backend/target/*.jar
COPY ${JAR_FILE} /opt/gameyfin/gameyfin.jar

WORKDIR /opt/gameyfin

ENTRYPOINT ["java", "-jar", "gameyfin.jar"]