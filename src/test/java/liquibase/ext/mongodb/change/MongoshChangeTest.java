package liquibase.ext.mongodb.change;

import liquibase.exception.ValidationErrors;
import liquibase.ext.mongodb.statement.MongoshStatement;
import liquibase.ext.mongodb.tools.MongoshRunner;
import liquibase.statement.SqlStatement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoshChangeTest extends AbstractMongoChangeTest {

    @Test
    void getConfirmationMessage_shouldReturnCorrectMessage() {
        final MongoshChange mongoshChange = new MongoshChange();
        assertThat(mongoshChange.getConfirmationMessage())
                .isEqualTo("Mongosh command executed");
    }

    @Test
    void setMongo_shouldSetMongoAndSqlProperties() {
        final MongoshChange mongoshChange = new MongoshChange();
        final String mongoScript = "db.users.find({});";

        mongoshChange.setMongo(mongoScript);

        assertThat(mongoshChange.getMongo()).isEqualTo(mongoScript);
        assertThat(mongoshChange.getSql()).isEqualTo(mongoScript);
    }

    @Test
    void generateStatements_withValidMongoScript_shouldReturnMongoshStatement() {
        final MongoshChange mongoshChange = new MongoshChange();
        final String mongoScript = "db.collection.insertOne({name: 'test'});";
        mongoshChange.setMongo(mongoScript);

        final SqlStatement[] statements = mongoshChange.generateStatements(database);

        assertThat(statements).hasSize(1);
        assertThat(statements[0]).isInstanceOf(MongoshStatement.class);

        final MongoshStatement mongoshStatement = (MongoshStatement) statements[0];
        assertThat(mongoshStatement.getJavaScript()).isEqualTo(mongoScript);
        assertThat(mongoshStatement.getEndDelimiter()).isEqualTo(";");
    }

    @Test
    void generateStatements_withNullMongo_shouldReturnEmptyArray() {
        final MongoshChange mongoshChange = new MongoshChange();
        mongoshChange.setMongo(null);

        final SqlStatement[] statements = mongoshChange.generateStatements(database);

        assertThat(statements).isEmpty();
    }

    @Test
    void validate_withEmptyMongo_shouldReturnValidationError() {
        final MongoshChange mongoshChange = new MongoshChange();
        mongoshChange.setMongo("   ");

        final ValidationErrors errors = mongoshChange.validate(database);

        assertThat(errors.hasErrors()).isTrue();
        assertThat(errors.getErrorMessages())
                .anyMatch(msg -> msg.contains("'mongo' property is required"));
    }

    @Test
    void validate_withOidcConnection_shouldReturnValidationError() {
        final MongoshChange mongoshChange = new MongoshChange();
        mongoshChange.setMongo("db.users.find({});");
        connectWith("mongodb://localhost:27017/test_db?authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT:k8s");

        assertThat(mongoshChange.validate(database).getErrorMessages())
                .containsExactly(MongoshRunner.MSG_MONGOSH_UNSUPPORTED_WITH_OIDC);
    }

    @Test
    void validate_withNonOidcConnection_shouldReturnNoErrors() {
        final MongoshChange mongoshChange = new MongoshChange();
        mongoshChange.setMongo("db.users.find({});");
        connectWith("mongodb://user1:password1@localhost:27017/test_db");

        assertThat(mongoshChange.validate(database).hasErrors()).isFalse();
    }

    @Test
    void getSerializableFields_shouldReturnCorrectFields() {
        final MongoshChange mongoshChange = new MongoshChange();

        assertThat(mongoshChange.getSerializableFields())
                .containsExactlyInAnyOrder("mongo", "dbms");
    }
}
