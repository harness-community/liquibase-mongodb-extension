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
import liquibase.structure.core.Catalog;

import static liquibase.plugin.Plugin.PRIORITY_SPECIALIZED;

/**
 * Resolves the Mongo database itself as the (only) {@link Catalog}, without any JDBC metadata calls -
 * core's own {@code CatalogSnapshotGenerator} would ClassCastException on Mongo's non-JDBC connection.
 */
public class MongoCatalogSnapshotGenerator implements SnapshotGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof MongoLiquibaseDatabase && Catalog.class.isAssignableFrom(objectType)) {
            return PRIORITY_SPECIALIZED;
        }
        return PRIORITY_NONE;
    }

    @Override
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) throws DatabaseException, InvalidExampleException {
        final Database database = snapshot.getDatabase();
        final String catalogName = database.getDefaultCatalogName();

        final String exampleName = example.getName();
        if (exampleName != null && !exampleName.equalsIgnoreCase(catalogName)) {
            return null;
        }

        final Catalog catalog = new Catalog(catalogName);
        catalog.setDefault(true);
        //noinspection unchecked
        return (T) catalog;
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        return null;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        //noinspection unchecked
        return new Class[]{liquibase.snapshot.jvm.CatalogSnapshotGenerator.class};
    }
}
