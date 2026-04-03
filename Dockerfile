# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Dependency-ləri öncədən çəkirik (layer caching üçün)
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

# Kodu kopyalayıb paketi yığırıq
COPY src ./src
RUN mvn -B -ntp -DskipTests clean package

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app


# Performans və container dəstəyi üçün Java tənzimləmələri
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"


COPY --from=build /workspace/target/Paygenix-0.0.1-SNAPSHOT.jar /app/app.jar

# --- KRİTİK DÜZƏLİŞ 2 ---
# Sənin application.properties-də port 8000-dir, ona görə bura da 8000 yazılmalıdır.
EXPOSE 8000

ENTRYPOINT ["java","-jar","/app/app.jar"]