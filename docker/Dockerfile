FROM eclipse-temurin:21-jre-alpine

ENV GAMEYFIN_SOURCES=/opt/gameyfin-library
ENV USER=gameyfin


RUN addgroup gameyfin && \
    adduser --disabled-password --gecos "" --ingroup "$USER" --no-create-home "$USER" && \
    mkdir -p /opt/gameyfin ${GAMEYFIN_SOURCES} && \
    chown -R gameyfin:gameyfin /opt/gameyfin ${GAMEYFIN_SOURCES}

USER gameyfin:gameyfin

ARG JAR_FILE=./backend/target/*.jar
COPY ${JAR_FILE} /opt/gameyfin/gameyfin.jar

WORKDIR /opt/gameyfin

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "gameyfin.jar"]
