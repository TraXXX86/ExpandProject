FROM maven:3.9.6-eclipse-temurin-11 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY commons/pom.xml commons/pom.xml
COPY importdata/pom.xml importdata/pom.xml
COPY model/pom.xml model/pom.xml

COPY commons/src commons/src
COPY importdata/src importdata/src
COPY model/src model/src

RUN mvn -pl importdata -am package -DskipTests

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /workspace/importdata/target/expandproject-importdata.jar /app/expandproject-importdata.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/expandproject-importdata.jar", "--api", "8080"]
