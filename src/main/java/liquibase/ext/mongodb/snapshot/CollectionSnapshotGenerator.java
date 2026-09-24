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
import liquibase.Scope;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.snapshot.MongoSnapshotCache.CachedCollection;
import liquibase.ext.mongodb.statement.AuthorizedListCollectionsStatement;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * generate-changelog / diff lifecycle: core snapshots the default Schema, this generator then lists
 * Mongo collections onto that Schema (same role as TableSnapshotGenerator on SQL). Each Collection is
 * later re-snapshotted so options (validator, etc.) are filled in; IndexSnapshotGenerator attaches
 * indexes. MissingCollectionChangeGenerator turns each missing Collection into createCollection.
 */
public class CollectionSnapshotGenerator extends AbstractMongoSnapshotGenerator {

    private static final String SYSTEM_COLLECTION_PREFIX = "system.";
    // Only these types are emitted; "view" and any other/unrecognized type is skipped. Missing type
    // (older/simple collections) is treated as included.
    private static final Set<String> INCLUDED_TYPES = new HashSet<>(Arrays.asList("collection", "timeseries"));

    public CollectionSnapshotGenerator() {
        super(Collection.class, Schema.class);
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        // Core's addsTo={Schema.class} generators consider themselves applicable to any AbstractJdbcDatabase,
        // which Mongo's database class extends, and would otherwise crash trying real JDBC calls under Schema.
        //noinspection unchecked
        return new Class[]{
                liquibase.snapshot.jvm.TableSnapshotGenerator.class,
                liquibase.snapshot.jvm.ViewSnapshotGenerator.class,
                liquibase.snapshot.jvm.SequenceSnapshotGenerator.class
        };
    }

    @Override
    protected DatabaseObject snapshotObject(final DatabaseObject example, final DatabaseSnapshot snapshot) throws DatabaseException {
        return snapshotCollection((Collection) example, snapshot);
    }

    @Override
    protected void addTo(final DatabaseObject parent, final DatabaseSnapshot snapshot) throws DatabaseException {
        final Schema schema = (Schema) parent;
        for (String collectionName : loadCollections(snapshot).keySet()) {
            schema.addDatabaseObject(new Collection(collectionName, schema));
        }
    }

    /** Match one collection by case-sensitive name and copy its listCollections options (validator, etc.). */
    private Collection snapshotCollection(final Collection example, final DatabaseSnapshot snapshot) throws DatabaseException {
        final CachedCollection cached = loadCollections(snapshot).get(example.getName());
        if (cached == null) {
            return null;
        }
        final Document collectionInfo = cached.getInfo();
        return new Collection(collectionInfo.getString("name"), example.getSchema())
                .setOptions(collectionInfo.get("options", Document.class));
    }

    /**
     * Loaded once per {@link DatabaseSnapshot} and shared by every subsequent call, whether that is
     * {@link #addTo} listing collections onto the Schema, or core re-snapshotting each Collection
     * example afterward: both would otherwise re-list collections from Mongo on every call.
     */
    private Map<String, CachedCollection> loadCollections(final DatabaseSnapshot snapshot) throws DatabaseException {
        final Map<String, CachedCollection> cached = MongoSnapshotCache.get(snapshot);
        if (cached != null) {
            return cached;
        }

        final MongoLiquibaseDatabase database = (MongoLiquibaseDatabase) snapshot.getDatabase();
        // Eager listIndexes avoids a second round trip per collection when IndexSnapshotGenerator
        // attaches indexes right after, but only pay for it when indexes are actually wanted.
        final boolean includeIndexes = snapshot.getSnapshotControl().shouldInclude(Index.class);

        final List<Document> listing;
        try {
            listing = new AuthorizedListCollectionsStatement().queryForList(database);
        } catch (MongoException e) {
            throw new DatabaseException("Unable to list collections", e);
        }

        final Map<String, CachedCollection> collectionsByName = new LinkedHashMap<>();
        for (Document collectionInfo : listing) {
            final String collectionName = collectionInfo.getString("name");
            if (shouldSkip(collectionName, collectionInfo, database)) {
                continue;
            }
            collectionsByName.put(collectionName, new CachedCollection(collectionInfo,
                    includeIndexes ? listIndexes(database, collectionName) : null));
        }

        // listCollections is issued with authorizedCollections:true, which returns the visible subset
        // with ok:1 rather than failing, so a privilege-limited user cannot tell a complete listing from
        // a filtered one. Say what was seen, once per snapshot, so a short changelog is explicable.
        Scope.getCurrentScope().getLog(getClass()).info("Snapshotting " + collectionsByName.size()
                + " MongoDB collection(s) visible to the current user; a user without listCollections on the"
                + " whole database sees only the collections it is authorized for");

        MongoSnapshotCache.put(snapshot, collectionsByName);
        return collectionsByName;
    }

    private List<Document> listIndexes(final MongoLiquibaseDatabase database, final String collectionName) throws DatabaseException {
        try {
            return database.getMongoDatabase().getCollection(collectionName)
                    .listIndexes().into(new ArrayList<>());
        } catch (MongoException e) {
            throw new DatabaseException("Unable to list indexes for collection '" + collectionName + "'", e);
        }
    }

    /** Skip tracking collections, system.*, views, and any type other than collection/timeseries. */
    private boolean shouldSkip(final String collectionName, final Document collectionInfo, final Database database) {
        if (collectionName == null) {
            return true;
        }
        if (collectionName.equals(database.getDatabaseChangeLogTableName())
                || collectionName.equals(database.getDatabaseChangeLogLockTableName())
                || collectionName.startsWith(SYSTEM_COLLECTION_PREFIX)) {
            return true;
        }
        final String type = collectionInfo.getString("type");
        return type != null && !INCLUDED_TYPES.contains(type);
    }
}
