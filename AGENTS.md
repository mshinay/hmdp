# AGENTS.md
Practical guidance for coding agents working in this repository.

## 1) Purpose and scope
- This file defines build, test, and style expectations for agentic edits.
- Follow existing project patterns first; prefer minimal, targeted changes.
- Apply these rules to all Java, resource, and test files under this repo.

## 2) Project snapshot
- Stack: Java 8, Spring Boot 2.3.12, MyBatis-Plus 3.4.3, Redis, MySQL.
- Build tool: Maven (`pom.xml` at repository root).
- App entry point: `src/main/java/com/hmdp/HmDianPingApplication.java`.
- Root package: `com.hmdp`.
- Standard API wrapper: `com.hmdp.dto.Result`.
- Global runtime exception handler: `com.hmdp.config.WebExceptionAdvice`.

## 3) Repository layout
- `src/main/java/com/hmdp/controller`: REST controllers and request mapping.
- `src/main/java/com/hmdp/service`: service interfaces (prefixed with `I`).
- `src/main/java/com/hmdp/service/impl`: service implementations.
- `src/main/java/com/hmdp/mapper`: MyBatis-Plus mapper interfaces.
- `src/main/java/com/hmdp/entity`: persistence entities.
- `src/main/java/com/hmdp/dto`: API-facing DTOs and view models.
- `src/main/java/com/hmdp/config`: config and cross-cutting concerns.
- `src/main/java/com/hmdp/utils`: utility classes and constants.
- `src/test/java/com/hmdp`: tests (currently minimal suite).

## 4) Local prerequisites
- JDK 8 required (`java -version` should report 1.8.x).
- Maven 3.6+ required as `mvn` (no Maven wrapper is present).
- MySQL and Redis should match settings in `src/main/resources/application.yaml`.
- Default service port is `8081` unless overridden.

## 5) Build, lint, and test commands
Run from repository root.

```bash
# compile only (fast baseline check)
mvn clean compile

# run all tests
mvn test

# run one test class (preferred fast iteration)
mvn -Dtest=HmDianPingApplicationTests test

# run one test method
mvn -Dtest=HmDianPingApplicationTests#methodName test

# run one test using fully qualified name (safe fallback)
mvn -Dtest=com.hmdp.HmDianPingApplicationTests test

# package jar (includes tests)
mvn clean package

# package jar without tests
mvn clean package -DskipTests

# start application locally
mvn spring-boot:run
```

## 6) Lint and quality gates
- No dedicated lint plugin is configured (no Checkstyle/PMD/Spotless in `pom.xml`).
- Treat successful compile and tests as the quality gate for agent changes.
- Preferred pre-handoff sequence:
  1) `mvn clean compile`
  2) `mvn -Dtest=<TouchedTestClass> test` when applicable
  3) `mvn test` before final handoff when feasible
- If external services are unavailable, report which command was blocked and why.

## 7) Coding style rules

### 7.1 Imports
- Prefer explicit imports over wildcard imports.
- Keep imports organized in stable groups:
  1) project (`com.hmdp...`)
  2) third-party (`org...`, `com.baomidou...`, `cn.hutool...`)
  3) JDK and `javax`
- Keep imports sorted inside each group.

### 7.2 Formatting
- Use 4-space indentation.
- Keep opening braces on the same line.
- Avoid unrelated reformatting in touched files.
- Preserve existing annotation and Lombok usage style.
- Keep methods focused and avoid unnecessary complexity.

### 7.3 Types and API boundaries
- Use `Long` for ids to stay consistent with entities/controllers.
- Use DTOs at API boundaries (`LoginFormDTO`, `UserDTO`, `Result`).
- Keep domain entities in service/mapper layers.
- Avoid raw types; always use generics.
- Favor clear, concrete return types over `Object`.

### 7.4 Naming conventions
- Classes: PascalCase (`UserServiceImpl`).
- Methods/fields: camelCase (`sendCode`, `userService`).
- Constants: UPPER_SNAKE_CASE (`LOGIN_CODE_TTL`).
- Service interfaces: `I*Service` pattern.
- Mappers: `*Mapper` suffix.
- Service implementations: `*ServiceImpl` suffix.

### 7.5 Spring layering
- Controllers: HTTP orchestration, parameter parsing, response shaping.
- Services: business logic and transactional behavior.
- Mappers/SQL: persistence and query logic.
- Utils/config: cross-cutting shared helpers, constants, and setup.
- Keep endpoint paths aligned with existing route conventions.

### 7.6 Error handling and logging
- Return business validation failures with `Result.fail(...)`.
- Let unexpected runtime exceptions bubble to `WebExceptionAdvice`.
- Use `@Slf4j` for operational logs with clear context.
- Log exceptions at handling boundaries with stack traces.
- Do not log secrets (passwords, tokens, credentials, private keys).

### 7.7 Persistence and Redis patterns
- Reuse MyBatis-Plus patterns: `ServiceImpl<Mapper, Entity>` and `BaseMapper<Entity>`.
- Put SQL-heavy logic in mapper/XML rather than controller/service glue.
- Reuse existing Redis key conventions from `RedisConstants`.
- Preserve TTL units and semantics already used in service implementations.

### 7.8 Testing expectations
- Place tests under `src/test/java` mirroring package structure.
- Name tests `*Test` or `*Tests`.
- Prefer focused service tests for new logic.
- For integration tests, document MySQL/Redis assumptions in test notes.
- During development, run the narrowest test first, then broader suites.

## 8) Configuration and secrets
- `application.yaml` contains local defaults; treat them as development-only values.
- Do not commit new secrets, tokens, or real credentials.
- Prefer environment-specific overrides outside source control.

## 9) Cursor/Copilot instruction files
Checked paths:
- `.cursorrules`
- `.cursor/rules/`
- `.github/copilot-instructions.md`

Current status:
- No Cursor or Copilot rule files were found in this repository.
- If any are added later, treat them as higher-priority instructions and update this file.

## 10) Agent workflow checklist
- Read `pom.xml` and touched package files before editing.
- Keep diffs minimal and scoped to the requested change.
- Do not refactor unrelated files while implementing task work.
- Prefer single-test execution during iteration; run broader checks before handoff.
- Report commands executed, outcomes, and any environment blockers.
