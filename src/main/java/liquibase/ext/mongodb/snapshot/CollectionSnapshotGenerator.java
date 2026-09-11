package liquibase.ext.mongodb.snapshot;

/*-
 * #%L
 * Liquibase MongoDB Extension
 * %%
 * Copyright (C) 2026 Mastercard
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
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * generate-changelog / diff lifecycle: core snapshots the default Schema, this generator then lists
 * Mongo collections onto that Schema (same role as TableSnapshotGenerator on SQL). Each Collection is
 * later re-snapshotted so options (validator, etc.) are filled in; IndexSnapshotGenerator attaches
 * indexes. MissingCollectionChangeGenerator turns each missing Collection into createCollection.
 */
public class CollectionSnapshotGenerator implements SnapshotGenerator {

    private static final String SYSTEM_COLLECTION_PREFIX = "system.";
    // Only these types are emitted; "view" and any other/unrecognized type is skipped. Missing type
    // (older/simple collections) is treated as included.
    private static final Set<String> INCLUDED_TYPES = new HashSet<>(Arrays.asList("collection", "timeseries"));

    /**
     * This JAR is Mongo-only, but SnapshotGenerator is a global SPI. We also attach to Schema
     * (PRIORITY_ADDITIONAL); without the Mongo database guard that would run on JDBC Schema snapshots
     * if this JAR were on a mixed classpath.
     */
    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (!(database instanceof MongoLiquibaseDatabase)) {
            return PRIORITY_NONE;
        }
        if (Collection.class.isAssignableFrom(objectType)) {
            return PRIORITY_DEFAULT;
        }
        if (Schema.class.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        //noinspection unchecked
        return new Class[]{Schema.class};
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
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) throws DatabaseException, InvalidExampleException {
        if (example instanceof Collection) {
            //noinspection unchecked
            return (T) snapshotCollection((Collection) example, snapshot);
        }

        final DatabaseObject chainResponse = chain.snapshot(example, snapshot);
        if (chainResponse == null) {
            return null;
        }

        if (example instanceof Schema && snapshot.getSnapshotControl().shouldInclude(Collection.class)) {
            addTo((Schema) chainResponse, snapshot);
        }

        //noinspection unchecked
        return (T) chainResponse;
    }

    private Collection snapshotCollection(Collection example, DatabaseSnapshot snapshot) throws DatabaseException {
        final MongoLiquibaseDatabase database = (MongoLiquibaseDatabase) snapshot.getDatabase();
        final MongoDatabase mongoDatabase = database.getMongoDatabase();
        try {
            for (Document collectionInfo : mongoDatabase.listCollections()) {
                final String collectionName = collectionInfo.getString("name");
                if (!Objects.equals(collectionName, example.getName()) || shouldSkip(collectionName, collectionInfo, database)) {
                    continue;
                }
                return new Collection(collectionName, example.getSchema())
                        .setOptions(collectionInfo.get("options", Document.class));
            }
        } catch (MongoException e) {
            throw new DatabaseException("Unable to list collections while snapshotting '" + example.getName() + "'", e);
        }
        return null;
    }

    private void addTo(Schema schema, DatabaseSnapshot snapshot) throws DatabaseException {
        final MongoLiquibaseDatabase database = (MongoLiquibaseDatabase) snapshot.getDatabase();
        final MongoDatabase mongoDatabase = database.getMongoDatabase();
        try {
            for (Document collectionInfo : mongoDatabase.listCollections()) {
                final String collectionName = collectionInfo.getString("name");
                if (shouldSkip(collectionName, collectionInfo, database)) {
                    continue;
                }
                schema.addDatabaseObject(new Collection(collectionName, schema));
            }
        } catch (MongoException e) {
            throw new DatabaseException("Unable to list collections", e);
        }
    }

    private boolean shouldSkip(String collectionName, Document collectionInfo, Database database) {
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
