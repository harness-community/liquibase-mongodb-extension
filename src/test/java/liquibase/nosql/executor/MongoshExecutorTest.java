package liquibase.nosql.executor;

import com.mongodb.ConnectionString;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.exception.ValidationErrors;
import liquibase.ext.mongodb.change.CreateCollectionChange;
import liquibase.ext.mongodb.change.MongoshChange;
import liquibase.ext.mongodb.database.MongoConnection;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.statement.MongoshStatement;
import liquibase.ext.mongodb.tools.MongoshRunner;
import liquibase.sql.visitor.SqlVisitor;
import liquibase.statement.SqlStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoshExecutorTest {

    private MongoshExecutor executor;
    private MongoLiquibaseDatabase database;
    private ChangeSet changeSet;

    @BeforeEach
    void setUp() {
        executor = new MongoshExecutor();
        database = mock(MongoLiquibaseDatabase.class);
        changeSet = mock(ChangeSet.class);
        executor.setDatabase(database);
    }

    @Test
    void getName_shouldReturnMongosh() {
        assertThat(executor.getName()).isEqualTo("mongosh");
    }

    @Test
    void supports_withMongoLiquibaseDatabase_shouldReturnTrue() {
        final Database mongoDatabase = new MongoLiquibaseDatabase();

        assertThat(executor.supports(mongoDatabase)).isTrue();
    }

    @Test
    void validate_withMongoshChange_shouldReturnNoErrors() {
        final MongoshChange mongoshChange = new MongoshChange();
        when(changeSet.getChanges()).thenReturn(Arrays.asList(mongoshChange));

        final ValidationErrors errors = executor.validate(changeSet);

        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void validate_withOidcConnection_shouldReturnValidationError() {
        final MongoConnection connection = new MongoConnection();
        connection.setConnectionString(new ConnectionString(
                "mongodb://localhost:27017/test_db?authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT:k8s"));
        when(database.getConnection()).thenReturn(connection);
        when(changeSet.getChanges()).thenReturn(Arrays.asList(new MongoshChange()));

        final ValidationErrors errors = executor.validate(changeSet);

        assertThat(errors.getErrorMessages()).containsExactly(MongoshRunner.MSG_MONGOSH_UNSUPPORTED_WITH_OIDC);
    }

    @Test
    void validate_withNonOidcConnection_shouldReturnNoErrors() {
        final MongoConnection connection = new MongoConnection();
        connection.setConnectionString(new ConnectionString("mongodb://user1:password1@localhost:27017/test_db"));
        when(database.getConnection()).thenReturn(connection);
        when(changeSet.getChanges()).thenReturn(Arrays.asList(new MongoshChange()));

        assertThat(executor.validate(changeSet).hasErrors()).isFalse();
    }

    @Test
    void validate_withUnsupportedChange_shouldReturnValidationError() {
        final CreateCollectionChange unsupportedChange = new CreateCollectionChange();
        when(changeSet.getChanges()).thenReturn(Arrays.asList(unsupportedChange));
        when(changeSet.getId()).thenReturn("test-changeset");
        when(changeSet.getAuthor()).thenReturn("test-author");

        final ValidationErrors errors = executor.validate(changeSet);

        assertThat(errors.hasErrors()).isTrue();
        assertThat(errors.getErrorMessages())
                .anyMatch(msg -> msg.contains("unsupported change type")
                        && msg.contains("CreateCollectionChange")
                        && msg.contains("runWith='mongosh'"));
    }

    @Test
    void execute_withNonMongoshStatement_shouldThrowException() {
        final SqlStatement nonMongoshStatement = mock(SqlStatement.class);
        final List<SqlVisitor> visitors = new ArrayList<>();

        executor.validate(changeSet);

        assertThatThrownBy(() -> executor.execute(nonMongoshStatement, visitors))
                .isInstanceOf(DatabaseException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void execute_withValidMongoshStatement_shouldAttemptExecution() {
        final MongoshStatement statement = new MongoshStatement("db.test.find()");
        final List<SqlVisitor> visitors = new ArrayList<>();

        when(changeSet.getId()).thenReturn("test-changeset");
        when(changeSet.getAuthor()).thenReturn("test-author");

        executor.validate(changeSet);

        assertThatThrownBy(() -> executor.execute(statement, visitors))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("mongosh");
    }

    @Test
    void queryForLong_shouldThrowDatabaseException() {
        final SqlStatement statement = mock(SqlStatement.class);

        assertThatThrownBy(() -> executor.queryForLong(statement))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Query operations are not supported by mongosh executor");
    }

    @Test
    void update_shouldThrowDatabaseException() {
        final SqlStatement statement = mock(SqlStatement.class);

        assertThatThrownBy(() -> executor.update(statement))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Update operations are not supported by mongosh executor");
    }

    @Test
    void updatesDatabase_shouldReturnTrue() {
        assertThat(executor.updatesDatabase()).isTrue();
    }
}
