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

import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import liquibase.exception.DatabaseException;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static liquibase.snapshot.SnapshotGenerator.PRIORITY_ADDITIONAL;
import static liquibase.snapshot.SnapshotGenerator.PRIORITY_DEFAULT;
import static liquibase.snapshot.SnapshotGenerator.PRIORITY_NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    private final Collection collection = new Collection("orders", null);
    private final Map<String, Object> scratchData = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(database.getMongoDatabase()).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("orders")).thenReturn(mongoCollection);
        lenient().when(mongoCollection.listIndexes()).thenReturn(listIndexesIterable);
        lenient().when(databaseSnapshot.getDatabase()).thenReturn(database);
        lenient().when(databaseSnapshot.getSnapshotControl()).thenReturn(snapshotControl);
        lenient().when(databaseSnapshot.getScratchData(anyString()))
                .thenAnswer(inv -> scratchData.get(inv.getArgument(0, String.class)));
        lenient().when(databaseSnapshot.setScratchData(anyString(), any()))
                .thenAnswer(inv -> scratchData.put(inv.getArgument(0, String.class), inv.getArgument(1)));
    }

    private void stubIndexes(List<Document> indexes) {
        when(listIndexesIterable.into(any())).thenAnswer(inv -> {
            final java.util.Collection<Document> target = inv.getArgument(0);
            target.addAll(indexes);
            return target;
        });
    }

    private liquibase.snapshot.SnapshotGeneratorChain passthroughChain() {
        return new liquibase.snapshot.SnapshotGeneratorChain(null) {
            @Override
            public <T extends liquibase.structure.DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot) {
                return example;
            }
        };
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

        generator.snapshot(collection, databaseSnapshot, passthroughChain());

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
        stubIndexes(Collections.singletonList(
                new Document("name", "ab_idx").append("key", Document.parse("{\"a\": 1, \"b\": -1}"))
        ));

        final Index example = new Index("ab_idx", collection);
        final Index result = generator.snapshot(example, databaseSnapshot, null);

        assertThat(result.getKeys().toJson()).isEqualTo("{\"a\": 1, \"b\": -1}");
    }

    @Test
    void addToPropagatesMongoExceptionFromListIndexes() {
        when(mongoCollection.listIndexes()).thenThrow(new com.mongodb.MongoException("boom"));
        when(snapshotControl.shouldInclude(Index.class)).thenReturn(true);

        assertThatThrownBy(() -> generator.snapshot(collection, databaseSnapshot, passthroughChain()))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Unable to list indexes for collection");
    }

    @Test
    void snapshotIndexPropagatesMongoExceptionFromListIndexes() {
        when(mongoCollection.listIndexes()).thenThrow(new com.mongodb.MongoException("boom"));

        final Index example = new Index("email_idx", collection);
        assertThatThrownBy(() -> generator.snapshot(example, databaseSnapshot, null))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Unable to list indexes for collection");
    }

    @Test
    void usesEagerlyCachedIndexesWithoutCallingListIndexesAgain() throws Exception {
        final MongoSnapshotCache.CachedCollection ordersEntry = new MongoSnapshotCache.CachedCollection(
                new Document("name", "orders").append("type", "collection"),
                Collections.singletonList(
                        new Document("name", "email_unique").append("key", new Document("email", 1)).append("unique", true)
                ));
        final Map<String, MongoSnapshotCache.CachedCollection> cached = new HashMap<>();
        cached.put("orders", ordersEntry);
        databaseSnapshot.setScratchData("liquibase.ext.mongodb.snapshot.collectionsByName", cached);
        when(snapshotControl.shouldInclude(Index.class)).thenReturn(true);

        generator.snapshot(collection, databaseSnapshot, passthroughChain());

        final java.util.Set<Index> indexes = collection.getDatabaseObjects(Index.class);
        assertThat(indexes).extracting(Index::getName).containsExactly("email_unique");
        verify(mongoCollection, never()).listIndexes();
    }

    @Test
    void fallsBackToDirectListIndexesWhenCollectionIsNotCached() throws Exception {
        databaseSnapshot.setScratchData("liquibase.ext.mongodb.snapshot.collectionsByName",
                new HashMap<String, MongoSnapshotCache.CachedCollection>());
        stubIndexes(Collections.singletonList(
                new Document("name", "email_unique").append("key", new Document("email", 1))
        ));
        when(snapshotControl.shouldInclude(Index.class)).thenReturn(true);

        generator.snapshot(collection, databaseSnapshot, passthroughChain());

        final java.util.Set<Index> indexes = collection.getDatabaseObjects(Index.class);
        assertThat(indexes).extracting(Index::getName).containsExactly("email_unique");
        verify(mongoCollection, times(1)).listIndexes();
    }

    @Test
    void fallbackFetchIsWrittenBackSoLaterIndexSnapshotsReuseIt() throws Exception {
        final Map<String, MongoSnapshotCache.CachedCollection> cached = new HashMap<>();
        cached.put("orders", new MongoSnapshotCache.CachedCollection(
                new Document("name", "orders").append("type", "collection"), null));
        databaseSnapshot.setScratchData("liquibase.ext.mongodb.snapshot.collectionsByName", cached);
        stubIndexes(Collections.singletonList(
                new Document("name", "email_unique").append("key", new Document("email", 1))
        ));
        when(snapshotControl.shouldInclude(Index.class)).thenReturn(true);

        generator.snapshot(collection, databaseSnapshot, passthroughChain());
        final Index resnapshotted = generator.snapshot(new Index("email_unique", collection), databaseSnapshot, null);

        assertThat(resnapshotted).isNotNull();
        assertThat(cached.get("orders").getIndexes()).extracting(d -> d.getString("name"))
                .containsExactly("email_unique");
        verify(mongoCollection, times(1)).listIndexes();
    }

    @Test
    void fallsBackToDirectListIndexesWhenCollectionWasCachedWithoutIndexes() throws Exception {
        final Map<String, MongoSnapshotCache.CachedCollection> cached = new HashMap<>();
        cached.put("orders", new MongoSnapshotCache.CachedCollection(
                new Document("name", "orders").append("type", "collection"), null));
        databaseSnapshot.setScratchData("liquibase.ext.mongodb.snapshot.collectionsByName", cached);
        stubIndexes(Collections.singletonList(
                new Document("name", "email_unique").append("key", new Document("email", 1))
        ));
        when(snapshotControl.shouldInclude(Index.class)).thenReturn(true);

        generator.snapshot(collection, databaseSnapshot, passthroughChain());

        assertThat(collection.getDatabaseObjects(Index.class))
                .extracting(Index::getName).containsExactly("email_unique");
        verify(mongoCollection, times(1)).listIndexes();
    }
}
