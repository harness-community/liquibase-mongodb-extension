package liquibase.ext.mongodb.diff;

/*-
 * #%L
 * Liquibase MongoDB Extension
 * %%
 * Copyright (C) 2026 Mastercard
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

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.AbstractChangeGenerator;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.diff.output.changelog.MissingObjectChangeGenerator;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.ext.mongodb.change.CreateIndexChange;
import liquibase.ext.mongodb.structure.Index;
import liquibase.structure.DatabaseObject;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * generate-changelog / diff lifecycle: each missing {@link Index} becomes a {@link CreateIndexChange}.
 * {@link #runAfterTypes()} puts these after createCollection so replay creates the collection first.
 */
public class MissingIndexChangeGenerator extends AbstractChangeGenerator implements MissingObjectChangeGenerator {

    // createIndexes-accepted options (plus name, always set below). Started from the workaround script's
    // denylist (key/v/ns) and switched to a whitelist so listIndexes internals — textIndexVersion,
    // 2dsphereIndexVersion, background — are not copied into a changelog that cannot replay.
    // See https://www.mongodb.com/docs/manual/reference/command/createIndexes/
    private static final Set<String> INDEX_OPTION_KEYS = new HashSet<>(Arrays.asList(
            "unique", "sparse", "expireAfterSeconds", "hidden", "partialFilterExpression",
            "collation", "wildcardProjection", "weights", "default_language", "language_override",
            "bits", "min", "max", "prepareUnique"));

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (Index.class.isAssignableFrom(objectType)) {
            return PRIORITY_DEFAULT;
        }
        return PRIORITY_NONE;
    }

    /**
     * Emit createIndex only after the parent collection's createCollection changeset.
     */
    @Override
    public Class<? extends DatabaseObject>[] runAfterTypes() {
        //noinspection unchecked
        return new Class[]{liquibase.ext.mongodb.structure.Collection.class};
    }

    @Override
    public Class<? extends DatabaseObject>[] runBeforeTypes() {
        return null;
    }

    @Override
    public Change[] fixMissing(DatabaseObject missingObject, DiffOutputControl control, Database referenceDatabase, Database comparisonDatabase, ChangeGeneratorChain chain) {
        final Index missingIndex = (Index) missingObject;
        final Document indexInfo = missingIndex.getIndexInfo();
        final Document keys = missingIndex.getKeys();
        if (keys == null || keys.isEmpty()) {
            final String collectionName = missingIndex.getCollection() == null
                    ? "<unknown>" : missingIndex.getCollection().getName();
            throw new UnexpectedLiquibaseException(
                    "Cannot generate createIndex for '" + missingIndex.getName()
                            + "' on collection '" + collectionName + "': index keys are missing");
        }

        final Document options = new Document();
        if (indexInfo != null) {
            for (Map.Entry<String, Object> entry : indexInfo.entrySet()) {
                if (INDEX_OPTION_KEYS.contains(entry.getKey())) {
                    options.put(entry.getKey(), entry.getValue());
                }
            }
        }
        options.put("name", missingIndex.getName());

        final CreateIndexChange change = new CreateIndexChange();
        change.setCollectionName(missingIndex.getCollection().getName());
        change.setKeys(keys.toJson());
        change.setOptions(options.toJson());

        return new Change[]{change};
    }
}
