package liquibase.ext.mongodb.change;

import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.SetupException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.exception.ValidationErrors;
import liquibase.ext.mongodb.statement.MongoshStatement;
import liquibase.ext.mongodb.tools.MongoshRunner;
import liquibase.statement.SqlStatement;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoshFileChangeTest extends AbstractMongoChangeTest {

    private MongoshFileChange mongoshFileChange;

    @BeforeEach
    void setUp() {
        super.setUp();
        mongoshFileChange = new MongoshFileChange();
    }

    @Test
    void finishInitialization_withNullPath_shouldThrowSetupException() {
        mongoshFileChange.setPath(null);

        assertThatThrownBy(() -> mongoshFileChange.finishInitialization())
                .isInstanceOf(SetupException.class)
                .hasMessageContaining("'path' is required for mongoFile changes");
    }

    @Test
    void validate_withOidcConnection_shouldReturnValidationError() {
        mongoshFileChange.setPath("scripts/test.js");
        connectWith("mongodb://localhost:27017/test_db?authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT:k8s");

        assertThat(mongoshFileChange.validate(database).getErrorMessages())
                .containsExactly(MongoshRunner.MSG_MONGOSH_UNSUPPORTED_WITH_OIDC);
    }

    @Test
    void validate_withNonOidcConnection_shouldReturnNoErrors() {
        mongoshFileChange.setPath("scripts/test.js");
        connectWith("mongodb://user1:password1@localhost:27017/test_db");

        assertThat(mongoshFileChange.validate(database).hasErrors()).isFalse();
    }

    @Test
    void validate_withEmptyPath_shouldReturnValidationError() {
        mongoshFileChange.setPath("   ");

        final ValidationErrors errors = mongoshFileChange.validate(database);

        assertThat(errors.hasErrors()).isTrue();
        assertThat(errors.getErrorMessages())
                .anyMatch(msg -> msg.contains("'path' is required for mongoFile changes"));
    }

    @Test
    void getSql_withCachedContent_shouldReturnCachedValue() {
        final String cachedContent = "db.test.find();";
        mongoshFileChange.setSql(cachedContent);

        assertThat(mongoshFileChange.getSql()).isEqualTo(cachedContent);
    }

    @Test
    void generateStatements_withValidSql_shouldReturnMongoshStatement() {
        final String jsContent = "db.collection.insertOne({test: true});";
        mongoshFileChange.setSql(jsContent);

        final SqlStatement[] statements = mongoshFileChange.generateStatements(database);

        assertThat(statements).hasSize(1);
        assertThat(statements[0]).isInstanceOf(MongoshStatement.class);

        final MongoshStatement mongoshStatement = (MongoshStatement) statements[0];
        assertThat(mongoshStatement.getJavaScript()).isEqualTo(jsContent);
    }

    @Test
    void getSql_withInvalidPath_shouldThrowException() {
        mongoshFileChange.setPath("nonexistent-path-that-does-not-exist.js");

        assertThatThrownBy(() -> mongoshFileChange.getSql())
                .isInstanceOf(UnexpectedLiquibaseException.class)
                .hasMessageContaining("Error reading mongosh script file");
    }

    @Test
    @SneakyThrows
    void openNoSqlStream_withRelativeToChangelogFile_shouldAttemptRelativeResolution() {
        mongoshFileChange.setPath("relative-script.js");
        mongoshFileChange.setRelativeToChangelogFile(true);

        ChangeSet mockChangeSet = mock(ChangeSet.class);
        DatabaseChangeLog mockChangeLog = mock(DatabaseChangeLog.class);
        mongoshFileChange.setChangeSet(mockChangeSet);

        when(mockChangeSet.getChangeLog()).thenReturn(mockChangeLog);
        when(mockChangeLog.getPhysicalFilePath()).thenReturn("changelog/main.xml");

        assertThatThrownBy(() -> mongoshFileChange.openNoSqlStream())
                .isInstanceOf(UnexpectedLiquibaseException.class)
                .hasMessageContaining("Resource does not exist");
    }
}
