package liquibase.ext.mongodb.diff;

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

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.AbstractChangeGenerator;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.diff.output.changelog.MissingObjectChangeGenerator;
import liquibase.ext.mongodb.change.CreateIndexChange;
import liquibase.ext.mongodb.structure.Index;
import liquibase.structure.DatabaseObject;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MissingIndexChangeGenerator extends AbstractChangeGenerator implements MissingObjectChangeGenerator {

    // Fields accepted by MongoDB's createIndexes command (besides key/name, handled separately below).
    // Includes legacy 2d-index options (bits/min/max) and the newer prepareUnique option.
    // Excludes internal listIndexes metadata such as v, ns, textIndexVersion, 2dsphereIndexVersion, background.
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
        change.setKeys((keys == null ? new Document() : keys).toJson());
        change.setOptions(options.toJson());

        return new Change[]{change};
    }
}
