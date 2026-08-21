# Build from the repository root so :ai is available:
#   docker build -f website/Dockerfile -t kitten-website .
#   docker run --rm -p 8080:8080 kitten-website

FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /src
COPY ai/ ai/
COPY website/ website/

WORKDIR /src/website
RUN chmod +x gradlew \
  && ./gradlew :ktor:installDist --no-daemon

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system kitten \
  && useradd --system --gid kitten --home /app --shell /usr/sbin/nologin kitten

WORKDIR /app
COPY --from=build /src/website/ktor/build/install/ktor/ ./

USER kitten
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["/app/bin/ktor"]
