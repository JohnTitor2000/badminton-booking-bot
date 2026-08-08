# syntax=docker/dockerfile:1

FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM amazoncorretto:21-alpine
WORKDIR /app
RUN apk add --no-cache fontconfig ttf-dejavu \
    && addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/badminton-booking-bot.jar app.jar
USER app
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
