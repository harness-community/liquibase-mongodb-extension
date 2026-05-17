package liquibase.ext.mongodb.snapshot;

import com.mongodb.client.MongoDatabase;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.EmptyDatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import lombok.SneakyThrows;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class MongoSnapshotGeneratorTest {

    private MongoLiquibaseDatabase database;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        database = spy(new MongoLiquibaseDatabase());

        final MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        doReturn(mongoDatabase).when(database).getMongoDatabase();
        when(mongoDatabase.getName()).thenReturn("appdb");

        database.setDefaultCatalogName("appdb");
        database.setDefaultSchemaName("appdb");
    }

    @Test
    @SneakyThrows
    void snapshotsCatalogAndSchema() {
        final DatabaseSnapshot snapshot = snapshotFor(Catalog.class, Schema.class);

        final Catalog catalog = (Catalog) new CatalogSnapshotGeneratorMongo()
                .snapshot(new Catalog("appdb"), snapshot, null);
        final Schema schema = (Schema) new SchemaSnapshotGeneratorMongo()
                .snapshot(new Schema("appdb", "appdb"), snapshot, null);

        assertThat(catalog.getName()).isEqualTo("appdb");
        assertThat(catalog.isDefault()).isTrue();

        assertThat(schema.getCatalogName()).isEqualTo("appdb");
        assertThat(schema.getName()).isEqualTo("appdb");
        assertThat(schema.isDefault()).isTrue();
    }

    @Test
    @SneakyThrows
    void snapshotsCollectionsAndStoresOptions() {
        final Map<String, Document> collections = new LinkedHashMap<>();
        collections.put("users", new Document("name", "users")
                .append("options", new Document("validator", new Document("level", "strict"))));
        collections.put("orders", new Document("name", "orders"));

        final TestTableSnapshotGenerator generator = new TestTableSnapshotGenerator(collections);
        final DatabaseSnapshot snapshot = snapshotFor(Table.class, Schema.class);
        final Schema schema = new Schema("appdb", "appdb").setDefault(true);

        generator.addTables(schema, snapshot);

        assertThat(schema.getDatabaseObjects(Table.class))
                .extracting(Table::getName)
                .containsExactly("orders", "users");

        final Table users = generator.snapshotTable((Table) new Table().setName("users").setSchema(schema), snapshot);
        assertThat(users.getAttribute(MongoSnapshotAttributes.COLLECTION_OPTIONS, String.class))
                .contains("\"validator\"");
    }

    @Test
    @SneakyThrows
    void snapshotsIndexesWithoutIdIndex() {
        final Map<String, List<Document>> indexes = new LinkedHashMap<>();
        indexes.put("users", Arrays.asList(
                new Document("name", "_id_").append("key", new Document("_id", 1)),
                new Document("name", "email_unique")
                        .append("key", new Document("email", 1))
                        .append("unique", true),
                new Document("name", "search_text")
                        .append("key", new Document("bio", "text"))
                        .append("default_language", "english")
        ));

        final TestIndexSnapshotGenerator generator = new TestIndexSnapshotGenerator(indexes);
        final DatabaseSnapshot snapshot = snapshotFor(Index.class, Table.class, Schema.class);
        final Table users = (Table) new Table().setName("users").setSchema(new Schema("appdb", "appdb").setDefault(true));

        generator.addIndexes(users, snapshot);

        assertThat(users.getIndexes())
                .extracting(Index::getName)
                .containsExactly("email_unique", "search_text");

        final Index emailIndex = generator.snapshotIndex(new Index().setName("email_unique").setRelation(users), snapshot);
        assertThat(emailIndex.getColumns()).extracting(Column::getName).containsExactly("email");
        assertThat(emailIndex.getAttribute(MongoSnapshotAttributes.INDEX_KEYS, String.class))
                .isEqualTo("{\"email\": 1}");
        assertThat(emailIndex.getAttribute(MongoSnapshotAttributes.INDEX_OPTIONS, String.class))
                .contains("\"name\": \"email_unique\"")
                .contains("\"unique\": true");
    }

    @SafeVarargs
    private final DatabaseSnapshot snapshotFor(final Class<? extends liquibase.structure.DatabaseObject>... types)
            throws DatabaseException, liquibase.snapshot.InvalidExampleException {
        return new EmptyDatabaseSnapshot(database, new SnapshotControl(database, false, types));
    }

    private static final class TestTableSnapshotGenerator extends TableSnapshotGeneratorMongo {

        private final Map<String, Document> collections;

        private TestTableSnapshotGenerator(final Map<String, Document> collections) {
            this.collections = collections;
        }

        private Table snapshotTable(final Table example, final DatabaseSnapshot snapshot)
                throws DatabaseException, liquibase.snapshot.InvalidExampleException {
            return (Table) snapshot(example, snapshot, null);
        }

        private void addTables(final Schema schema, final DatabaseSnapshot snapshot)
                throws DatabaseException, liquibase.snapshot.InvalidExampleException {
            super.addTo(schema, snapshot);
        }

        @Override
        protected List<String> listCollectionNames(final DatabaseSnapshot snapshot) {
            return new ArrayList<>(collections.keySet());
        }

        @Override
        protected Document getCollectionMetadata(final String collectionName, final DatabaseSnapshot snapshot) {
            final Document metadata = collections.get(collectionName);
            return metadata == null ? null : new Document(metadata);
        }
    }

    private static final class TestIndexSnapshotGenerator extends IndexSnapshotGeneratorMongo {

        private final Map<String, List<Document>> indexes;

        private TestIndexSnapshotGenerator(final Map<String, List<Document>> indexes) {
            this.indexes = indexes;
        }

        private Index snapshotIndex(final Index example, final DatabaseSnapshot snapshot)
                throws DatabaseException, liquibase.snapshot.InvalidExampleException {
            return (Index) snapshot(example, snapshot, null);
        }

        private void addIndexes(final Table table, final DatabaseSnapshot snapshot)
                throws DatabaseException, liquibase.snapshot.InvalidExampleException {
            super.addTo(table, snapshot);
        }

        @Override
        protected List<Document> loadIndexes(final String collectionName, final DatabaseSnapshot snapshot) {
            final List<Document> documents = new ArrayList<>();
            for (Document indexDocument : indexes.getOrDefault(collectionName, java.util.Collections.emptyList())) {
                documents.add(new Document(indexDocument));
            }
            return documents;
        }
    }
}
