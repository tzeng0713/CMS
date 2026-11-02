FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.source="https://github.com/tzeng0713/CMS"
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# e2-micro 只有 1GB 記憶體，JVM heap 預設會抓太多，用 JAVA_OPTS 收緊，
# docker-compose 可以覆寫這個環境變數調整。
ENV JAVA_OPTS="-Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
