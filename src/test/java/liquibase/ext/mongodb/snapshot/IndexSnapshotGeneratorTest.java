package liquibase.ext.mongodb.snapshot;

/*-
 * #%L
 * Liquibase MongoDB Extension
 * %%
 * Copyright (C) 2019 Mastercard
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

import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static liquibase.snapshot.SnapshotGenerator.PRIORITY_ADDITIONAL;
import static liquibase.snapshot.SnapshotGenerator.PRIORITY_DEFAULT;
import static liquibase.snapshot.SnapshotGenerator.PRIORITY_NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexSnapshotGeneratorTest {

    private final IndexSnapshotGenerator generator = new IndexSnapshotGenerator();

    @Mock
    private MongoLiquibaseDatabase database;

    @Mock
    private MongoDatabase mongoDatabase;

    @Mock
    private MongoCollection<Document> mongoCollection;

    @Mock
    private DatabaseSnapshot databaseSnapshot;

    @Mock
    private SnapshotControl snapshotControl;

    @Mock
    private ListIndexesIterable<Document> listIndexesIterable;

    @Mock
    private MongoCursor<Document> cursor;

    private final Collection collection = new Collection("orders", null);

    @BeforeEach
    void setUp() {
        lenient().when(database.getMongoDatabase()).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("orders")).thenReturn(mongoCollection);
        lenient().when(mongoCollection.listIndexes()).thenReturn(listIndexesIterable);
        lenient().when(databaseSnapshot.getDatabase()).thenReturn(database);
        lenient().when(databaseSnapshot.getSnapshotControl()).thenReturn(snapshotControl);
    }

    private void stubIndexes(List<Document> indexes) {
        when(listIndexesIterable.iterator()).thenReturn(cursor);
        final Iterator<Document> it = indexes.iterator();
        when(cursor.hasNext()).thenAnswer(inv -> it.hasNext());
        when(cursor.next()).thenAnswer(inv -> it.next());
    }

    @Test
    void getPriorityIsDefaultForIndexAndAdditionalForCollection() {
        assertThat(generator.getPriority(Index.class, database)).isEqualTo(PRIORITY_DEFAULT);
        assertThat(generator.getPriority(Collection.class, database)).isEqualTo(PRIORITY_ADDITIONAL);
    }

    @Test
    void getPriorityIsNoneForNonMongoDatabase() {
        final liquibase.database.Database other = org.mockito.Mockito.mock(liquibase.database.Database.class);
        assertThat(generator.getPriority(Index.class, other)).isEqualTo(PRIORITY_NONE);
    }

    @Test
    void addToExcludesIdIndex() throws Exception {
        stubIndexes(Arrays.asList(
                new Document("name", "_id_").append("key", new Document("_id", 1)),
                new Document("name", "email_unique").append("key", new Document("email", 1)).append("unique", true)
        ));
        when(snapshotControl.shouldInclude(Index.class)).thenReturn(true);

        generator.snapshot(collection, databaseSnapshot, new liquibase.snapshot.SnapshotGeneratorChain(null) {
            @Override
            public <T extends liquibase.structure.DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot) {
                return example;
            }
        });

        final java.util.Set<Index> indexes = collection.getDatabaseObjects(Index.class);
        assertThat(indexes).extracting(Index::getName).containsExactly("email_unique");
        assertThat(indexes.iterator().next().isUnique()).isTrue();
    }

    @Test
    void snapshotIndexMatchesByCaseSensitiveName() throws Exception {
        stubIndexes(Arrays.asList(
                new Document("name", "Email_idx").append("key", new Document("email", 1)),
                new Document("name", "email_idx").append("key", new Document("email", 1))
        ));

        final Index example = new Index("email_idx", collection);
        final Index result = generator.snapshot(example, databaseSnapshot, null);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("email_idx");
    }

    @Test
    void snapshotIndexPreservesCompoundKeyOrder() throws Exception {
        stubIndexes(Arrays.asList(
                new Document("name", "ab_idx").append("key", Document.parse("{\"a\": 1, \"b\": -1}"))
        ));

        final Index example = new Index("ab_idx", collection);
        final Index result = generator.snapshot(example, databaseSnapshot, null);

        assertThat(result.getKeys().toJson()).isEqualTo("{\"a\": 1, \"b\": -1}");
    }

    @Test
    void addToSwallowsMongoExceptionFromListIndexes() throws Exception {
        when(mongoCollection.listIndexes()).thenThrow(new com.mongodb.MongoException("boom"));
        when(snapshotControl.shouldInclude(Index.class)).thenReturn(true);

        generator.snapshot(collection, databaseSnapshot, new liquibase.snapshot.SnapshotGeneratorChain(null) {
            @Override
            public <T extends liquibase.structure.DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot) {
                return example;
            }
        });

        assertThat(collection.getDatabaseObjects(Index.class)).isEmpty();
    }

    @Test
    void snapshotIndexSwallowsMongoExceptionFromListIndexes() throws Exception {
        when(mongoCollection.listIndexes()).thenThrow(new com.mongodb.MongoException("boom"));

        final Index example = new Index("email_idx", collection);
        final Index result = generator.snapshot(example, databaseSnapshot, null);

        assertThat(result).isNull();
    }
}
