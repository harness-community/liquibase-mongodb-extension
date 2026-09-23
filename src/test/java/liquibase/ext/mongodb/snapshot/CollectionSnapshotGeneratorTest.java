package liquibase.ext.mongodb.snapshot;

/*-
 * #%L
 * Liquibase MongoDB Extension
 * %%
 * Copyright (C) 2026 Harness Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.mongodb.client.ListCollectionsIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static liquibase.snapshot.SnapshotGenerator.PRIORITY_ADDITIONAL;
import static liquibase.snapshot.SnapshotGenerator.PRIORITY_DEFAULT;
import static liquibase.snapshot.SnapshotGenerator.PRIORITY_NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionSnapshotGeneratorTest {

    private final CollectionSnapshotGenerator generator = new CollectionSnapshotGenerator();

    @Mock
    private MongoLiquibaseDatabase database;

    @Mock
    private MongoDatabase mongoDatabase;

    @Mock
    private DatabaseSnapshot databaseSnapshot;

    @Mock
    private SnapshotControl snapshotControl;

    @Mock
    private ListCollectionsIterable<Document> listCollectionsIterable;

    @Mock
    private MongoCursor<Document> cursor;

    @BeforeEach
    void setUp() {
        lenient().when(database.getMongoDatabase()).thenReturn(mongoDatabase);
        lenient().when(database.getDatabaseChangeLogTableName()).thenReturn("DATABASECHANGELOG");
        lenient().when(database.getDatabaseChangeLogLockTableName()).thenReturn("DATABASECHANGELOGLOCK");
        lenient().when(databaseSnapshot.getDatabase()).thenReturn(database);
        lenient().when(databaseSnapshot.getSnapshotControl()).thenReturn(snapshotControl);
        lenient().when(mongoDatabase.listCollections()).thenReturn(listCollectionsIterable);
    }

    private void stubCollections(List<Document> collections) {
        when(listCollectionsIterable.iterator()).thenReturn(cursor);
        final java.util.Iterator<Document> it = collections.iterator();
        when(cursor.hasNext()).thenAnswer(inv -> it.hasNext());
        when(cursor.next()).thenAnswer(inv -> it.next());
    }

    @Test
    void getPriorityIsDefaultForCollectionAndAdditionalForSchema() {
        assertThat(generator.getPriority(Collection.class, database)).isEqualTo(PRIORITY_DEFAULT);
        assertThat(generator.getPriority(liquibase.structure.core.Schema.class, database)).isEqualTo(PRIORITY_ADDITIONAL);
    }

    @Test
    void getPriorityIsNoneForNonMongoDatabase() {
        final liquibase.database.Database other = org.mockito.Mockito.mock(liquibase.database.Database.class);
        assertThat(generator.getPriority(Collection.class, other)).isEqualTo(PRIORITY_NONE);
    }

    @Test
    void addToSkipsViewsSystemAndTrackingCollections() throws Exception {
        stubCollections(Arrays.asList(
                new Document("name", "users").append("type", "collection"),
                new Document("name", "userView").append("type", "view"),
                new Document("name", "system.indexes"),
                new Document("name", "DATABASECHANGELOG"),
                new Document("name", "DATABASECHANGELOGLOCK"),
                new Document("name", "readings").append("type", "timeseries"),
                new Document("name", "legacyCollection")
        ));
        when(snapshotControl.shouldInclude(Collection.class)).thenReturn(true);

        final liquibase.structure.core.Schema schema = new liquibase.structure.core.Schema();
        generator.snapshot(schema, databaseSnapshot, new liquibase.snapshot.SnapshotGeneratorChain(null) {
            @Override
            public <T extends liquibase.structure.DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot) {
                return example;
            }
        });

        final java.util.List<Collection> found = schema.getDatabaseObjects(Collection.class);
        assertThat(found).extracting(Collection::getName)
                .containsExactlyInAnyOrder("users", "readings", "legacyCollection");
    }

    @Test
    void addToRespectsCustomTrackingCollectionNames() throws Exception {
        when(database.getDatabaseChangeLogTableName()).thenReturn("customLog");
        when(database.getDatabaseChangeLogLockTableName()).thenReturn("customLogLock");
        stubCollections(Arrays.asList(
                new Document("name", "customLog"),
                new Document("name", "customLogLock"),
                new Document("name", "orders").append("type", "collection")
        ));
        when(snapshotControl.shouldInclude(Collection.class)).thenReturn(true);

        final liquibase.structure.core.Schema schema = new liquibase.structure.core.Schema();
        generator.snapshot(schema, databaseSnapshot, new liquibase.snapshot.SnapshotGeneratorChain(null) {
            @Override
            public <T extends liquibase.structure.DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot) {
                return example;
            }
        });

        assertThat(schema.getDatabaseObjects(Collection.class))
                .extracting(Collection::getName)
                .containsExactly("orders");
    }

    @Test
    void snapshotCollectionMatchesByCaseSensitiveName() throws Exception {
        stubCollections(Arrays.asList(
                new Document("name", "Users").append("type", "collection"),
                new Document("name", "users").append("type", "collection")
        ));

        final Collection example = new Collection("users", null);
        final Collection result = generator.snapshot(example, databaseSnapshot, null);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("users");
    }

    @Test
    void addToSkipsUnknownCollectionTypesNotJustViews() throws Exception {
        stubCollections(Arrays.asList(
                new Document("name", "orders").append("type", "collection"),
                new Document("name", "clustered").append("type", "clusteredCollection")
        ));
        when(snapshotControl.shouldInclude(Collection.class)).thenReturn(true);

        final liquibase.structure.core.Schema schema = new liquibase.structure.core.Schema();
        generator.snapshot(schema, databaseSnapshot, new liquibase.snapshot.SnapshotGeneratorChain(null) {
            @Override
            public <T extends liquibase.structure.DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot) {
                return example;
            }
        });

        assertThat(schema.getDatabaseObjects(Collection.class))
                .extracting(Collection::getName)
                .containsExactly("orders");
    }

    @Test
    void addToToleratesNullCollectionNameWithoutThrowing() throws Exception {
        stubCollections(Arrays.asList(
                new Document("type", "collection"),
                new Document("name", "orders").append("type", "collection")
        ));
        when(snapshotControl.shouldInclude(Collection.class)).thenReturn(true);

        final liquibase.structure.core.Schema schema = new liquibase.structure.core.Schema();
        generator.snapshot(schema, databaseSnapshot, new liquibase.snapshot.SnapshotGeneratorChain(null) {
            @Override
            public <T extends liquibase.structure.DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot) {
                return example;
            }
        });

        assertThat(schema.getDatabaseObjects(Collection.class))
                .extracting(Collection::getName)
                .containsExactly("orders");
    }

    @Test
    void snapshotCollectionToleratesNullCollectionNameWithoutThrowing() throws Exception {
        stubCollections(Arrays.asList(
                new Document("type", "collection"),
                new Document("name", "users").append("type", "collection")
        ));

        final Collection example = new Collection("users", null);
        final Collection result = generator.snapshot(example, databaseSnapshot, null);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("users");
    }

    @Test
    void addToPropagatesMongoExceptionFromListCollections() {
        when(mongoDatabase.listCollections()).thenThrow(new com.mongodb.MongoException("boom"));
        when(snapshotControl.shouldInclude(Collection.class)).thenReturn(true);

        final liquibase.structure.core.Schema schema = new liquibase.structure.core.Schema();
        assertThatThrownBy(() -> generator.snapshot(schema, databaseSnapshot, new liquibase.snapshot.SnapshotGeneratorChain(null) {
            @Override
            public <T extends liquibase.structure.DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot) {
                return example;
            }
        })).isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Unable to list collections");
    }

    @Test
    void snapshotCollectionPropagatesMongoExceptionFromListCollections() {
        when(mongoDatabase.listCollections()).thenThrow(new com.mongodb.MongoException("boom"));

        final Collection example = new Collection("users", null);
        assertThatThrownBy(() -> generator.snapshot(example, databaseSnapshot, null))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Unable to list collections while snapshotting 'users'");
    }
}
