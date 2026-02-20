# AGENTS.md
Guidance for coding agents working in this repository.

## 1) Project overview
- Stack: Java 8, Spring Boot 2.3.12, MyBatis-Plus 3.4.3, Redis, MySQL.
- Build tool: Maven (`pom.xml` at repository root).
- App entry point: `src/main/java/com/hmdp/HmDianPingApplication.java`.
- Package root: `com.hmdp`.
- API response wrapper: `com.hmdp.dto.Result`.
- Global exception advice: `com.hmdp.config.WebExceptionAdvice`.

## 2) Local prerequisites
- JDK 8 (`java -version` should show 1.8.x).
- Maven 3.6+ available as `mvn` (no Maven wrapper in repo).
- MySQL reachable per `src/main/resources/application.yaml`.
- Redis reachable per `src/main/resources/application.yaml`.
- Default HTTP port is `8081`.

## 3) Build / run / test commands
Run commands from repository root.

```bash
# compile only
mvn clean compile

# run all tests
mvn test

# run one test class (fastest normal iteration)
mvn -Dtest=HmDianPingApplicationTests test

# run one test method
mvn -Dtest=HmDianPingApplicationTests#methodName test

# package jar (runs tests)
mvn clean package

# package jar without tests
mvn clean package -DskipTests

# run app
mvn spring-boot:run
```

Notes:
- If needed, use fully qualified test name: `-Dtest=com.hmdp.HmDianPingApplicationTests`.
- Current test suite is minimal (`src/test/java/com/hmdp/HmDianPingApplicationTests.java`).
- Integration behavior can depend on MySQL/Redis availability.

## 4) Lint and quality gates
- No explicit lint plugin is configured in `pom.xml` (no Checkstyle/Spotless/PMD).
- Use compile/tests/package as quality gates:

```bash
mvn clean compile
mvn test
mvn clean package
```

- Treat successful compile + tests as the baseline "lint" signal.
- Keep formatting aligned with nearby files; do not introduce new formatters unless asked.

## 5) Repository structure conventions
- `controller/`: REST endpoints and request mapping.
- `service/`: service interfaces, conventionally prefixed with `I`.
- `service/impl/`: concrete services (`*ServiceImpl`).
- `mapper/`: MyBatis-Plus mapper interfaces (`*Mapper`).
- `entity/`: persistence/domain entities.
- `dto/`: API DTOs (`Result`, login payloads, view objects).
- `config/`: Spring configuration and cross-cutting concerns.
- `utils/`: stateless utility classes and shared constants.

## 6) Code style guidelines

### 6.1 Imports
- Prefer explicit imports.
- Avoid wildcard imports unless the file already uses them and consistency matters.
- Group imports in this order:
  1) project (`com.hmdp...`)
  2) third-party (`org...`, `com.baomidou...`, etc.)
  3) JDK/`javax`
- Keep groups stable and sorted.

### 6.2 Formatting
- 4 spaces for indentation.
- Opening braces on the same line as declarations.
- Keep methods focused and reasonably small.
- Preserve existing annotation style (`@RestController`, `@Service`, etc.).
- Avoid unrelated reformatting in touched files.

### 6.3 Types and API boundaries
- Use concrete domain types (`User`, `Shop`, etc.) in mapper/service layers.
- Use DTOs at API boundaries (`LoginFormDTO`, `UserDTO`, `Result`).
- Controllers should return `Result` consistently.
- Use `Long` for IDs (matches existing entities/controllers).
- Avoid raw types; use generics (`List<?>`, `BaseMapper<Shop>`, etc.).

### 6.4 Naming conventions
- Class names: PascalCase (`ShopServiceImpl`).
- Methods/fields: camelCase (`sendCode`, `userService`).
- Constants: UPPER_SNAKE_CASE (`RedisConstants`, `SystemConstants`).
- Service interfaces keep `I` prefix (`IUserService`, `IShopService`).
- Mapper interfaces end with `Mapper`.
- Service implementations end with `ServiceImpl`.

### 6.5 Spring layering rules
- Controllers handle HTTP orchestration only.
- Business logic belongs in services.
- Persistence logic stays in mappers/mapper XML.
- Shared cross-cutting logic belongs in `config` or `utils`.
- Keep routes coherent with existing prefixes (for example `/user`, `/shop`).

### 6.6 Error handling and logging
- Return business errors via `Result.fail(...)`.
- Let unexpected runtime exceptions bubble to `WebExceptionAdvice` when appropriate.
- Use `@Slf4j` and include contextual information in logs.
- Log stack traces at handling boundaries.
- Never log secrets (passwords, tokens, connection strings, private keys).

### 6.7 MyBatis-Plus and persistence
- Reuse `ServiceImpl<Mapper, Entity>` + `BaseMapper<Entity>` patterns.
- Put SQL-specific behavior in mapper layer / XML when needed.
- Reuse configured pagination support in `MybatisConfig`.
- Keep entity/schema alignment with `src/main/resources/db/hmdp.sql`.

### 6.8 Testing expectations
- Put tests under `src/test/java` with mirrored package structure.
- Use class names ending in `Test` or `Tests`.
- Prefer focused service-level unit tests for new logic.
- For integration tests, document MySQL/Redis requirements.
- During development, run single-test command first; run full suite before handoff.

## 7) Configuration and secrets
- `application.yaml` includes local-development credentials; treat as defaults only.
- Do not commit new secrets or real credentials.
- Prefer environment overrides for non-local deployments.

## 8) Cursor and Copilot rule files
Checked paths:
- `.cursorrules`
- `.cursor/rules/`
- `.github/copilot-instructions.md`

Status at time of writing:
- No Cursor or Copilot rule files were found.
- If these files are later added, treat them as higher-priority instructions and update this document.

## 9) Agent workflow checklist
- Read `pom.xml` and target package files before major edits.
- Keep changes minimal and aligned with surrounding patterns.
- Run `mvn -Dtest=... test` for touched logic when possible.
- Run at least `mvn clean compile` before handoff; prefer `mvn test`.
- Report commands run and notable outcomes in final handoff.
