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

import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Schema;

import static liquibase.plugin.Plugin.PRIORITY_SPECIALIZED;

/**
 * Resolves the Mongo database's (only) {@link Schema} without any JDBC metadata calls - core's
 * {@code SchemaSnapshotGenerator} only avoids JDBC when the example's catalog is the default one, which relies on
 * {@code Database.getDefaultCatalogName()} returning null. Mongo's default catalog name is the actual database
 * name (never null), so core's generator would otherwise ClassCastException on Mongo's non-JDBC connection.
 */
public class MongoSchemaSnapshotGenerator implements SnapshotGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof MongoLiquibaseDatabase && Schema.class.isAssignableFrom(objectType)) {
            return PRIORITY_SPECIALIZED;
        }
        return PRIORITY_NONE;
    }

    @Override
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) throws DatabaseException, InvalidExampleException {
        final Database database = snapshot.getDatabase();
        final String catalogName = database.getDefaultCatalogName();

        final String exampleCatalogName = ((Schema) example).getCatalogName();
        if (exampleCatalogName != null && !exampleCatalogName.equalsIgnoreCase(catalogName)) {
            return null;
        }

        final Catalog catalog = new Catalog(catalogName);
        catalog.setDefault(true);

        final Schema schema = new Schema(catalog, catalogName);
        schema.setDefault(true);
        //noinspection unchecked
        return (T) schema;
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        return null;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        //noinspection unchecked
        return new Class[]{liquibase.snapshot.jvm.SchemaSnapshotGenerator.class};
    }
}
