package liquibase.ext.mongodb.structure;

import liquibase.structure.AbstractDatabaseObject;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import org.bson.Document;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Collection extends AbstractDatabaseObject {

    public Collection() {
    }

    public Collection(String name, Schema schema) {
        setName(name);
        setSchema(schema);
    }

    @Override
    public String getName() {
        return getAttribute("name", String.class);
    }

    @Override
    public Collection setName(String name) {
        setAttribute("name", name);
        return this;
    }

    @Override
    public Schema getSchema() {
        return getAttribute("schema", Schema.class);
    }

    public Collection setSchema(Schema schema) {
        setAttribute("schema", schema);
        return this;
    }

    public Document getOptions() {
        final String json = getAttribute("options", String.class);
        return json == null ? null : Document.parse(json);
    }

    public Collection setOptions(Document options) {
        // Stored as JSON rather than a Document (a Map) because DatabaseSnapshot.replaceObject()
        // rebuilds any Map-typed attribute via a HashSet, scrambling field order.
        setAttribute("options", options == null ? null : options.toJson());
        return this;
    }

    @Override
    public DatabaseObject[] getContainingObjects() {
        return new DatabaseObject[]{getSchema()};
    }

    public void addDatabaseObject(DatabaseObject object) {
        if (object == null) {
            return;
        }
        Map<Class<? extends DatabaseObject>, Set<DatabaseObject>> objects = getAttribute("objects", Map.class);
        if (objects == null) {
            objects = new HashMap<>();
            setAttribute("objects", objects);
        }
        objects.computeIfAbsent(object.getClass(), key -> new HashSet<>()).add(object);
    }

    public <T extends DatabaseObject> Set<T> getDatabaseObjects(Class<T> type) {
        final Map<Class<? extends DatabaseObject>, Set<DatabaseObject>> objects = getAttribute("objects", Map.class);
        if (objects == null || !objects.containsKey(type)) {
            return new HashSet<>();
        }
        //noinspection unchecked
        return (Set<T>) objects.get(type);
    }
}
