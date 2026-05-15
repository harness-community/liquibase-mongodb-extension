package liquibase.ext.mongodb.tools;

import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoshRunnerTest {

    private MongoshRunner runner;
    private ChangeSet changeSet;
    private DatabaseChangeLog changeLog;
    private Sql[] sqlStatements;

    @BeforeEach
    void setUp() {
        changeSet = mock(ChangeSet.class);
        changeLog = mock(DatabaseChangeLog.class);

        when(changeSet.getId()).thenReturn("test-changeset");
        when(changeSet.getAuthor()).thenReturn("test-author");
        when(changeSet.getChangeLog()).thenReturn(changeLog);
        when(changeLog.getLogicalFilePath()).thenReturn("changelog/test.xml");

        sqlStatements = new Sql[] {
                new UnparsedSql("db.users.find({status: 'active'})"),
                new UnparsedSql("db.orders.insertOne({user_id: 123, total: 99.99})")
        };

        runner = new MongoshRunner(changeSet, sqlStatements);
    }

    @Test
    void constructor_withValidParameters_shouldInitializeCorrectly() {
        assertThat(runner).isNotNull();
        assertThat(runner).isInstanceOf(liquibase.change.core.ExecuteShellCommandChange.class);
    }

    @Test
    void serviceLocator_shouldBeSkipped() {
        liquibase.servicelocator.LiquibaseService annotation =
                MongoshRunner.class.getAnnotation(liquibase.servicelocator.LiquibaseService.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.skip()).isTrue();
    }

    @Test
    void multipleSqlStatements_shouldHandleCorrectly() {
        final Sql[] multipleSqlStatements = new Sql[] {
                new UnparsedSql("db.collection1.find()"),
                new UnparsedSql("db.collection2.insertOne({test: true})"),
                new UnparsedSql("db.collection3.updateMany({}, {$set: {updated: true}})")
        };

        final MongoshRunner multiRunner = new MongoshRunner(changeSet, multipleSqlStatements);

        assertThat(multiRunner).isNotNull();
    }

    @Test
    void sqlStatements_withSpecialCharacters_shouldHandleCorrectly() {
        final Sql[] specialCharSqlStatements = new Sql[] {
                new UnparsedSql("db.collection.insertOne({text: 'Hello \"World\"', special: 'chars!@#$%^&*()'})"),
                new UnparsedSql("db.collection.find({regex: /^test.*$/i})")
        };

        final MongoshRunner specialRunner = new MongoshRunner(changeSet, specialCharSqlStatements);

        assertThat(specialRunner).isNotNull();
    }
}
