FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copia o pom.xml e o código fonte
COPY pom.xml ./
COPY src ./src

# Compila usando o Maven oficial instalado na imagem
RUN mvn clean package -DskipTests

# Imagem final leve apenas para correr a aplicação
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]