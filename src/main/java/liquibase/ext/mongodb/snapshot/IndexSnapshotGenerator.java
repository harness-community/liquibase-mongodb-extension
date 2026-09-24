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

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.snapshot.MongoSnapshotCache.CachedCollection;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.structure.DatabaseObject;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * generate-changelog / diff lifecycle: after a Collection is snapshotted, this generator lists its
 * indexes onto that Collection (same role as IndexSnapshotGenerator attaching to Table on SQL).
 * MissingIndexChangeGenerator then emits createIndex, ordered after createCollection via runAfterTypes.
 */
public class IndexSnapshotGenerator extends AbstractMongoSnapshotGenerator {

    private static final String ID_INDEX_NAME = "_id_";

    public IndexSnapshotGenerator() {
        super(Index.class, Collection.class);
    }

    @Override
    protected DatabaseObject snapshotObject(final DatabaseObject example, final DatabaseSnapshot snapshot) throws DatabaseException {
        return snapshotIndex((Index) example, snapshot);
    }

    @Override
    protected void addTo(final DatabaseObject parent, final DatabaseSnapshot snapshot) throws DatabaseException {
        final Collection collection = (Collection) parent;
        for (Document indexInfo : listIndexes(collection, snapshot)) {
            final String indexName = indexInfo.getString("name");
            if (indexName == null || ID_INDEX_NAME.equals(indexName)) {
                continue;
            }
            collection.addDatabaseObject(toIndex(indexInfo, collection));
        }
    }

    /** Match one index by case-sensitive name. Skips the implicit {@code _id_} index. */
    private Index snapshotIndex(final Index example, final DatabaseSnapshot snapshot) throws DatabaseException {
        final Collection collection = example.getCollection();
        for (Document indexInfo : listIndexes(collection, snapshot)) {
            final String indexName = indexInfo.getString("name");
            if (indexName == null || !indexName.equals(example.getName()) || ID_INDEX_NAME.equals(indexName)) {
                continue;
            }
            return toIndex(indexInfo, collection);
        }
        return null;
    }

    /**
     * Prefers the indexes CollectionSnapshotGenerator already fetched eagerly and cached alongside the
     * collection listing; falls back to a direct listIndexes call when that cache holds no indexes for
     * this collection, e.g. an Index snapshotted directly without the Schema having been listed first,
     * or a listing taken when indexes were not yet in scope. A fallback fetch is written back to the
     * cache entry so re-snapshotting each index of that collection does not repeat it.
     */
    private List<Document> listIndexes(final Collection collection, final DatabaseSnapshot snapshot) throws DatabaseException {
        final Map<String, CachedCollection> cachedCollections = MongoSnapshotCache.get(snapshot);
        final CachedCollection cachedCollection = cachedCollections == null
                ? null
                : cachedCollections.get(collection.getName());
        if (cachedCollection != null && cachedCollection.getIndexes() != null) {
            return cachedCollection.getIndexes();
        }

        final MongoDatabase mongoDatabase = ((MongoLiquibaseDatabase) snapshot.getDatabase()).getMongoDatabase();
        final List<Document> indexes;
        try {
            indexes = mongoDatabase.getCollection(collection.getName()).listIndexes().into(new ArrayList<>());
        } catch (MongoException e) {
            throw new DatabaseException("Unable to list indexes for collection '" + collection.getName() + "'", e);
        }

        if (cachedCollection != null) {
            cachedCollection.setIndexes(indexes);
        }
        return indexes;
    }

    /** Copy name, key document, unique flag, and the raw listIndexes payload for option filtering later. */
    private Index toIndex(final Document indexInfo, final Collection collection) {
        final Index index = new Index(indexInfo.getString("name"), collection)
                .setKeys(indexInfo.get("key", Document.class))
                .setIndexInfo(indexInfo);
        if (Boolean.TRUE.equals(indexInfo.getBoolean("unique"))) {
            index.setUnique(true);
        }
        return index;
    }
}
