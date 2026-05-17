package liquibase.ext.mongodb.diff.output.changelog;

import liquibase.change.Change;
import liquibase.diff.output.DiffOutputControl;
import liquibase.ext.mongodb.change.CreateCollectionChange;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.snapshot.MongoSnapshotAttributes;
import liquibase.structure.core.Index;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingObjectChangeGeneratorMongoTest {

    private final MongoLiquibaseDatabase database = new MongoLiquibaseDatabase();

    @Test
    void createsCollectionChangeFromMissingTable() {
        final Table table = new Table().setName("users");
        table.setAttribute(MongoSnapshotAttributes.COLLECTION_OPTIONS, "{\"validator\": {\"level\": \"strict\"}}");

        final Change[] changes = new MissingTableChangeGeneratorMongo()
                .fixMissing(table, new DiffOutputControl(), database, database, null);

        assertThat(changes).singleElement().isInstanceOf(CreateCollectionChange.class)
                .extracting(change -> (CreateCollectionChange) change)
                .returns("users", CreateCollectionChange::getCollectionName)
                .returns("{\"validator\": {\"level\": \"strict\"}}", CreateCollectionChange::getOptions);
    }

    @Test
    void createsIndexChangeFromMissingIndex() {
        final Table table = new Table().setName("users");
        final Index index = new Index().setName("email_unique").setRelation(table);
        index.setAttribute(MongoSnapshotAttributes.INDEX_KEYS, "{\"email\": 1}");
        index.setAttribute(MongoSnapshotAttributes.INDEX_OPTIONS, "{\"name\": \"email_unique\", \"unique\": true}");

        final Change[] changes = new MissingIndexChangeGeneratorMongo()
                .fixMissing(index, new DiffOutputControl(), database, database, null);

        assertThat(changes).singleElement().isInstanceOf(liquibase.ext.mongodb.change.CreateIndexChange.class)
                .extracting(change -> (liquibase.ext.mongodb.change.CreateIndexChange) change)
                .returns("users", liquibase.ext.mongodb.change.CreateIndexChange::getCollectionName)
                .returns("{\"email\": 1}", liquibase.ext.mongodb.change.CreateIndexChange::getKeys)
                .returns("{\"name\": \"email_unique\", \"unique\": true}",
                        liquibase.ext.mongodb.change.CreateIndexChange::getOptions);
    }
}
