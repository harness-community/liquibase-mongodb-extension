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

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import liquibase.Scope;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import liquibase.logging.Logger;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;
import org.bson.Document;

/**
 * Snapshots Mongo indexes, attaching itself to {@link Collection} the same way CollectionSnapshotGenerator
 * attaches itself to Schema, so that snapshotting a collection also enumerates its indexes.
 */
public class IndexSnapshotGenerator implements SnapshotGenerator {

    private static final String ID_INDEX_NAME = "_id_";

    private static Logger log() {
        return Scope.getCurrentScope().getLog(IndexSnapshotGenerator.class);
    }

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (!(database instanceof MongoLiquibaseDatabase)) {
            return PRIORITY_NONE;
        }
        if (Index.class.isAssignableFrom(objectType)) {
            return PRIORITY_DEFAULT;
        }
        if (Collection.class.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        //noinspection unchecked
        return new Class[]{Collection.class};
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return null;
    }

    @Override
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) throws DatabaseException, InvalidExampleException {
        if (example instanceof Index) {
            //noinspection unchecked
            return (T) snapshotIndex((Index) example, snapshot);
        }

        final DatabaseObject chainResponse = chain.snapshot(example, snapshot);
        if (chainResponse == null) {
            return null;
        }

        if (example instanceof Collection && snapshot.getSnapshotControl().shouldInclude(Index.class)) {
            addTo((Collection) chainResponse, snapshot);
        }

        //noinspection unchecked
        return (T) chainResponse;
    }

    private Index snapshotIndex(Index example, DatabaseSnapshot snapshot) {
        final MongoDatabase mongoDatabase = ((MongoLiquibaseDatabase) snapshot.getDatabase()).getMongoDatabase();
        final Collection collection = example.getCollection();
        try {
            for (Document indexInfo : mongoDatabase.getCollection(collection.getName()).listIndexes()) {
                final String indexName = indexInfo.getString("name");
                if (!indexName.equals(example.getName()) || ID_INDEX_NAME.equals(indexName)) {
                    continue;
                }
                return toIndex(indexInfo, collection);
            }
        } catch (MongoException e) {
            log().warning("Unable to list indexes for collection '" + collection.getName() + "', skipping", e);
        }
        return null;
    }

    private void addTo(Collection collection, DatabaseSnapshot snapshot) {
        final MongoDatabase mongoDatabase = ((MongoLiquibaseDatabase) snapshot.getDatabase()).getMongoDatabase();
        try {
            for (Document indexInfo : mongoDatabase.getCollection(collection.getName()).listIndexes()) {
                final String indexName = indexInfo.getString("name");
                if (ID_INDEX_NAME.equals(indexName)) {
                    continue;
                }
                collection.addDatabaseObject(toIndex(indexInfo, collection));
            }
        } catch (MongoException e) {
            log().warning("Unable to list indexes for collection '" + collection.getName() + "', skipping", e);
        }
    }

    private Index toIndex(Document indexInfo, Collection collection) {
        final Index index = new Index(indexInfo.getString("name"), collection)
                .setKeys(indexInfo.get("key", Document.class))
                .setIndexInfo(indexInfo);
        if (Boolean.TRUE.equals(indexInfo.getBoolean("unique"))) {
            index.setUnique(true);
        }
        return index;
    }
}
