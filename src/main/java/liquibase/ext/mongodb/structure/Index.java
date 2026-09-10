package liquibase.ext.mongodb.structure;

import liquibase.structure.AbstractDatabaseObject;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import org.bson.Document;

public class Index extends AbstractDatabaseObject {

    public Index() {
    }

    public Index(String name, Collection collection) {
        setName(name);
        setCollection(collection);
    }

    @Override
    public String getName() {
        return getAttribute("name", String.class);
    }

    @Override
    public Index setName(String name) {
        setAttribute("name", name);
        return this;
    }

    @Override
    public Schema getSchema() {
        return getCollection() == null ? null : getCollection().getSchema();
    }

    public Collection getCollection() {
        return getAttribute("collection", Collection.class);
    }

    public Index setCollection(Collection collection) {
        setAttribute("collection", collection);
        return this;
    }

    public Document getKeys() {
        final String json = getAttribute("keys", String.class);
        return json == null ? null : Document.parse(json);
    }

    public Index setKeys(Document keys) {
        // Stored as JSON rather than a Document (a Map) because DatabaseSnapshot.replaceObject()
        // rebuilds any Map-typed attribute via a HashSet, scrambling compound-index field order.
        setAttribute("keys", keys == null ? null : keys.toJson());
        return this;
    }

    public Document getIndexInfo() {
        final String json = getAttribute("indexInfo", String.class);
        return json == null ? null : Document.parse(json);
    }

    public Index setIndexInfo(Document indexInfo) {
        // Stored as JSON rather than a Document (a Map) because DatabaseSnapshot.replaceObject()
        // rebuilds any Map-typed attribute via a HashSet, scrambling field order.
        setAttribute("indexInfo", indexInfo == null ? null : indexInfo.toJson());
        return this;
    }

    public boolean isUnique() {
        return Boolean.TRUE.equals(getAttribute("unique", Boolean.class));
    }

    public Index setUnique(boolean unique) {
        setAttribute("unique", unique);
        return this;
    }

    @Override
    public DatabaseObject[] getContainingObjects() {
        return new DatabaseObject[]{getCollection()};
    }
}
