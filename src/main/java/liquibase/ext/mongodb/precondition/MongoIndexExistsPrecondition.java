package liquibase.ext.mongodb.precondition;

/*-
 * #%L
 * Liquibase MongoDB Extension
 * %%
 * Copyright (C) 2019 Mastercard
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
    public Warnings warn(Database database) {
        return new Warnings();
    }

    @Override
    public ValidationErrors validate(Database database) {
        ValidationErrors errors = new ValidationErrors();
        if (isBlank(collectionName)) {
            errors.addError("collectionName is required");
        }
        if (isBlank(indexName)) {
            errors.addError("indexName is required");
        }
        return errors;
    }

    @Override
    public void check(Database database, DatabaseChangeLog changeLog, ChangeSet changeSet,
                      ChangeExecListener changeExecListener)
            throws PreconditionFailedException, PreconditionErrorException {
        try {
            MongoLiquibaseDatabase mongoDatabase = (MongoLiquibaseDatabase) database;

            CountCollectionByNameStatement countCollectionByNameStatement =
                    new CountCollectionByNameStatement(collectionName);
            if (countCollectionByNameStatement.queryForLong(mongoDatabase) == 0L) {
                throw new PreconditionFailedException(format("Collection %s does not exist", collectionName), changeLog, this);
            }

            MongoCollection<Document> collection = mongoDatabase.getMongoDatabase().getCollection(collectionName);
            List<Document> indexes = new ArrayList<>();
            collection.listIndexes().into(indexes);

            boolean found = indexes.stream()
                    .map(d -> d.getString("name"))
                    .anyMatch(indexName::equals);
            if (!found) {
                throw new PreconditionFailedException(
                        format("Index %s does not exist in collection %s", indexName, collectionName),
                        changeLog,
                        this
                );
            }
        } catch (PreconditionFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new PreconditionErrorException(e, changeLog, this);
        }
    }

    @Override
    public String getSerializedObjectNamespace() {
        return GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
