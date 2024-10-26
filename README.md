# data-prepper-logs-test

- For OpenSearch TA.

1. Execute the following command on the Ubuntu 22.04.5 instance with `SDKMAN!` and `Docker Engine` installed.

```shell
sdk install java 23-open
sdk use java 23-open

git clone git@github.com:linghengqian/data-prepper-logs-test.git
cd ./data-prepper-logs-test/

docker compose --file ./opensearch/docker-compose.yml up -d

./mvnw clean dependency:get -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.9.0

./mvnw clean spring-boot:run \
  -Dspring-boot.run.agents="$HOME/.m2/repository/io/opentelemetry/javaagent/opentelemetry-javaagent/2.9.0/opentelemetry-javaagent-2.9.0.jar" \
  -Dspring-boot.run.jvmArguments="\
  -Dotel.service.name='1-linghengqian-smoke-tests' \
  -Dotel.exporter.otlp.endpoint='http://localhost:24321'\
  "
```

2. Open `http://localhost:14321/app/observability-traces#/services` in Microsoft Edge browser and log in with the
   account `admin` and the password `opensearchNode1Test`.
   The data schema named `Data Prepper` will not have any services.
   However, the data schema named `Custom source` will display `1-linghengqian-smoke-tests` normally.
