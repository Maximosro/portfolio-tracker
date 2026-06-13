# ============================================
# Stage 1: Build — compila el proyecto con Maven
# ============================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copiar descriptor de dependencias primero para cachear la capa
COPY pom.xml .

# Descargar dependencias (capa cacheable mientras pom.xml no cambie)
RUN mvn dependency:go-offline -B -q

# Copiar código fuente
COPY src/ src/

# Compilar y empaquetar (saltar tests — usan H2 que no necesita config adicional)
RUN mvn package -DskipTests -B -q

# ============================================
# Stage 2: Runtime — JRE mínimo para ejecución
# ============================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Instalar wget para healthcheck
RUN apk add --no-cache wget

# Crear usuario no-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar el fat JAR desde la stage de build
COPY --from=build /app/target/PortfolioTracker.jar app.jar

# Cambiar a usuario no-root
USER appuser:appgroup

# Exponer el puerto de la app (solo documentación — networking vía Docker)
EXPOSE 19480

# Forzar perfil pro (Supabase) en el contenedor
ENV SPRING_PROFILES_ACTIVE=pro

# JVM options para producción (sobrescribible vía env var)
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Healthcheck: verifica que el context-path responde
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:19480/portfoliotracker/ || exit 1

# Punto de entrada
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
