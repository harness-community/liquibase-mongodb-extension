# AGENTS.md - Mongosh Tools

## Purpose

This package contains **utility classes for executing mongosh (MongoDB Shell) commands**. These tools enable the Harness-enhanced mongosh native executor feature, allowing users to run native MongoDB shell scripts from Liquibase changesets.

This is a **critical package** for the Harness enhancements to the Liquibase MongoDB extension.

## Key Files

- **`MongoshRunner.java`** - Executes mongosh binary with sophisticated configuration and error handling
  - Extends Liquibase's `ExecuteShellCommandChange` framework
  - Handles mongosh binary detection and execution
  - Manages connection strings, timeouts, and output capture
  - Provides detailed error reporting
  - **5 commits in 6 months** - actively maintained

- **`MongoshFileCreator.java`** - Creates temporary mongosh script files for execution
  - Handles file I/O for mongosh scripts
  - Manages temporary file lifecycle
  - Supports loading scripts from resource paths
  - **2 commits in 6 months** - stable utility

## Testing

- **Run tests for this package**: `mvn test -Dtest=*Runner*,*Creator*`
- **Test files**: 
  - `MongoshRunnerTest.java` - Unit tests for runner
  - `MongoshFileCreatorTest.java` - Unit tests for file creator
- **Integration tests**: Mongosh execution is tested via `MongoLiquibaseIT.java`

## Dependencies

**Internal:**
- `liquibase.ext.mongodb.configuration.MongoConfiguration` - MongoDB configuration management
- `liquibase.ext.mongodb.database.MongoConnection` - Connection to MongoDB
- `liquibase.ext.mongodb.database.MongoLiquibaseDatabase` - Database implementation
- `liquibase.ext.mongodb.statement.MongoshStatement` - Statement for mongosh commands

**External:**
- Liquibase Core - `liquibase.change.core.ExecuteShellCommandChange`, `liquibase.resource.*`
- Java NIO - `java.nio.file.*` for file operations
- SLF4J - Logging framework

## Important Patterns

### MongoshRunner Usage Flow

1. **Binary Detection**: Checks for `mongosh` binary in PATH or configured location
2. **Script Preparation**: Creates temporary .js file with MongoDB commands
3. **Command Construction**: Builds mongosh command with connection string and options
4. **Execution**: Runs mongosh as shell command with timeout
5. **Output Capture**: Captures stdout/stderr for logging and error reporting
6. **Cleanup**: Removes temporary files

### Configuration

Mongosh execution respects these configurations:
- **Connection string**: From `MongoLiquibaseDatabase` connection
- **Timeout**: Default 120 seconds (configurable)
- **Working directory**: Temporary directory for script files
- **Binary path**: Auto-detected or configured via environment

### Error Handling

MongoshRunner provides detailed error reporting:
- Exit code checking
- stdout/stderr capture
- Timeout detection
- Binary not found errors
- Script syntax errors

## DOs

- Use `MongoshRunner` for all mongosh command execution
- Always set appropriate timeout values (default 120s may be too short for large scripts)
- Use `MongoshFileCreator` for creating temporary script files
- Clean up temporary files after execution
- Log mongosh output for debugging and audit trails
- Handle mongosh exit codes properly (non-zero = failure)
- Test mongosh scripts independently before embedding in changesets
- Use absolute paths for mongosh binary if auto-detection fails
- Provide meaningful error messages when mongosh execution fails

## DON'Ts

- Don't execute mongosh directly via `Runtime.exec()` - use `MongoshRunner`
- Don't leave temporary script files behind - ensure cleanup in finally blocks
- Don't ignore exit codes from mongosh execution
- Don't hardcode mongosh binary paths - support auto-detection
- Don't execute untrusted mongosh scripts without validation
- Don't expose MongoDB credentials in logs or error messages
- Don't modify `MongoshRunner` without updating tests
- Don't assume mongosh is installed - provide clear error if missing

## Configuration Options

### Environment Variables
- `MONGOSH_PATH` - Custom path to mongosh binary
- `LIQUIBASE_MONGOSH_TIMEOUT` - Override default timeout (milliseconds)

### MongoConfiguration Properties
- `mongoshPath` - Configured mongosh binary location
- `timeout` - Command execution timeout

## Troubleshooting

### Common Issues

**"mongosh binary not found"**
- Install mongosh: https://www.mongodb.com/docs/mongodb-shell/install/
- Or set `MONGOSH_PATH` environment variable

**"Command timed out"**
- Increase timeout via configuration
- Check if mongosh command hangs on stdin
- Verify MongoDB connection is reachable

**"Script failed with exit code X"**
- Check mongosh stderr output in logs
- Validate JavaScript syntax
- Test script manually: `mongosh <connection-string> --file script.js`

## Recent Development Focus

This package is part of the **Harness mongosh native executor enhancement**:
- **5 commits to MongoshRunner** - Improved execution and error handling
- **2 commits to MongoshFileCreator** - File handling improvements
- Focus on reliability and error reporting for production use

## Integration with Change Types

These tools are used by:
- `MongoshChange` - Inline mongosh commands
- `MongoshFileChange` - Mongosh commands from external files
- `MongoshExecutor` - Executor that orchestrates mongosh execution

## Future Considerations

- Support for mongosh --eval for inline commands (avoid file creation)
- Connection pooling for multiple mongosh executions
- Async execution for long-running scripts
- Better progress reporting for large scripts
