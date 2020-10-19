FROM clojure:lein-alpine AS build
MAINTAINER Jeff Mataya <jmataya@hey.com>

COPY . .
RUN lein uberjar

FROM openjdk:8-alpine
COPY --from=build /tmp/target/uberjar/sanilac-0.1.0-SNAPSHOT-standalone.jar sanilac-0.1.0.SNAPSHOT-standalone.jar

EXPOSE 8080
CMD ["java", "-jar", "sanilac-0.1.0-SNAPSHOT-standalone.jar"]
