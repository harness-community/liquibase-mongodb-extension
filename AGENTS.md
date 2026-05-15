# AGENTS.md - Liquibase MongoDB Extension

## Project Overview

This is the Liquibase MongoDB Extension, a Harness-enhanced fork that provides MongoDB support for Liquibase database migrations. The extension allows executing MongoDB operations through Liquibase changesets and includes Harness-specific enhancements:

- **Mongosh-backed executor** for inline and file-based shell scripts (`mongo` and `mongoFile` changes)
- **executeNative command** for arbitrary MongoDB JSON commands
- **mongoIndexExists precondition** for safer index management

The project extends Liquibase's NoSQL support with MongoDB-specific implementations.

**Repository**: liquibase/liquibase-mongodb  
**Maven Artifact**: `org.liquibase.ext:liquibase-mongodb`  
**Current Version**: 4.33.0.1-SNAPSHOT

## Build System

- **Build tool**: Maven
- **Build project**: `mvn clean install`
- **Build without tests**: `mvn clean install -DskipTests`
- **Package JAR**: `mvn package`
- **Install to local repo**: `mvn install`

## Testing

- **Run all tests**: `mvn test`
- **Run integration tests**: `mvn test -Prun-its`
- **Run specific test class**: `mvn test -Dtest=ClassName`
- **Run single test method**: `mvn test -Dtest=ClassName#methodName`
- **Test file pattern**: `*Test.java` in `src/test/java/`

**MongoDB Connection**: Tests require a MongoDB instance. Connection string is configured in `src/test/resources/liquibase.properties`:
```
url=mongodb://localhost:27017/test_db?socketTimeoutMS=100&connectTimeoutMS=100&serverSelectionTimeoutMS=100
```

**Important**: Adjust the connection string in `liquibase.properties` before running tests if using a different MongoDB instance.

## Linting & Formatting

- **Check style**: Maven parent POM may include Checkstyle or Spotless (check `pom.xml` for plugins)
- **No automatic formatting detected**: No pre-commit hooks found
- **Manual review**: Code style follows standard Java conventions

## Git Workflow

- **Branch naming**: `feature/DBOPS-123-short-description` or `fix/DBOPS-456-short-description`
- **Commit format**: `<type>: [DBOPS-XXX]: <description>`
  - Types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`
  - Example: `feat: [DBOPS-1941]: Add mongoIndexExists precondition`
  - Example: `fix: [DBOPS-2105]: Handle null connection string in getVisibleUrl`
- **PR title format**: Same as commit format
- **Default branch**: `main`

## DOs

- Always run tests before committing (`mvn test`)
- Follow existing code patterns in the codebase
- Use descriptive commit messages with JIRA ticket references (DBOPS-XXX)
- Add unit tests for all new functionality
- Update relevant documentation when adding new features or changes
- Keep changes focused and atomic per commit
- Ensure MongoDB connection is available before running integration tests
- Use the `run-its` Maven profile when running integration tests

## DON'Ts

- Never force push to main branch
- Never commit secrets, credentials, or connection strings with real passwords
- Never skip tests when making functional changes
- Never commit IDE-specific files (.idea/, *.iml, *.iws, .project, .classpath)
- Never modify the Maven parent POM reference without team approval
- Never change the Liquibase core version without verifying compatibility

## Commands to Never Run

- `git push --force origin main`
- `git push --force origin master`
- `git commit --no-verify` (never skip hooks, even if none currently configured)
- `git push --no-verify` (never skip hooks)
- `rm -rf /` or any destructive recursive delete
- `DROP DATABASE` or `DROP TABLE` commands on production databases

## Project Structure

```
liquibase-mongodb/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── liquibase/
│   │   │   │   ├── ext/mongodb/          # MongoDB-specific Liquibase extensions
│   │   │   │   │   ├── change/           # MongoDB change types (createCollection, insertOne, etc.)
│   │   │   │   │   ├── changelog/        # Changelog parsing and handling
│   │   │   │   │   ├── command/          # Custom commands (executeNative, etc.)
│   │   │   │   │   ├── configuration/    # MongoDB connection configuration
│   │   │   │   │   ├── database/         # MongoDB database implementation
│   │   │   │   │   ├── lockservice/      # Database lock service for MongoDB
│   │   │   │   │   ├── precondition/     # MongoDB preconditions (mongoIndexExists, etc.)
│   │   │   │   │   ├── statement/        # MongoDB statement implementations
│   │   │   │   │   └── tools/            # Utility tools and helpers
│   │   │   │   └── nosql/                # Generic NoSQL infrastructure
│   │   │   │       ├── changelog/        # NoSQL changelog tracking
│   │   │   │       ├── database/         # NoSQL database abstraction
│   │   │   │       ├── executor/         # NoSQL executors (including MongoshExecutor)
│   │   │   │       ├── lockservice/      # NoSQL lock service
│   │   │   │       ├── parser/           # NoSQL changelog parsers
│   │   │   │       ├── snapshot/         # NoSQL snapshot generation
│   │   │   │       └── statement/        # NoSQL statement abstractions
│   │   └── resources/
│   │       ├── META-INF/services/        # Java SPI service definitions
│   │       ├── liquibase/i18n/           # Internationalization messages
│   │       └── www.liquibase.org/xml/ns/mongodb/  # XSD schemas for MongoDB changes
│   └── test/
│       ├── java/                         # Test classes (mirrors main structure)
│       └── resources/                    # Test resources (changelog XMLs, liquibase.properties)
├── pom.xml                               # Maven project configuration
├── README.md                             # Project documentation
└── changelog.txt                         # Release notes and changelog
```

## Important Packages/Folders

Based on the project structure and purpose:

### Core Extension Logic
- **`src/main/java/liquibase/ext/mongodb/`** - Primary MongoDB extension implementation
  - `change/` - MongoDB change types (70+ change implementations)
  - `database/` - MongoDB database connection and metadata
  - `statement/` - MongoDB operation statements

### Harness Enhancements
- **`src/main/java/liquibase/ext/mongodb/command/`** - Custom commands (executeNative, etc.)
- **`src/main/java/liquibase/nosql/executor/`** - Mongosh executor for shell script execution
- **`src/main/java/liquibase/ext/mongodb/precondition/`** - Custom preconditions (mongoIndexExists)

### Infrastructure
- **`src/main/java/liquibase/nosql/`** - Generic NoSQL support layer that MongoDB extends
- **`src/main/resources/META-INF/services/`** - Java SPI registrations for Liquibase discovery

### Configuration & Schemas
- **`src/main/resources/www.liquibase.org/xml/ns/mongodb/`** - XSD schemas for MongoDB changelog validation
- **`src/test/resources/`** - Test changelogs and configuration

## Language-Specific Guidelines

This is a **Java** project. Use the `maf:java-conventions` skill for Java-specific patterns and best practices.

**Key Java Patterns in this project:**
- Service Provider Interface (SPI) pattern for Liquibase extension discovery
- Builder patterns for change and statement construction
- MongoDB Java Driver 5.x API usage
- JUnit 5 for testing with Mockito for mocking

## Maven Profiles

- **`run-its`** - Enables integration tests (requires running MongoDB instance)

## Dependencies

**Core:**
- Liquibase Core: 4.33.0
- MongoDB Java Driver: 5.5.1
- Jackson Core: 2.15.3

**Test:**
- JUnit Jupiter (JUnit 5)
- Mockito 4.x
- Liquibase Test Harness: 1.0.10

## Useful Commands

```bash
# Clean build
mvn clean install

# Run unit tests only
mvn test

# Run integration tests (requires MongoDB)
mvn test -Prun-its

# Skip tests during build
mvn clean install -DskipTests

# Run specific test
mvn test -Dtest=MongoLiquibaseIT

# Package without running tests
mvn package -DskipTests

# Verify build and run all checks
mvn verify
```

## Additional Resources

- **Harness MongoDB Docs**: https://developer.harness.io/docs/database-devops/concepts/database-devops/concepts/mongodb-command
- **Liquibase Documentation**: https://docs.liquibase.com/
- **MongoDB Java Driver Docs**: https://mongodb.github.io/mongo-java-driver/
