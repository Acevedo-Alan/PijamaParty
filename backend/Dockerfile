# Backend image (T-E0-06). Tests run in CI (they need Docker for Testcontainers), so the image build only packages.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package \
    && java -Djarmode=tools -jar target/store-*.jar extract --layers --launcher --destination /build/extracted

FROM eclipse-temurin:21-jre-alpine
# Fixed uid/gid, so host files and volumes can be given to this user predictably. The uploads volume takes the owner
# of /srv/uploads when Docker first creates it.
RUN addgroup -S -g 10001 store && adduser -S -u 10001 -G store store \
    && mkdir -p /srv/uploads && chown store:store /srv/uploads
WORKDIR /app
# Spring Boot layers: dependencies change less often than the application, so they are cached apart.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER store
EXPOSE 8080
# RNF-21: 768 MB heap on the 4 GB server; a JVM out of memory exits so Docker restarts it.
ENV JAVA_TOOL_OPTIONS="-Xmx768m -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Argentina/Buenos_Aires"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
