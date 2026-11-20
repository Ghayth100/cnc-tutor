# ---- Étape 1 : compilation ----
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Étape 2 : exécution (JRE seul, image légère) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/cnc-tutor-*.jar app.jar

# Mémoire adaptée aux petites instances (512 Mo sur l'offre gratuite de Render)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -Djava.awt.headless=true"
# Render (et la plupart des hébergeurs) fournissent le port dans $PORT
ENV PORT=8080
EXPOSE 8080

# La clé est fournie par l'hébergeur via la variable d'environnement LLM_API_KEY
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
