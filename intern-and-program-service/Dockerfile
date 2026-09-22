FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache mysql-client
WORKDIR /app
COPY build/libs/*.jar ./
RUN rm -f *-plain.jar || true
ENTRYPOINT ["sh", "-c", "java -jar $(ls *.jar | head -n 1)"]
