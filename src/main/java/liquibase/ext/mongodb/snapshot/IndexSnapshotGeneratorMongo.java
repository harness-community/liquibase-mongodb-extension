package liquibase.ext.mongodb.snapshot;

import liquibase.diff.compare.DatabaseObjectComparatorFactory;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.Relation;
import liquibase.structure.core.Table;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class IndexSnapshotGeneratorMongo extends AbstractMongoSnapshotGenerator {

    public IndexSnapshotGeneratorMongo() {
        super(Index.class, new Class[]{Table.class});
    }

    @Override
    protected DatabaseObject snapshotObject(final DatabaseObject example, final DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        final Index exampleIndex = (Index) example;
        final Relation relation = exampleIndex.getRelation();
        if ((relation == null) || (relation.getName() == null)) {
            return null;
        }

        for (Document indexDocument : listIndexes(relation.getName(), snapshot)) {
            final Index index = buildIndex(indexDocument, relation);
            if (matches(exampleIndex, index, snapshot)) {
                return index;
            }
        }

        return null;
    }

    @Override
    protected void addTo(final DatabaseObject foundObject, final DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Index.class)
                || !snapshot.getDatabase().supports(Index.class)
                || !(foundObject instanceof Table)) {
            return;
        }

        final Table table = (Table) foundObject;
        for (Document indexDocument : listIndexes(table.getName(), snapshot)) {
            table.getIndexes().add(buildIndex(indexDocument, table));
        }
    }

    protected List<Document> listIndexes(final String collectionName, final DatabaseSnapshot snapshot) {
        final List<Document> indexes = loadIndexes(collectionName, snapshot);
        indexes.removeIf(indexDocument -> "_id_".equals(indexDocument.getString("name")));
        indexes.sort(Comparator.comparing(indexDocument -> indexDocument.getString("name"), String.CASE_INSENSITIVE_ORDER));
        return indexes;
    }

    protected List<Document> loadIndexes(final String collectionName, final DatabaseSnapshot snapshot) {
        return getMongoLiquibaseDatabase(snapshot).getMongoDatabase()
                .getCollection(collectionName)
                .listIndexes(Document.class)
                .into(new ArrayList<>());
    }

    private boolean matches(final Index exampleIndex, final Index candidate, final DatabaseSnapshot snapshot) {
        if (exampleIndex.getName() != null) {
            return exampleIndex.getName().equalsIgnoreCase(candidate.getName());
        }

        return DatabaseObjectComparatorFactory.getInstance().isSameObject(
                exampleIndex,
                candidate,
                snapshot.getSchemaComparisons(),
                snapshot.getDatabase()
        );
    }

    private Index buildIndex(final Document indexDocument, final Relation relation) {
        final Index index = new Index();
        index.setName(indexDocument.getString("name"));
        index.setRelation(relation);
        index.setUnique(indexDocument.getBoolean("unique", false));

        final Document keys = indexDocument.get("key", Document.class);
        if ((keys != null) && !keys.isEmpty()) {
            index.setAttribute(MongoSnapshotAttributes.INDEX_KEYS, keys.toJson());
            for (Map.Entry<String, Object> keyEntry : keys.entrySet()) {
                final Column column = new Column(keyEntry.getKey()).setRelation(relation);
                if (keyEntry.getValue() instanceof Number) {
                    final int direction = ((Number) keyEntry.getValue()).intValue();
                    if (direction == -1) {
                        column.setDescending(true);
                    } else if (direction == 1) {
                        column.setDescending(false);
                    }
                }
                index.addColumn(column);
            }
        }

        final Document options = new Document(indexDocument);
        options.remove("key");
        options.remove("ns");
        options.remove("v");
        if (!options.isEmpty()) {
            index.setAttribute(MongoSnapshotAttributes.INDEX_OPTIONS, options.toJson());
        }

        return index;
    }
}
