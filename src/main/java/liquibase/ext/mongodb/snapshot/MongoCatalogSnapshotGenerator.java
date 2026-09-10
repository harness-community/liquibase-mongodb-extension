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
