package liquibase.ext.mongodb.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Schema;

public class SchemaSnapshotGeneratorMongo extends AbstractMongoSnapshotGenerator {

    public SchemaSnapshotGeneratorMongo() {
        super(Schema.class);
    }

    @Override
    protected DatabaseObject snapshotObject(final DatabaseObject example, final DatabaseSnapshot snapshot)
            throws DatabaseException {
        final Schema schemaExample = (Schema) example;
        String catalogName = schemaExample.getCatalogName();
        String schemaName = schemaExample.getName();

        if (!getMongoLiquibaseDatabase(snapshot).supports(Schema.class)
                && getMongoLiquibaseDatabase(snapshot).supports(Catalog.class)) {
            if ((catalogName == null) && (schemaName != null)) {
                catalogName = schemaName;
                schemaName = null;
            }
        }

        final String currentCatalogName = getMongoLiquibaseDatabase(snapshot).getMongoDatabase().getName();
        if (catalogName == null) {
            catalogName = currentCatalogName;
        }

        if (!currentCatalogName.equalsIgnoreCase(catalogName)) {
            return null;
        }

        final Catalog catalog = new Catalog(currentCatalogName);
        catalog.setDefault(true);

        final Schema schema = new Schema(catalog, currentCatalogName);
        schema.setDefault(true);
        return schema;
    }

    @Override
    protected void addTo(final DatabaseObject foundObject, final DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        // Nothing to add to.
    }
}
