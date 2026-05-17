package liquibase.ext.mongodb.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Catalog;

public class CatalogSnapshotGeneratorMongo extends AbstractMongoSnapshotGenerator {

    public CatalogSnapshotGeneratorMongo() {
        super(Catalog.class);
    }

    @Override
    protected DatabaseObject snapshotObject(final DatabaseObject example, final DatabaseSnapshot snapshot)
            throws DatabaseException {
        final String currentCatalogName = getMongoLiquibaseDatabase(snapshot).getMongoDatabase().getName();
        final String requestedCatalogName = example.getName() != null ? example.getName() : currentCatalogName;
        if (!currentCatalogName.equalsIgnoreCase(requestedCatalogName)) {
            return null;
        }

        final Catalog catalog = new Catalog(currentCatalogName);
        catalog.setDefault(true);
        return catalog;
    }

    @Override
    protected void addTo(final DatabaseObject foundObject, final DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        // Nothing to add to.
    }
}
