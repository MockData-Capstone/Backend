# syntax=docker/dockerfile:1

# 빌드: 실행 가능한 JAR(bootJar)만 만든다. 테스트는 CI(GitHub Actions)에서 돌린다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
# Gradle 캐시를 빌드 사이에 재사용해 다시 배포할 때 의존성을 새로 받지 않는다.
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

# 실행
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 zipfit && mkdir -p /app/uploads && chown zipfit /app/uploads
COPY --from=build /src/build/libs/*.jar app.jar
USER zipfit
EXPOSE 8080
# 힙은 컨테이너 메모리 제한(docker-compose.yml의 mem_limit)의 75%까지 쓴다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
