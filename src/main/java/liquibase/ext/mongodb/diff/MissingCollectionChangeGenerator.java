package liquibase.ext.mongodb.diff;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.AbstractChangeGenerator;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.diff.output.changelog.MissingObjectChangeGenerator;
import liquibase.ext.mongodb.change.CreateCollectionChange;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.structure.DatabaseObject;
import org.bson.Document;

public class MissingCollectionChangeGenerator extends AbstractChangeGenerator implements MissingObjectChangeGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (Collection.class.isAssignableFrom(objectType)) {
            return PRIORITY_DEFAULT;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] runAfterTypes() {
        return null;
    }

    @Override
    public Class<? extends DatabaseObject>[] runBeforeTypes() {
        return null;
    }

    @Override
    public Change[] fixMissing(DatabaseObject missingObject, DiffOutputControl control, Database referenceDatabase, Database comparisonDatabase, ChangeGeneratorChain chain) {
        final Collection missingCollection = (Collection) missingObject;

        final CreateCollectionChange change = new CreateCollectionChange();
        change.setCollectionName(missingCollection.getName());
        final Document options = missingCollection.getOptions();
        change.setOptions((options == null ? new Document() : options).toJson());

        return new Change[]{change};
    }
}
