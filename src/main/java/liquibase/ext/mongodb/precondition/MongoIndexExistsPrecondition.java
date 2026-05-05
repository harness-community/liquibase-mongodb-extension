package liquibase.ext.mongodb.precondition;

import com.mongodb.client.MongoCollection;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.visitor.ChangeExecListener;
import liquibase.database.Database;
import liquibase.exception.PreconditionErrorException;
import liquibase.exception.PreconditionFailedException;
import liquibase.exception.ValidationErrors;
import liquibase.exception.Warnings;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.statement.CountCollectionByNameStatement;
import liquibase.precondition.AbstractPrecondition;
import liquibase.util.StringUtil;
import lombok.Getter;
import lombok.Setter;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

public class MongoIndexExistsPrecondition extends AbstractPrecondition {

    @Getter
    @Setter
    private String collectionName;

    @Getter
    @Setter
    private String indexName;

    @Override
    public String getName() {
        return "mongoIndexExists";
    }

    @Override
    public Warnings warn(final Database database) {
        return new Warnings();
    }

    @Override
    public ValidationErrors validate(final Database database) {
        final ValidationErrors errors = new ValidationErrors();
        if (StringUtil.isEmpty(collectionName)) {
            errors.addError("collectionName is required");
        }
        if (StringUtil.isEmpty(indexName)) {
            errors.addError("indexName is required");
        }
        return errors;
    }

    @Override
    public void check(final Database database,
                      final DatabaseChangeLog changeLog,
                      final ChangeSet changeSet,
                      final ChangeExecListener changeExecListener) throws PreconditionFailedException, PreconditionErrorException {
        try {
            final MongoLiquibaseDatabase mongoDatabase = (MongoLiquibaseDatabase) database;

            final CountCollectionByNameStatement countCollectionByNameStatement = new CountCollectionByNameStatement(collectionName);
            if (countCollectionByNameStatement.queryForLong(mongoDatabase) == 0L) {
                throw new PreconditionFailedException(format("Collection %s does not exist", collectionName), changeLog, this);
            }

            final MongoCollection<Document> collection = mongoDatabase.getMongoDatabase().getCollection(collectionName);
            final List<Document> indexes = new ArrayList<>();
            collection.listIndexes().into(indexes);

            final boolean found = indexes.stream()
                    .map(d -> d.getString("name"))
                    .anyMatch(indexName::equals);

            if (!found) {
                throw new PreconditionFailedException(
                        format("Index %s does not exist in collection %s", indexName, collectionName),
                        changeLog,
                        this
                );
            }
        } catch (final PreconditionFailedException e) {
            throw e;
        } catch (final Exception e) {
            throw new PreconditionErrorException(e, changeLog, this);
        }
    }

    @Override
    public String getSerializedObjectNamespace() {
        return GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
    }
}

