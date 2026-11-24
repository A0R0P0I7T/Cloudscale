# CloudScale Metrics Report

**Measurement date:** 2026-11-24  
**Scope:** Local source/build/test evidence only. These values are not production performance claims.

## Environment

| Item | Value |
| --- | --- |
| Host | macOS 27.0.1, arm64, 10 logical CPUs, 24 GiB RAM |
| Java | Temurin 21.0.13.1 from the checksum-verified local toolchain |
| Maven | 3.9.11 via `./mvnw` |
| Node / npm | Node v24.22.0, npm 11.19.1 |
| Browser tests | Chrome 157.0.0.0 |
| Docker | Docker CLI unavailable (`docker: command not found`) |

The wrapper requires an explicit `JAVA_HOME` in this shell because the system Java launcher is not registered with macOS. The successful command was:

```bash
env MAVEN_SKIP_RC=1 \
  JAVA_HOME=/private/var/folders/t0/rlmgz1vd7jvf2097nywt16nr0000gn/T/opencode/cloudscale-jdk21/Contents/Home \
  PATH=/private/var/folders/t0/rlmgz1vd7jvf2097nywt16nr0000gn/T/opencode/cloudscale-jdk21/Contents/Home/bin:$PATH \
  ./mvnw -B clean verify
```

An earlier bare `./mvnw -B verify` used the macOS launcher, printed “Unable to locate a Java Runtime,” and was terminated (exit 143) before tests ran. The clean verification above is the successful backend result; the failed launch is not counted as a test failure.

## Build and test results

| Area | Result | Method and sample | Evidence |
| --- | --- | --- | --- |
| Backend reactor | **BUILD SUCCESS**; 8/8 modules, 102 tests, 85 passed, 17 skipped, 0 failures/errors. | Maven Wrapper clean verification; Java 21.0.13.1 / Maven 3.9.11. The 17 skips are Docker-dependent PostgreSQL/Kafka/Redis tests. | `./mvnw -B clean verify`; 39 Surefire XML files under module `target/surefire-reports/`. |
| Backend line coverage | 41.30% (807/1,954 lines); branch coverage 47.23% (273/578); instruction coverage 40.00% (3,836/9,590). | One-off JaCoCo 0.8.13 instrumentation/report over the six service modules; the 17 Docker tests were skipped. The root and `contracts` modules are not part of this aggregate. | Per-service `target/site/jacoco/jacoco.xml` and `index.html`; aggregate parsed from six JaCoCo XML reports. |
| Local security checks | Request-level MockMvc checks for all four resource-server chains; Gateway WebTestClient checks; decoder tests reject missing/weak secrets and wrong issuers. Live container-backed service requests remain skipped. | Included in the 102-test backend suite. | `*SecurityChainTest.java`, `ResourceServerConfigurationTest.java`, `GatewayRoutesTest.java`, and `auth-service/src/test/java/com/cloudscale/auth/JwtTokenServiceTest.java`. |
| Frontend unit/component tests | 15 passed, 0 failed. | `npm test`; Chrome 157.0.0.0, 15 Jasmine specs. | `frontend/src/app/**/*.spec.ts`; command output from final run. |
| Frontend production build | Passed; 341.08 kB raw initial bundle, 86.77 kB estimated transfer. | `npm run build`; Angular production configuration. | Build output at `frontend/dist/cloudscale-frontend/`. |
| Frontend instrumented coverage | 40.04% lines (163/407), 39.66% statements (167/421), 31.55% branches (59/187), 46.55% functions (54/116). | `npm test -- --code-coverage`; same 15 tests. | `frontend/coverage/cloudscale-frontend/index.html` (ignored build output). |
| Frontend dependency audit | 0 vulnerabilities. | `npm ci` audited 486 packages; `npm audit`. | `frontend/package-lock.json`; command output. This is the npm dependency graph only. |
| Docker Compose structure | YAML parsed; 13 service definitions, shared JWT/database environment maps, Gateway service DNS, and 7 loopback-only publishers passed a Ruby structural check. | One local Ruby YAML parse and invariant check; no daemon required. | `docker-compose.yml`; exact check below. |

Backend test cases by service:

| Module | Tests | Passed | Skipped |
| --- | ---: | ---: | ---: |
| Gateway | 8 | 8 | 0 |
| Auth | 10 | 8 | 2 |
| Configuration | 18 | 12 | 6 |
| Rollout | 26 | 21 | 5 |
| Evaluation | 25 | 24 | 1 |
| Audit | 15 | 12 | 3 |
| **Total** | **102** | **85** | **17** |

The Maven output includes non-failing Mockito dynamic-agent warnings under Java 21. The Docker skips are explicit Testcontainers skips, not passed integration runs.

The static Compose check can be reproduced without Docker:

```bash
ruby - <<'RUBY'
require "yaml"
config = YAML.load_file("docker-compose.yml")
services = config.fetch("services")
expected = %w[postgres redis kafka auth-service config-service rollout-service evaluation-service audit-service gateway frontend prometheus grafana]
abort "missing service" unless (expected - services.keys).empty?
auth_env = services.fetch("auth-service").fetch("environment")
abort "missing shared credentials" unless %w[JWT_ISSUER JWT_SECRET POSTGRES_USER POSTGRES_PASSWORD].all? { |key| auth_env.key?(key) }
gateway_env = services.fetch("gateway").fetch("environment")
abort "missing Gateway upstream" unless %w[AUTH_SERVICE_BASE_URL CONFIG_SERVICE_BASE_URL ROLLOUT_SERVICE_BASE_URL EVALUATION_SERVICE_BASE_URL AUDIT_SERVICE_BASE_URL].all? { |key| gateway_env.key?(key) }
published = services.select { |_, service| service.key?("ports") }
expected_publishers = %w[postgres redis kafka gateway frontend prometheus grafana]
abort "unexpected published service" unless (published.keys - expected_publishers).empty?
abort "missing expected publisher" unless (expected_publishers - published.keys).empty?
abort "non-loopback port" unless published.values.all? { |service| service.fetch("ports").all? { |port| port.start_with?("127.0.0.1:") } }
puts "Compose YAML and local-runtime invariants: OK (#{services.length} services; #{published.length} loopback-only publishers)"
RUBY
```

This checks YAML structure and selected invariants only; it does not replace Docker Compose’s interpolation validation.

`npm ci` also emitted npm 11 `allowScripts` notices for five transitive packages. No install scripts were manually approved; the subsequent production build and browser tests passed in this environment.

## Measured algorithm result

| Metric | Value | Method, sample, and conditions | Evidence |
| --- | --- | --- | --- |
| 50% rollout assignment | 5,023/10,000 assigned (50.23%); 0 repeat mismatches | Reproduced the Java algorithm’s SHA-256 over `userId + U+001F + featureUUID + U+001F + environmentUUID`, positive digest modulo 10,000, with stable IDs `customer-0`…`customer-9999`; repeated every calculation. This is algorithm-level, not an HTTP/service benchmark. | `rollout-service/src/main/java/com/cloudscale/rollout/domain/StableRolloutBucketer.java`; Java test `StableRolloutBucketerTest`; local Node `crypto` measurement. |

Reproduce the assignment calculation with Node’s built-in crypto:

```bash
node -e 'const {createHash}=require("node:crypto"); const feature="b2bb1375-a6d7-458d-a732-4ccab7a9df04", environment="065d8c23-5b9b-40f1-bfce-ef541f2387d2"; const bucket=id=>Number(BigInt("0x"+createHash("sha256").update(`${id}\x1f${feature}\x1f${environment}`).digest("hex"))%10000n); let assigned=0, mismatches=0; for(let i=0;i<10000;i++){const id=`customer-${i}`, first=bucket(id); if(first<5000) assigned++; if(bucket(id)!==first) mismatches++;} console.log(JSON.stringify({identities:10000,stagePercentage:50,assigned,assignedPercent:assigned/100,repeatMismatches:mismatches}));'
```

## Not measured

| Metric | Status and reason |
| --- | --- |
| Evaluation p50/p95/p99 latency | Not measured. No local backend runtime was available; Docker is unavailable. |
| Evaluation throughput / request error rate | Not measured. No live Gateway, database, Redis, or representative request load. |
| Redis hit ratio and configuration fetch rate | Not measured. Counters are implemented, but Redis/runtime counters were not observed. |
| Kafka publish-to-consume or cache-invalidation latency | Not measured. No live Kafka broker or consumer run. |
| Kafka processing/retry/error counts | Not measured at runtime. Counters exist; broker-dependent tests skip without Docker. |
| Database calls per evaluation | Not measured. Evaluation’s request/query count is not instrumented, and PostgreSQL was unavailable. |
| Cross-tenant denial against live, database-backed services | Not measured end-to-end. Request-level MockMvc checks exercise all four resource-server filter chains; Testcontainers-backed service HTTP tests require Docker. |
| Container health / Prometheus scrape state | Not measured. No Docker CLI/daemon; Compose startup and `/targets` cannot be checked here. |
| Remote GitHub Actions result | Not measured. CI workflow is present but was not pushed or run remotely. |

## Runtime and security limits

`docker compose config`, image builds, database migrations, Testcontainers integration, Gateway-to-service live routing, and Prometheus scraping remain unverified until Docker is installed and running. The persisted-outbox retry and completed-then-rollback assignment regressions are present as Testcontainers tests but were skipped locally. The frontend’s authenticated workspace was exercised with mocked HTTP responses; only the logged-out login screen was visually captured. The Rollout percentage is diagnostic and is not connected to Evaluation; health observations may pause a rollout, while rollback remains manual.

Backend coverage was generated without changing the Maven POM:

```bash
env MAVEN_SKIP_RC=1 \
  JAVA_HOME=/private/var/folders/t0/rlmgz1vd7jvf2097nywt16nr0000gn/T/opencode/cloudscale-jdk21/Contents/Home \
  PATH=/private/var/folders/t0/rlmgz1vd7jvf2097nywt16nr0000gn/T/opencode/cloudscale-jdk21/Contents/Home/bin:$PATH \
  ./mvnw -B org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent verify \
    org.jacoco:jacoco-maven-plugin:0.8.13:report
```
