# AGENTS.md - Liquibase MongoDB Extension

## Project Overview

**Harness Liquibase MongoDB Extension** - A Harness-enhanced fork of the Liquibase MongoDB Extension with additional features including Mongo Native Executor for executing native MongoDB operations.

This extension provides enterprise-grade database change management for MongoDB deployments through Liquibase's changeset framework, calling specific `mongo-java-driver` methods.

**Key Features:**
- Mongo Native Executor (`mongo` and `mongoFile` changeTypes)
- MongoDB change types (createIndex, dropIndex, insertOne, insertMany, etc.)
- MongoDB preconditions (documentExists, collectionExists, mongoIndexExists)
- Integration with Liquibase 4.24.0

**Repository:** https://git0.harness.io/l7B_kbSEQD2wjrM7PShm5w/default/CD/liquibase-mongodb-extension.git

## Build System

- **Build tool**: Maven 3.x
- **Java version**: 1.8
- **Clean and build**: `mvn clean install`
- **Build without tests**: `mvn clean install -DskipTests`
- **Package JAR**: `mvn package`

## Testing

- **Run all unit tests**: `mvn test`
- **Run all integration tests**: `mvn verify` or `mvn failsafe:integration-test`
- **Run specific test class**: `mvn test -Dtest=ClassName`
- **Run specific test method**: `mvn test -Dtest=ClassName#methodName`
- **Test file patterns**: 
  - Unit tests: `*Test.java`
  - Integration tests: `*IT.java`
  - Groovy tests: `*.groovy` in `src/test/groovy/`

**Test frameworks:**
- JUnit 5 (Jupiter)
- Mockito for mocking
- AssertJ for assertions
- Hamcrest for matchers

## Code Quality & Security

- **Git hooks**: Pre-commit and pre-push hooks enabled
  - **Pre-commit**: Git-leaks security scan (checks for secrets/credentials)
  - **Pre-push**: Additional validation
- **Code coverage**: JaCoCo plugin enabled (`mvn jacoco:report`)
- **Auto-run after agent completes**: Git hooks will automatically run on commit/push

## Git Workflow

- **Default branch**: `main`
- **Branch naming**: `feat/DBOPS-123-short-description` or `fix/DBOPS-456-short-description`
  - Types: `feat`, `fix`, `build`, `chore`, `refactor`, `test`, `docs`
- **Commit format**: `<type>: [DBOPS-TICKET]: <description>`
  - Example: `feat: [DBOPS-2368]: mongoIndexExists preCondition`
  - Example: `fix: [DBOPS-1840]: selective property serialisation for mongo native executor`
- **PR title format**: Same as commit format
- **Jira project**: DBOPS

## DOs

- Always run tests before committing (`mvn test`)
- Follow existing code patterns and conventions in the codebase
- Use descriptive commit messages with Jira ticket references
- Add unit tests for all new change types and preconditions
- Update XSD schema files when adding new change types or attributes
- Add integration tests for database operations
- Use Lombok annotations (@Getter, @Setter, etc.) for boilerplate code
- Update README.md when adding new features or changeTypes
- Ensure git-leaks pre-commit hook passes (never commit secrets)
- Follow Liquibase extension conventions for naming and structure

## DON'Ts

- Never force push to main branch
- Never commit secrets, .env files, credentials, or API keys
- Never run destructive commands without confirmation
- Never skip git hooks (--no-verify) - they protect against security issues
- Don't modify core Liquibase interfaces without thorough testing
- Don't add new dependencies without considering compatibility with Liquibase 4.24.0
- Don't skip integration tests when modifying database operations
- Don't change MongoDB driver version without testing compatibility

## Commands to Never Run

- `git push --force origin main`
- `git push --force origin master`
- `git commit --no-verify` (skips git-leaks security scan)
- `git push --no-verify` (skips validation)
- `rm -rf target/` is OK, but never `rm -rf /` or `rm -rf .` or `rm -rf *`

## Project Structure

```
liquibase-mongodb-extension/
├── src/
│   ├── main/
│   │   ├── java/liquibase/
│   │   │   ├── ext/mongodb/
│   │   │   │   ├── change/           # MongoDB change types (createIndex, insertOne, etc.)
│   │   │   │   ├── changelog/        # Changelog parsing
│   │   │   │   ├── command/          # Execute-native command implementation
│   │   │   │   ├── configuration/    # MongoDB configuration
│   │   │   │   ├── database/         # MongoDB connection, driver, and database impl
│   │   │   │   ├── lockservice/      # Distributed locking
│   │   │   │   ├── precondition/     # MongoDB preconditions
│   │   │   │   ├── statement/        # MongoDB statements
│   │   │   │   └── tools/            # Mongosh runner and file creator
│   │   │   └── nosql/
│   │   │       └── executor/         # Mongosh executor and generator
│   │   └── resources/
│   │       ├── META-INF/services/    # Liquibase service provider interfaces
│   │       ├── liquibase/            # i18n properties
│   │       └── www.liquibase.org/    # XSD schema files
│   └── test/
│       ├── java/liquibase/ext/       # Unit and integration tests
│       ├── groovy/liquibase/ext/     # Groovy-based tests
│       └── resources/                # Test resources and Docker configs
├── pom.xml                           # Maven build configuration
├── README.md                         # Project documentation
└── AGENTS.md                         # This file
```

## Important Packages/Folders

Based on recent commit activity, these are the most actively modified areas:

1. **src/main/java/liquibase/ext/mongodb/change/** - MongoDB change type implementations
   - `MongoshChange.java` - Execute inline mongosh commands
   - `MongoshFileChange.java` - Execute mongosh commands from file
   - Core change types: CreateIndexChange, DropIndexChange, InsertOneChange, InsertManyChange

2. **src/main/java/liquibase/nosql/executor/** - Mongosh executor infrastructure
   - `MongoshExecutor.java` - Executor for mongosh commands
   - `MongoshGenerator.java` - SQL to mongosh command generator

3. **src/main/java/liquibase/ext/mongodb/tools/** - Supporting utilities
   - `MongoshRunner.java` - Runs mongosh shell commands
   - `MongoshFileCreator.java` - Creates temporary mongosh script files

4. **src/main/java/liquibase/ext/mongodb/precondition/** - MongoDB preconditions
   - `MongoIndexExistsPrecondition.java` - Check if index exists

5. **src/main/java/liquibase/ext/mongodb/configuration/** - Configuration management
   - `MongoConfiguration.java` - MongoDB-specific configuration

6. **src/main/java/liquibase/ext/mongodb/command/** - Command execution
   - Execute-native command step implementation

7. **src/main/resources/** - Service providers and schemas
   - META-INF/services - Liquibase SPI registrations
   - XSD schema files for XML validation

## Language-Specific Guidelines

This is a **Java** project. Use the Java conventions skill for detailed guidance:
- **Skill**: `make-agent-friendly:java-conventions`
- **Java version**: 1.8 (legacy compatibility)
- **Build tool**: Maven
- **Testing**: JUnit 5 + Mockito + AssertJ

## Maven Profiles & Properties

Key properties defined in pom.xml:
- `mongodb-driver.version`: 4.10.2 (MongoDB Java Driver)
- `liquibase.version`: 4.24.0
- `jupiter.version`: 5.10.0 (JUnit 5)
- `mockito-core.version`: 4.11.0
- `lombok.version`: 1.18.30

## Integration with Liquibase

This extension integrates with Liquibase through:
- **Service Provider Interface (SPI)**: META-INF/services/ files register custom implementations
- **Change types**: Custom change classes extending AbstractMongoChange
- **Preconditions**: Custom precondition classes
- **Database implementation**: MongoLiquibaseDatabase
- **Executor**: MongoshExecutor for command execution

## External Dependencies

**Key runtime dependencies:**
- Liquibase Core 4.24.0
- MongoDB Java Driver (Sync) 4.10.2
- Jackson Databind (JSON processing)
- Lombok (compile-time annotation processing)

**Key test dependencies:**
- JUnit Jupiter 5.10.0
- Mockito 4.11.0
- AssertJ 3.24.2
- Liquibase Test Harness 1.0.9

## Helpful Resources

- **Harness Developer Docs**: https://developer.harness.io/docs/database-devops/concepts/database-devops/concepts/mongodb-command
- **Maven Repository**: https://console.cloud.google.com/artifacts/maven/gar-prod-setup/us/harness-maven-public/io.harness:liquibase-mongodb-dbops-extension
- **Upstream Project**: https://github.com/liquibase/liquibase-mongodb
