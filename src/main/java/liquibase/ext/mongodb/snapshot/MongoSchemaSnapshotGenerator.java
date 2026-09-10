package liquibase.ext.mongodb.snapshot;

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
