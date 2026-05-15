package liquibase.nosql.executor;

import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.statement.MongoshStatement;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MongoshGeneratorTest {

    private MongoshGenerator generator;
    private Database database;
    private SqlGeneratorChain<MongoshStatement> sqlGeneratorChain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        generator = new MongoshGenerator();
        database = new MongoLiquibaseDatabase();
        sqlGeneratorChain = mock(SqlGeneratorChain.class);
    }

    @Test
    void validate_withValidJavaScript_shouldReturnNoErrors() {
        final MongoshStatement statement = new MongoshStatement("db.users.find({status: 'active'})");

        final ValidationErrors errors = generator.validate(statement, database, sqlGeneratorChain);

        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void validate_withWhitespaceOnlyJavaScript_shouldReturnValidationError() {
        final MongoshStatement statement = new MongoshStatement("   \t\n  ");

        final ValidationErrors errors = generator.validate(statement, database, sqlGeneratorChain);

        assertThat(errors.hasErrors()).isTrue();
        assertThat(errors.getErrorMessages())
                .anyMatch(msg -> msg.contains("JavaScript content is required"));
    }

    @Test
    void generateSql_withValidJavaScript_shouldReturnFormattedSql() {
        final String javascript = "db.products.insertOne({name: 'test', price: 100})";
        final MongoshStatement statement = new MongoshStatement(javascript);

        final Sql[] result = generator.generateSql(statement, database, sqlGeneratorChain);

        assertThat(result).hasSize(1);
        assertThat(result[0].toSql()).contains("// MongoDB JavaScript:");
        assertThat(result[0].toSql()).contains("db.products.insertOne({name: 'test', price: 100})");
    }

    @Test
    void generateSql_withJavaScriptEndingSemicolon_shouldRemoveTrailingSemicolon() {
        final String javascript = "db.orders.find({status: 'pending'});";
        final MongoshStatement statement = new MongoshStatement(javascript);

        final Sql[] result = generator.generateSql(statement, database, sqlGeneratorChain);

        assertThat(result).hasSize(1);
        final String generatedSql = result[0].toSql();
        assertThat(generatedSql).contains("// MongoDB JavaScript:");
        assertThat(generatedSql).contains("db.orders.find({status: 'pending'})");
        assertThat(generatedSql).doesNotEndWith(";");
    }

    @Test
    void generateSql_withNullJavaScript_shouldReturnEmptyArray() {
        final MongoshStatement statement = new MongoshStatement(null);

        final Sql[] result = generator.generateSql(statement, database, sqlGeneratorChain);

        assertThat(result).isEmpty();
    }
}
