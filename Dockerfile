# ============================================
# Stage 1: 빌드 단계
# ============================================
FROM gradle:8.11-jdk17 AS build
WORKDIR /app

# Gradle 캐싱을 위해 먼저 의존성 파일만 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 의존성 다운로드 (캐싱됨)
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사
COPY src ./src

# 빌드 실행
RUN gradle clean build -x test --no-daemon

# ============================================
# Stage 2: 실행 단계
# ============================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/promptstudio-0.0.1-SNAPSHOT.jar app.jar

# 헬스체크 추가
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]