package liquibase.ext.mongodb.statement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoshStatementTest {

    @Test
    void constructor_withJavaScriptOnly_shouldUseDefaultDelimiter() {
        final String javascript = "db.users.find();";
        final MongoshStatement statement = new MongoshStatement(javascript);

        assertThat(statement.getJavaScript()).isEqualTo(javascript);
        assertThat(statement.getEndDelimiter()).isEqualTo(";");
    }

    @Test
    void constructor_withJavaScriptAndDelimiter_shouldUseProvidedDelimiter() {
        final String javascript = "db.products.insertOne({name: 'test'})";
        final String delimiter = "//";
        final MongoshStatement statement = new MongoshStatement(javascript, delimiter);

        assertThat(statement.getJavaScript()).isEqualTo(javascript);
        assertThat(statement.getEndDelimiter()).isEqualTo(delimiter);
    }

    @Test
    void getEndDelimiter_shouldHandleEscapeSequences() {
        final String javascript = "db.test.find()";
        final String delimiterWithEscapes = "\\r\\n";
        final MongoshStatement statement = new MongoshStatement(javascript, delimiterWithEscapes);

        assertThat(statement.getEndDelimiter()).isEqualTo("\r\n");
    }

    @Test
    void toJs_shouldReturnJavaScriptWithDelimiter() {
        final String javascript = "db.users.createIndex({email: 1})";
        final String delimiter = ";";
        final MongoshStatement statement = new MongoshStatement(javascript, delimiter);

        assertThat(statement.toJs()).isEqualTo(javascript + delimiter);
    }

    @Test
    void constructor_withNullJavaScript_shouldAllowNull() {
        final MongoshStatement statement = new MongoshStatement(null);

        assertThat(statement.getJavaScript()).isNull();
        assertThat(statement.getEndDelimiter()).isEqualTo(";");
        assertThat(statement.toString()).isNull();
        assertThat(statement.toJs()).isEqualTo("null;");
    }
}
