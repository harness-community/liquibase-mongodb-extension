package liquibase.ext.mongodb.database;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import liquibase.CatalogAndSchema;
import liquibase.database.DatabaseFactory;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.exception.DatabaseException;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static liquibase.servicelocator.PrioritizedService.PRIORITY_DATABASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class MongoLiquibaseDatabaseTest {

    @Mock
    protected MongoConnection connectionMock;

    @Mock
    protected MongoClient clientMock;

    @Mock
    protected MongoIterable<String> iterableMock;

    @Mock
    protected MongoCursor<String> mongoCursor;

    protected MongoLiquibaseDatabase database;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        resetServices();

        database = new MongoLiquibaseDatabase();
    }

    @AfterEach
    void tearDown() {
        resetServices();
    }

    protected void resetServices() {
        DatabaseFactory.reset();
    }

    @SneakyThrows
    @Test
    void getDefaultDriver() {
        database = (MongoLiquibaseDatabase) DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new MongoConnection());

        assertThat(database.getDefaultDriver("cosmos://qwe")).isNull();
        assertThat(database.getDefaultDriver("mongodb://qwe")).isEqualTo(MongoClientDriver.class.getName());
        assertThat(database.getDefaultDriver("mongodb+srv://qwe")).isEqualTo(MongoClientDriver.class.getName());
    }

    @Test
    void getDatabaseProductName() {
        assertThat(database.getDatabaseProductName()).isEqualTo("MongoDB");
    }

    @Test
    void getShortName() {
        assertThat(database.getShortName()).isEqualTo("mongodb");
    }

    @Test
    void getDefaultPort() {
        assertThat(database.getDefaultPort()).isEqualTo(27017);
    }

    @Test
    void getDefaultDatabaseProductName() {
        assertThat(database.getDefaultDatabaseProductName()).isEqualTo("MongoDB");
    }

    @Test
    void getPriority() {
        assertThat(database.getPriority())
                .isEqualTo(PRIORITY_DATABASE)
        .isEqualTo(5);
    }

    @Test
    void supportsCatalogs() {
        assertThat(database.supportsCatalogs()).isTrue();
    }

    @Test
    void supportsSchemas() {
        assertThat(database.supportsSchemas()).isFalse();
    }

    @Test
    void getSchemaAndCatalogCase() {
        assertThat(database.getSchemaAndCatalogCase()).isEqualTo(CatalogAndSchema.CatalogAndSchemaCase.ORIGINAL_CASE);
    }

    @Test
    void getObjectQuotingStrategy() {
        assertThat(database.getObjectQuotingStrategy()).isEqualTo(ObjectQuotingStrategy.LEGACY);
    }

    @Test
    void getDefaultCatalogName() {
        database.setDefaultCatalogName("catalog1");
        assertThat(database.getDefaultCatalogName()).isEqualTo("catalog1");
        // when schema present still catalog returned
        database.setDefaultSchemaName("schema1");
        assertThat(database.getDefaultCatalogName()).isEqualTo("catalog1");
    }

    @Test
    void getDefaultSchemaName() {
        database.setDefaultSchemaName("schema1");
        assertThat(database.getDefaultSchemaName()).isEqualTo("schema1");
        // when catalog is defined catalog is returned
        database.setDefaultCatalogName("catalog1");
        assertThat(database.getDefaultSchemaName()).isEqualTo("catalog1");
    }

    @Test
    void getDefaultSchema() {
        database.setDefaultSchemaName("schema1");
        database.setDefaultCatalogName("catalog1");
        assertThat(database.getDefaultSchema()).extracting(CatalogAndSchema::getCatalogName, CatalogAndSchema::getSchemaName)
                .containsExactly("catalog1", "catalog1");
    }

    @Test
    void supportsObjectTypes() {
        assertThat(database.supports(Table.class)).isTrue();
        assertThat(database.supports(Index.class)).isTrue();
        assertThat(database.supports(Catalog.class)).isTrue();
        assertThat(database.supports(Schema.class)).isFalse();
        assertThat(database.supports(Column.class)).isFalse();
    }

    @Test
    void isLiquibaseObject() {
        final Table changeLogTable = new Table().setName(database.getDatabaseChangeLogTableName());
        final Table userTable = new Table().setName("users");
        final Index changeLogIndex = new Index().setName("lock_idx").setRelation(changeLogTable);
        final Index userIndex = new Index().setName("users_idx").setRelation(userTable);

        assertThat(database.isLiquibaseObject(changeLogTable)).isTrue();
        assertThat(database.isLiquibaseObject(changeLogIndex)).isTrue();
        assertThat(database.isLiquibaseObject(userTable)).isFalse();
        assertThat(database.isLiquibaseObject(userIndex)).isFalse();
    }

    @SneakyThrows
    @Test
    void checkDatabaseConnection() {
        database.setConnection(connectionMock);
        ConnectionString connectionString = new ConnectionString("mongodb://lbuser:LiquibasePass1@localhost:27017/lbcat");
        when(connectionMock.getConnectionString()).thenReturn(connectionString);
        when(connectionMock.getMongoClient()).thenReturn(clientMock);
        when(clientMock.listDatabaseNames()).thenReturn(iterableMock);
        when(iterableMock.iterator()).thenReturn(mongoCursor);
        when(mongoCursor.hasNext()).thenReturn(false);

        assertThatExceptionOfType(DatabaseException.class)
                .isThrownBy(() ->         database.checkDatabaseConnection());
    }
}
