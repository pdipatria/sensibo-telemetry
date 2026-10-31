# Sensibo Telemetry

Java 21 / Spring Boot application. Stores historical temperature and humidity
readings using Spring JDBC and PostgreSQL, with opt-in one-shot import and
scheduled polling modes. Polling mode includes a local start/stop/status API.

## Configuration

### Using mise (recommended for local development)

Install [mise](https://mise.jdx.dev/getting-started), then run from the project root:

```sh
cp mise.local.toml.example mise.local.toml
```

Edit `mise.local.toml` to set your device IDs, database password, and Sensibo API key.
The file is gitignored; the example contains no credentials. Database URL,
username, and history window default to the values in `mise.toml` and can also be
overridden in `mise.local.toml`. These files supply environment variables to the
application; they do not create a database or role.

Review the configuration files, then trust them and install the selected JDK:

```sh
mise trust mise.toml
mise trust mise.local.toml
mise install
mise exec -- ./mvnw -v
```

`mise.toml` selects Temurin Java 21 and mise sets both `JAVA_HOME` and `PATH`,
preventing Maven from picking up an older SDKMAN Java installation. The selector
allows Java 21 patch updates; it is not an exact patch-version pin.

Run project commands through mise to apply the environment and Java version:

```sh
mise run test
mise run build
mise run import
mise run poll
mise run run
```

`test` and `build` require Docker for the integration tests. `import` performs one
live import and exits; `poll` runs continuously with the control API; `run` starts
the application without importing. For an
already-built JAR:

```sh
mise exec -- java -jar target/sensibo-telemetry-0.0.1-SNAPSHOT.jar --spring.profiles.active=import
```

For automatic activation when entering the directory, add
`eval "$(mise activate zsh)"` to your `~/.zshrc` and open a new shell. Keep activation
after any SDKMAN initialization or manual `JAVA_HOME` exports. Explicit
`mise exec` and `mise run` commands work without shell activation. Avoid sharing
`mise env` output because it can include credentials. The local configuration
is not packaged into the JAR; on another machine provide the environment there.

### Manual environment configuration

Set these environment variables before starting the application:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SENSIBO_DEVICE_IDS` | Comma-separated device IDs, trimmed and deduplicated in order; empty entries are rejected | Required unless legacy ID is set |
| `SENSIBO_DEVICE_ID` | Legacy single-device fallback when the plural setting is blank | Empty |
| `SENSIBO_DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/sensibo` |
| `SENSIBO_DB_USERNAME` | Database role | `sensibo` |
| `SENSIBO_DB_PASSWORD` | Database password | Empty |
| `SENSIBO_API_KEY` | Sensibo API key (required for `import` and `poll` profiles) | Required for import/poll |
| `SENSIBO_HISTORY_DAYS` | Positive number of days of history to request | `1` |
| `SENSIBO_POLL_INTERVAL` | Positive delay between completed polling cycles (ISO-8601 duration) | `PT5M` |
| `SENSIBO_POLL_INITIAL_DELAY` | Nonnegative delay before the first polling attempt | `PT10S` |
| `SENSIBO_CONTROL_ADDRESS` | Polling control API bind address | `127.0.0.1` |
| `SENSIBO_CONTROL_PORT` | Polling control API HTTP port | `8080` |

The database and role must already exist. Flyway applies
the SQL migrations in `src/main/resources/db/migration` on startup; the role
needs permission to create tables in the configured schema.

Each import commits a `RUNNING` row in `import_execution` before contacting Sensibo.
Numbers start at 1 independently for each device. Empty successful responses count
as executions. Readings and the `SUCCEEDED` outcome commit together; ingestion
failures roll back all reading changes and record `FAILED` separately. Failure
reasons are controlled descriptions without credentials or raw response bodies.
The V2 migration adds nullable `measurement.import_seq_num` with a composite foreign
key to execution history. Existing readings remain null until imported again.

Measurements retain the unique device/type/timestamp constraint. On conflict,
only the execution number changes; the first stored measurement value is retained.
Timestamps use PostgreSQL `TIMESTAMP WITH TIME ZONE` and UTC JDBC values.

Run one importing application process per database/account configuration. Sequence
allocation assumes a single process. Database outages can prevent recording an
attempt. A crash can leave `RUNNING` history; subsequent attempts allocate the next
number without reconciliation. Execution history is retained without pruning.

## One-shot API import

Create an API key at <https://home.sensibo.com/me/api>. Set `SENSIBO_API_KEY` in
your environment along with the database settings and `SENSIBO_DEVICE_ID` above.
Do not put real credentials in source files or command-line arguments.

With Java 21 selected, run:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```

The `import` profile starts without a web server, imports all configured devices sequentially and closes the application. Each
device uses its own transaction. Failures do not prevent later devices from
importing, and any failure produces a nonzero exit code. Ordinary startup without
either the `import` or `poll` profile makes no API calls and does not require an API key.

The client uses Sensibo's documented
[`GET /pods/{device_id}/historicalMeasurements`](https://support.sensibo.com/api/operations/podsdevice_idhistoricalmeasurements/)
with `apiKey` and `days` query parameters. The default is one day; larger windows
may depend on the account's available history. Requests have a 10-second connection
timeout and a 60-second request timeout. Errors, including HTTP 401 and 429, fail
the import without automatic retries. Repeat the command to retry; overlapping
readings remain idempotent. Every attempt is retained in execution history.

The API key is not included in application error messages, URLs are not logged,
and HTTP redirects are not followed. `sensibo.api-base-url` defaults to
`https://home.sensibo.com/api/v2` and can be overridden for local tests; use only a
trusted endpoint because it receives the API key.

## Scheduled polling and control API

With the same device, database, and API-key configuration as a one-shot import:

```sh
mise run poll
```

Equivalent Maven and packaged-JAR commands:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=poll
java -jar target/sensibo-telemetry-0.0.1-SNAPSHOT.jar --spring.profiles.active=poll
```

Polling starts automatically after the initial delay. Each cycle visits enabled
devices sequentially in configured order, using one shared API key and history
window. Device failures do not prevent later devices from importing. The next
cycle starts after the configured interval following completion of the whole
cycle. An in-flight guard prevents overlapping cycles within the process.

Override the timing in your gitignored `mise.local.toml`, for example:

```toml
[env]
SENSIBO_POLL_INTERVAL = "PT2M"
SENSIBO_POLL_INITIAL_DELAY = "PT5S"
```

Add these keys to the existing `[env]` section. Durations are validated at
startup. `PT0S` is allowed for the initial delay, but the interval must be positive.
The history window defaults to one day and should exceed the polling interval
and expected downtime. Readings older than that window are not recovered after
a long outage; increase the window if the Sensibo account supports it.

Control the running process using:

```sh
curl http://127.0.0.1:8080/api/polling/status
curl -X POST http://127.0.0.1:8080/api/polling/stop
curl -X POST http://127.0.0.1:8080/api/polling/start
```

Use a device ID suffix for device-specific controls, or literal `*` for all devices:

```sh
curl http://127.0.0.1:8080/api/polling/status/device-one
curl -X POST http://127.0.0.1:8080/api/polling/stop/device-one
curl -X POST 'http://127.0.0.1:8080/api/polling/start/*'
```

Paths without a device suffix remain all-device aliases. Every response uses the
same envelope, with selected devices in configured order:

```json
{"devices":[{"deviceId":"device-one","enabled":true,"running":false,"lastStartedAt":null,"lastSucceededAt":null,"lastFailedAt":null,"importSeqNum":0,"lastSuccessfulImportSeqNum":0,"lastImportStatus":null}]}
```

Execution numbers and latest outcome come from PostgreSQL, including one-shot
imports and previous runs. Outcomes are `RUNNING`, `SUCCEEDED`, or `FAILED`.
Unknown devices return 404; unavailable execution history returns 503.
Timestamps are UTC. Runtime timestamps initially are null. `stop` allows an active
import to finish and skips future turns for that device. `start` resumes at the
next turn without triggering an immediate import. Both commands are idempotent.

Poll failures are logged without exception messages or credentials, recorded in
status, and retried at the next regular interval. This includes HTTP 429 responses;
there is no additional retry loop or automatic backoff. A failed ingestion rolls
back its transaction. The process remains running so it can recover from API or
database outages after startup; initial database/migration/configuration failures
still prevent startup. Stop the process with Ctrl+C; shutdown stops future ticks
and waits up to 70 seconds for an active attempt.

The control API is unauthenticated and binds to localhost by default. Keep it
local; remote exposure requires access controls outside this application. It is
available only in the `poll` profile. Pause state and runtime timestamps are kept in memory; restarting enables all
configured devices. Execution history persists across restarts. Use `import` and `poll` separately;
enabling both profiles fails startup.

## Build and run an executable JAR

From the project root, select JDK 21 and confirm that Maven uses it. On macOS:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -v
```

Build the executable archive and run all tests (Docker must be running):

```sh
./mvnw clean package
```

To use the existing local PostgreSQL image for the integration tests:

```sh
./mvnw -Dtest.postgres.image=postgres:latest clean package
```

If tests have already passed and you only need to rebuild the archive, you can
skip test execution (Docker is then not required):

```sh
./mvnw clean package -DskipTests
```

The Spring Boot Maven plugin produces
`target/sensibo-telemetry-0.0.1-SNAPSHOT.jar`. It includes compiled application
classes, runtime dependencies (including the PostgreSQL JDBC driver), application
configuration templates, and Flyway SQL migrations. Test classes, test fixtures,
and test-only dependencies such as Testcontainers are excluded. The archive does
not include a Java runtime, a PostgreSQL server, or database contents. Credentials
remain external: environment-variable values are not embedded during packaging.
The `.jar.original` file, if present, is the archive before Spring Boot repackaging;
use the `.jar` file for execution.

The runtime machine needs Java 21 or later, a reachable PostgreSQL database, and
the environment variables described above. Maven and the source checkout are not
needed to run the JAR. From the project root, perform a one-shot import with:

```sh
java -jar target/sensibo-telemetry-0.0.1-SNAPSHOT.jar --spring.profiles.active=import
```

This imports once and exits. To request a different history window, for example
two days, pass an application argument after the JAR name:

```sh
java -jar target/sensibo-telemetry-0.0.1-SNAPSHOT.jar --spring.profiles.active=import --sensibo.history-days=2
```

For ordinary application startup without an API import:

```sh
java -jar target/sensibo-telemetry-0.0.1-SNAPSHOT.jar
```

Ordinary startup remains running with the web server; stop it with Ctrl+C. Both
modes apply pending Flyway migrations on startup. If you copy the executable JAR
elsewhere, adjust the path in these commands accordingly.

## Verification

Use a Java 21 `JAVA_HOME` and a running Docker daemon:

```sh
./mvnw clean test
```

Integration tests start disposable PostgreSQL containers, apply the production
migrations, and exercise the Spring service. They do not connect to the configured
application database and fail rather than silently skip when Docker is unavailable.
Client tests use a local HTTP stub. The import-profile integration test exercises
startup fetching, JSON deserialization, database persistence, repeated imports,
and API failure without contacting Sensibo or requiring a live API key.
Polling tests exercise actual scheduled execution, recovery after HTTP 429,
HTTP start/stop/status, pause/resume, and duplicate prevention against PostgreSQL.
Concurrency tests verify that stop/start cannot overlap an in-flight import.
The default image is `postgres:17-alpine`; an existing local image can be selected:

```sh
./mvnw -Dtest.postgres.image=postgres:latest clean test
```
