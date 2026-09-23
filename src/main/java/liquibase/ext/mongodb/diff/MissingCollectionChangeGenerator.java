package liquibase.ext.mongodb.diff;

/*-
 * #%L
 * Liquibase MongoDB Extension
 * %%
 * Copyright (C) 2026 Harness Inc.
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
import liquibase.ext.mongodb.change.CreateCollectionChange;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.structure.DatabaseObject;
import org.bson.Document;

/**
 * generate-changelog / diff lifecycle: after snapshot, core walks missing objects. For each missing
 * {@link Collection}, this emits the existing {@link CreateCollectionChange} (validator/options included
 * when present). Indexes are a separate missing object and are handled by {@link MissingIndexChangeGenerator}.
 */
public class MissingCollectionChangeGenerator extends AbstractChangeGenerator implements MissingObjectChangeGenerator {

    private static final String TIMESERIES = "timeseries";
    private static final String GRANULARITY = "granularity";
    private static final String BUCKET_MAX_SPAN_SECONDS = "bucketMaxSpanSeconds";
    private static final String BUCKET_ROUNDING_SECONDS = "bucketRoundingSeconds";
    private static final String CLUSTERED_INDEX = "clusteredIndex";
    private static final String INDEX_VERSION = "v";

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

    /** Emit one createCollection changeset; options JSON is omitted when empty. */
    @Override
    public Change[] fixMissing(DatabaseObject missingObject, DiffOutputControl control, Database referenceDatabase, Database comparisonDatabase, ChangeGeneratorChain chain) {
        final Collection missingCollection = (Collection) missingObject;

        final CreateCollectionChange change = new CreateCollectionChange();
        change.setCollectionName(missingCollection.getName());
        final Document options = stripDerivedOptions(missingCollection.getOptions());
        if (options != null && !options.isEmpty()) {
            change.setOptions(options.toJson());
        }

        return new Change[]{change};
    }

    /**
     * Unlike listIndexes, listCollections options replay as-is on 6.0/7.0/8.0, so these are removed rather
     * than whitelisted: a whitelist would silently drop valid options this extension does not know yet.
     * The bucket span pair is derived from granularity, and create rejects bucketRoundingSeconds alongside
     * it, so granularity is kept and the pair it implies is dropped. clusteredIndex.v is an index version
     * the server assigns.
     */
    private static Document stripDerivedOptions(final Document options) {
        if (options == null || options.isEmpty()) {
            return options;
        }
        final Document sanitized = new Document(options);

        final Object timeseries = options.get(TIMESERIES);
        if (timeseries instanceof Document && ((Document) timeseries).containsKey(GRANULARITY)) {
            final Document cleaned = new Document((Document) timeseries);
            cleaned.remove(BUCKET_MAX_SPAN_SECONDS);
            cleaned.remove(BUCKET_ROUNDING_SECONDS);
            sanitized.put(TIMESERIES, cleaned);
        }

        final Object clusteredIndex = options.get(CLUSTERED_INDEX);
        if (clusteredIndex instanceof Document) {
            final Document cleaned = new Document((Document) clusteredIndex);
            cleaned.remove(INDEX_VERSION);
            sanitized.put(CLUSTERED_INDEX, cleaned);
        }

        return sanitized;
    }
}
