package liquibase.ext.mongodb.command;

import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.ListCollectionsIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import liquibase.command.CommandResultsBuilder;
import liquibase.command.CommandScope;
import liquibase.database.Database;
import liquibase.exception.CommandValidationException;
import liquibase.exception.LiquibaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoGenerateChangelogCommandStepTest {

    @TempDir
    Path tempDir;

    @Test
    void commandIsDiscoverable() {
        assertThatCode(() -> new CommandScope("mongoGenerateChangelog"))
                .doesNotThrowAnyException();
    }

    @Test
    void run_generatesCollectionAndIndexChanges_skippingIdIndex() throws Exception {
        MongoDatabase mongoDatabase = mockDatabaseWithCollections(
                new Document("name", "users").append("type", "collection").append("options", new Document("capped", false)));
        mockIndexes(mongoDatabase, "users",
                new Document("v", 2).append("key", new Document("_id", 1)).append("name", "_id_"),
                new Document("v", 2).append("key", new Document("email", 1)).append("name", "email_1").append("unique", true));

        Path outputFile = tempDir.resolve("changelog.yaml");
        CommandResultsBuilder resultsBuilder = run(mongoDatabase, outputFile, "Harness", null, false);

        Map<String, Object> root = readYaml(outputFile);
        List<Map<String, Object>> changeLog = (List<Map<String, Object>>) root.get("databaseChangeLog");
        assertThat(changeLog).hasSize(1);

        Map<String, Object> changeSet = (Map<String, Object>) changeLog.get(0).get("changeSet");
        assertThat(changeSet.get("id")).isEqualTo("baseline-collections-users");
        assertThat(changeSet.get("author")).isEqualTo("Harness");

        List<Map<String, Object>> changes = (List<Map<String, Object>>) changeSet.get("changes");
        assertThat(changes).hasSize(2);

        Map<String, Object> createCollection = (Map<String, Object>) changes.get(0).get("createCollection");
        assertThat(createCollection.get("collectionName")).isEqualTo("users");
        assertThat((String) createCollection.get("options")).contains("capped");

        Map<String, Object> indexChange = changes.get(1);
        Map<String, Object> createIndex = (Map<String, Object>) indexChange.get("createIndex");
        assertThat(createIndex.get("collectionName")).isEqualTo("users");
        assertThat((String) createIndex.get("keys")).contains("email");
        assertThat((String) createIndex.get("options")).contains("email_1");
        assertThat(indexChange.get("unique")).isEqualTo(true);

        assertThat((String) resultsBuilder.getResult("output")).contains("1 changeSet(s)");
    }

    @Test
    void run_skipsTrackingAndSystemCollections() throws Exception {
        MongoDatabase mongoDatabase = mockDatabaseWithCollections(
                new Document("name", "DATABASECHANGELOG").append("type", "collection"),
                new Document("name", "DATABASECHANGELOGLOCK").append("type", "collection"),
                new Document("name", "system.views").append("type", "collection"),
                new Document("name", "aView").append("type", "view"),
                new Document("name", "orders").append("type", "collection").append("options", new Document()));
        mockIndexes(mongoDatabase, "orders");

        Path outputFile = tempDir.resolve("changelog.yaml");
        run(mongoDatabase, outputFile, "Harness", null, false);

        Map<String, Object> root = readYaml(outputFile);
        List<Map<String, Object>> changeLog = (List<Map<String, Object>>) root.get("databaseChangeLog");
        assertThat(changeLog).hasSize(1);
        Map<String, Object> changeSet = (Map<String, Object>) changeLog.get(0).get("changeSet");
        assertThat(changeSet.get("id")).isEqualTo("baseline-collections-orders");
    }

    @Test
    void run_diffTypesIndexesOnly_omitsCreateCollectionChange() throws Exception {
        MongoDatabase mongoDatabase = mockDatabaseWithCollections(
                new Document("name", "users").append("type", "collection").append("options", new Document()));
        mockIndexes(mongoDatabase, "users",
                new Document("v", 2).append("key", new Document("email", 1)).append("name", "email_1"));

        Path outputFile = tempDir.resolve("changelog.yaml");
        run(mongoDatabase, outputFile, "Harness", "indexes", false);

        Map<String, Object> root = readYaml(outputFile);
        List<Map<String, Object>> changeLog = (List<Map<String, Object>>) root.get("databaseChangeLog");
        Map<String, Object> changeSet = (Map<String, Object>) changeLog.get(0).get("changeSet");
        List<Map<String, Object>> changes = (List<Map<String, Object>>) changeSet.get("changes");

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0)).containsKey("createIndex");
    }

    @Test
    void run_diffTypesCollectionsOnly_omitsIndexChanges() throws Exception {
        MongoDatabase mongoDatabase = mockDatabaseWithCollections(
                new Document("name", "users").append("type", "collection").append("options", new Document()));

        Path outputFile = tempDir.resolve("changelog.yaml");
        run(mongoDatabase, outputFile, "Harness", "collections", false);

        Map<String, Object> root = readYaml(outputFile);
        List<Map<String, Object>> changeLog = (List<Map<String, Object>>) root.get("databaseChangeLog");
        Map<String, Object> changeSet = (Map<String, Object>) changeLog.get(0).get("changeSet");
        List<Map<String, Object>> changes = (List<Map<String, Object>>) changeSet.get("changes");

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0)).containsKey("createCollection");
    }

    @Test
    void run_withInvalidDiffTypes_throwsValidationError() {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        Path outputFile = tempDir.resolve("changelog.yaml");

        assertThatThrownBy(() -> run(mongoDatabase, outputFile, "Harness", "documents", false))
                .isInstanceOf(CommandValidationException.class)
                .hasMessageContaining("documents");
    }

    @Test
    void run_whenFileExistsAndOverwriteNotSet_throwsError() throws Exception {
        MongoDatabase mongoDatabase = mockDatabaseWithCollections();
        Path outputFile = tempDir.resolve("changelog.yaml");
        Files.write(outputFile, "existing content".getBytes());

        assertThatThrownBy(() -> run(mongoDatabase, outputFile, "Harness", null, false))
                .isInstanceOf(LiquibaseException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void run_whenFileExistsAndOverwriteSet_overwritesFile() throws Exception {
        MongoDatabase mongoDatabase = mockDatabaseWithCollections(
                new Document("name", "users").append("type", "collection").append("options", new Document()));
        mockIndexes(mongoDatabase, "users");

        Path outputFile = tempDir.resolve("changelog.yaml");
        Files.write(outputFile, "existing content".getBytes());

        run(mongoDatabase, outputFile, "Harness", null, true);

        assertThat(Files.readString(outputFile)).contains("baseline-collections-users");
    }

    @Test
    void run_createsParentDirectoriesForOutputFile() throws Exception {
        MongoDatabase mongoDatabase = mockDatabaseWithCollections();
        Path outputFile = tempDir.resolve("nested/dir/changelog.yaml");

        run(mongoDatabase, outputFile, "Harness", null, false);

        assertThat(Files.exists(outputFile)).isTrue();
    }

    @Test
    void run_withNonMongoDatabase_throwsHelpfulError() throws Exception {
        Database nonMongoDb = mock(Database.class);
        Path outputFile = tempDir.resolve("changelog.yaml");

        CommandScope scope = buildScope(outputFile, "Harness", null, false, nonMongoDb);
        CommandResultsBuilder resultsBuilder = newResultsBuilder(scope, new ByteArrayOutputStream());

        assertThatThrownBy(() -> new MongoGenerateChangelogCommandStep().run(resultsBuilder))
                .isInstanceOf(LiquibaseException.class)
                .hasMessage("The mongoGenerateChangelog command is only supported for MongoDB databases.");
    }

    private CommandResultsBuilder run(MongoDatabase mongoDatabase, Path outputFile, String author, String diffTypes, boolean overwrite) throws Exception {
        MongoLiquibaseDatabase database = mock(MongoLiquibaseDatabase.class);
        when(database.getMongoDatabase()).thenReturn(mongoDatabase);

        CommandScope scope = buildScope(outputFile, author, diffTypes, overwrite, database);
        CommandResultsBuilder resultsBuilder = newResultsBuilder(scope, new ByteArrayOutputStream());
        new MongoGenerateChangelogCommandStep().run(resultsBuilder);
        return resultsBuilder;
    }

    private CommandScope buildScope(Path outputFile, String author, String diffTypes, boolean overwrite, Database database) throws Exception {
        CommandScope scope = new CommandScope("mongoGenerateChangelog")
                .addArgumentValue(MongoGenerateChangelogCommandStep.CHANGELOG_FILE_ARG, outputFile.toString())
                .addArgumentValue(MongoGenerateChangelogCommandStep.OVERWRITE_OUTPUT_FILE_ARG, overwrite)
                .provideDependency(Database.class, database);
        if (author != null) {
            scope.addArgumentValue(MongoGenerateChangelogCommandStep.AUTHOR_ARG, author);
        }
        if (diffTypes != null) {
            scope.addArgumentValue(MongoGenerateChangelogCommandStep.DIFF_TYPES_ARG, diffTypes);
        }
        return scope;
    }

    private static MongoDatabase mockDatabaseWithCollections(Document... collectionInfos) {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        ListCollectionsIterable<Document> iterable = mock(ListCollectionsIterable.class);
        when(mongoDatabase.listCollections()).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursorOf(collectionInfos));
        return mongoDatabase;
    }

    private static void mockIndexes(MongoDatabase mongoDatabase, String collectionName, Document... indexes) {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(mongoDatabase.getCollection(collectionName)).thenReturn(collection);
        ListIndexesIterable<Document> iterable = mock(ListIndexesIterable.class);
        when(collection.listIndexes()).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursorOf(indexes));
    }

    private static MongoCursor<Document> cursorOf(Document... documents) {
        return new IteratorMongoCursor(Arrays.asList(documents).iterator());
    }

    private static final class IteratorMongoCursor implements MongoCursor<Document> {
        private final java.util.Iterator<Document> delegate;

        private IteratorMongoCursor(java.util.Iterator<Document> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Document next() {
            return delegate.next();
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public Document tryNext() {
            return delegate.hasNext() ? delegate.next() : null;
        }

        @Override
        public ServerCursor getServerCursor() {
            return null;
        }

        @Override
        public ServerAddress getServerAddress() {
            return new ServerAddress();
        }
    }

    private static Map<String, Object> readYaml(Path file) throws Exception {
        try (java.io.Reader reader = Files.newBufferedReader(file)) {
            return new Yaml().load(reader);
        }
    }

    private static CommandResultsBuilder newResultsBuilder(CommandScope commandScope, OutputStream outputStream) {
        try {
            Constructor<CommandResultsBuilder> ctor =
                    CommandResultsBuilder.class.getDeclaredConstructor(CommandScope.class, OutputStream.class);
            ctor.setAccessible(true);
            return ctor.newInstance(commandScope, outputStream);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create CommandResultsBuilder for tests", e);
        }
    }
}
