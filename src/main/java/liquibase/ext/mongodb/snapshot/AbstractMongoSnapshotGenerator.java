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

import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;

/**
 * generate-changelog / diff lifecycle shared by {@link CollectionSnapshotGenerator} (Schema -&gt;
 * Collection) and {@link IndexSnapshotGenerator} (Collection -&gt; Index): each attaches to Schema/
 * Collection with {@code PRIORITY_ADDITIONAL} to list its children once core has snapshotted the
 * parent, and separately fills in a single example object (name + options/keys) with
 * {@code PRIORITY_DEFAULT} when core re-snapshots each child it was just told about.
 */
public abstract class AbstractMongoSnapshotGenerator implements SnapshotGenerator {

    private final Class<? extends DatabaseObject> defaultFor;
    private final Class<? extends DatabaseObject> addsTo;

    protected AbstractMongoSnapshotGenerator(final Class<? extends DatabaseObject> defaultFor,
                                              final Class<? extends DatabaseObject> addsTo) {
        this.defaultFor = defaultFor;
        this.addsTo = addsTo;
    }

    /**
     * This JAR is Mongo-only, but SnapshotGenerator is a global SPI. We also attach to the parent type
     * (PRIORITY_ADDITIONAL); without the Mongo database guard that would run on JDBC snapshots of the
     * same parent type if this JAR were on a mixed classpath.
     */
    @Override
    public int getPriority(final Class<? extends DatabaseObject> objectType, final Database database) {
        if (!(database instanceof MongoLiquibaseDatabase)) {
            return PRIORITY_NONE;
        }
        if (defaultFor.isAssignableFrom(objectType)) {
            return PRIORITY_DEFAULT;
        }
        if (addsTo.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        //noinspection unchecked
        return new Class[]{addsTo};
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return null;
    }

    /**
     * Two roles: fill in an example object, or after the parent is snapshotted, list this type's
     * children onto it so core will then snapshot each child in turn.
     */
    @Override
    public <T extends DatabaseObject> T snapshot(final T example, final DatabaseSnapshot snapshot, final SnapshotGeneratorChain chain)
            throws DatabaseException, InvalidExampleException {
        if (defaultFor.isAssignableFrom(example.getClass())) {
            //noinspection unchecked
            return (T) snapshotObject(example, snapshot);
        }

        final DatabaseObject chainResponse = chain.snapshot(example, snapshot);
        if (chainResponse == null) {
            return null;
        }

        if (addsTo.isAssignableFrom(example.getClass()) && snapshot.getSnapshotControl().shouldInclude(defaultFor)) {
            addTo(chainResponse, snapshot);
        }

        //noinspection unchecked
        return (T) chainResponse;
    }

    /** Fill in a single example object (name + options/keys), or {@code null} if it no longer exists. */
    protected abstract DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException;

    /** List this generator's children onto the already-snapshotted parent. */
    protected abstract void addTo(DatabaseObject parent, DatabaseSnapshot snapshot) throws DatabaseException;
}
