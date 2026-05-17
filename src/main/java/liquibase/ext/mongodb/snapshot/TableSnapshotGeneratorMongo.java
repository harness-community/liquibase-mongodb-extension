package liquibase.ext.mongodb.snapshot;

import com.mongodb.client.model.Filters;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TableSnapshotGeneratorMongo extends AbstractMongoSnapshotGenerator {

    public TableSnapshotGeneratorMongo() {
        super(Table.class, new Class[]{Schema.class});
    }

    @Override
    protected DatabaseObject snapshotObject(final DatabaseObject example, final DatabaseSnapshot snapshot)
            throws DatabaseException {
        final Table tableExample = (Table) example;
        final Document collectionMetadata = getCollectionMetadata(tableExample.getName(), snapshot);
        if (collectionMetadata == null) {
            return null;
        }

        final Table table = new Table().setName(tableExample.getName());
        table.setSchema(resolveSchema(tableExample, snapshot));

        final Document options = collectionMetadata.get("options", Document.class);
        if ((options != null) && !options.isEmpty()) {
            table.setAttribute(MongoSnapshotAttributes.COLLECTION_OPTIONS, options.toJson());
        }

        return table;
    }

    @Override
    protected void addTo(final DatabaseObject foundObject, final DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Table.class)
                || !snapshot.getDatabase().supports(Table.class)
                || !(foundObject instanceof Schema)) {
            return;
        }

        final Schema schema = (Schema) foundObject;
        final List<String> collectionNames = listCollectionNames(snapshot);
        Collections.sort(collectionNames);
        for (String collectionName : collectionNames) {
            schema.addDatabaseObject(new Table().setName(collectionName).setSchema(schema));
        }
    }

    protected List<String> listCollectionNames(final DatabaseSnapshot snapshot) {
        return getMongoLiquibaseDatabase(snapshot).getMongoDatabase().listCollectionNames().into(new ArrayList<>());
    }

    protected Document getCollectionMetadata(final String collectionName, final DatabaseSnapshot snapshot) {
        return getMongoLiquibaseDatabase(snapshot).getMongoDatabase().listCollections(Document.class)
                .filter(Filters.eq("name", collectionName))
                .first();
    }

    private Schema resolveSchema(final Table tableExample, final DatabaseSnapshot snapshot) {
        if (tableExample.getSchema() != null) {
            return tableExample.getSchema();
        }

        final String currentCatalogName = getMongoLiquibaseDatabase(snapshot).getMongoDatabase().getName();
        return new Schema(currentCatalogName, currentCatalogName).setDefault(true);
    }
}
