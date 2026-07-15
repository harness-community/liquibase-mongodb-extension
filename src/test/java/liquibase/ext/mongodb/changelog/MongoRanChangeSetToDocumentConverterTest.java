package liquibase.ext.mongodb.changelog;

import liquibase.change.CheckSum;
import liquibase.changelog.ChangeSet;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class MongoRanChangeSetToDocumentConverterTest {

    protected MongoRanChangeSetToDocumentConverter converter = new MongoRanChangeSetToDocumentConverter();

    @Test
    void toDocument() {
    }

    @Test
    void fromDocument() {

        // Maximum
        final Date dateExecuted = new Date();
        final Document maximal = new Document()
                .append("id", "cs4")
                .append("author", "Alex")
                .append("fileName", "liquibase/file.xml")
                .append("dateExecuted", dateExecuted)
                .append("orderExecuted", 100)
                .append("execType", "EXECUTED")
                .append("md5sum", "9:c3981fa8d26e95d911fe8eaeb6570f2f")
                .append("description", "The Description")
                .append("comments", "The Comments")
                .append("tag", "Tags")
                .append("contexts", "context1,context2")
                .append("labels", "label1,label2")
                .append("deploymentId", "The Deployment Id")
                .append("liquibase", "Liquibase Version");

        assertThat(converter.fromDocument(maximal))
                .isInstanceOf(MongoRanChangeSet.class)
                .returns("cs4", MongoRanChangeSet::getId)
                .returns("Alex", MongoRanChangeSet::getAuthor)
                .returns("liquibase/file.xml", MongoRanChangeSet::getChangeLog)
                .returns(dateExecuted, MongoRanChangeSet::getDateExecuted)
                .returns(100, MongoRanChangeSet::getOrderExecuted)
                .returns(ChangeSet.ExecType.EXECUTED, MongoRanChangeSet::getExecType)
                .returns(CheckSum.compute("QWERTY"), MongoRanChangeSet::getLastCheckSum)
                .returns( "The Description", MongoRanChangeSet::getDescription)
                .returns( "The Comments", MongoRanChangeSet::getComments)
                .returns("Tags", MongoRanChangeSet::getTag)
                .returns(true, c-> c.getContextExpression().getContexts().containsAll(Arrays.asList("context1","context2")))
                .returns(true, c-> c.getLabels().getLabels().containsAll(Arrays.asList("label1", "label2")))
                .returns("The Deployment Id", MongoRanChangeSet::getDeploymentId)
                .returns("Liquibase Version", MongoRanChangeSet::getLiquibaseVersion);

        // Empty Document
        assertThat(converter.fromDocument(new Document()))
                .isInstanceOf(MongoRanChangeSet.class)
                .hasAllNullFieldsOrPropertiesExcept("contextExpression", "labels");
    }

    @Test
    void fromDocumentWithLongOrderExecuted() {
        final Date dateExecuted = new Date();
        final Document documentWithLong = new Document()
                .append("id", "cs5")
                .append("author", "TestAuthor")
                .append("fileName", "liquibase/test.xml")
                .append("dateExecuted", dateExecuted)
                .append("orderExecuted", 100L)
                .append("execType", "EXECUTED")
                .append("md5sum", "9:c3981fa8d26e95d911fe8eaeb6570f2f")
                .append("description", "Test Description")
                .append("deploymentId", "Test Deployment")
                .append("liquibase", "4.33.0");

        assertThat(converter.fromDocument(documentWithLong))
                .isInstanceOf(MongoRanChangeSet.class)
                .returns("cs5", MongoRanChangeSet::getId)
                .returns("TestAuthor", MongoRanChangeSet::getAuthor)
                .returns("liquibase/test.xml", MongoRanChangeSet::getChangeLog)
                .returns(dateExecuted, MongoRanChangeSet::getDateExecuted)
                .returns(100, MongoRanChangeSet::getOrderExecuted)
                .returns(ChangeSet.ExecType.EXECUTED, MongoRanChangeSet::getExecType);
    }

    @Test
    void fromDocumentWithDoubleOrderExecuted() {
        final Date dateExecuted = new Date();
        final Document documentWithDouble = new Document()
                .append("id", "cs6")
                .append("author", "TestAuthor2")
                .append("fileName", "liquibase/test2.xml")
                .append("dateExecuted", dateExecuted)
                .append("orderExecuted", 200.0)
                .append("execType", "EXECUTED")
                .append("md5sum", "9:c3981fa8d26e95d911fe8eaeb6570f2f")
                .append("description", "Test Description 2")
                .append("deploymentId", "Test Deployment 2")
                .append("liquibase", "4.33.0");

        assertThat(converter.fromDocument(documentWithDouble))
                .isInstanceOf(MongoRanChangeSet.class)
                .returns("cs6", MongoRanChangeSet::getId)
                .returns(200, MongoRanChangeSet::getOrderExecuted);
    }

    @Test
    void fromDocumentWithOverflowOrderExecuted() {
        final Date dateExecuted = new Date();
        final Document documentWithOverflow = new Document()
                .append("id", "cs7")
                .append("author", "TestAuthor3")
                .append("fileName", "liquibase/test3.xml")
                .append("dateExecuted", dateExecuted)
                .append("orderExecuted", 3000000000L)
                .append("execType", "EXECUTED");

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> converter.fromDocument(documentWithOverflow)
        ))
                .hasMessageContaining("exceeds Integer range")
                .hasMessageContaining("3000000000");
    }

    @Test
    void fromDocumentWithInvalidTypeOrderExecuted() {
        final Date dateExecuted = new Date();
        final Document documentWithString = new Document()
                .append("id", "cs8")
                .append("author", "TestAuthor4")
                .append("fileName", "liquibase/test4.xml")
                .append("dateExecuted", dateExecuted)
                .append("orderExecuted", "not_a_number")
                .append("execType", "EXECUTED");

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> converter.fromDocument(documentWithString)
        ))
                .hasMessageContaining("invalid type")
                .hasMessageContaining("String");
    }
}