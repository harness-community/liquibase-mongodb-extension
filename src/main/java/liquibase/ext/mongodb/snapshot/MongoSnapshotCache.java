package liquibase.ext.mongodb.snapshot;

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

import liquibase.snapshot.DatabaseSnapshot;
import lombok.Getter;
import lombok.Setter;
import org.bson.Document;

import java.util.List;
import java.util.Map;

/**
 * Per-run cache, scoped to one {@link DatabaseSnapshot}, of the collections {@link CollectionSnapshotGenerator}
 * lists onto the Schema, keyed by name. Each entry carries the collection's indexes when they were fetched
 * eagerly alongside the listing, so {@link IndexSnapshotGenerator} does not re-list them, and core
 * re-snapshotting each Collection/Index example afterward reads the same cache instead of round-tripping
 * to Mongo again.
 */
final class MongoSnapshotCache {

    private static final String SCRATCH_KEY = "liquibase.ext.mongodb.snapshot.collectionsByName";

    private MongoSnapshotCache() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, CachedCollection> get(final DatabaseSnapshot snapshot) {
        return (Map<String, CachedCollection>) snapshot.getScratchData(SCRATCH_KEY);
    }

    static void put(final DatabaseSnapshot snapshot, final Map<String, CachedCollection> collectionsByName) {
        snapshot.setScratchData(SCRATCH_KEY, collectionsByName);
    }

    /** One listCollections entry, kept as the server returned it, plus its indexes when they were fetched. */
    @Getter
    static final class CachedCollection {

        private final Document info;

        /** {@code null} when indexes were never fetched, as opposed to a collection that has none. */
        @Setter
        private List<Document> indexes;

        CachedCollection(final Document info, final List<Document> indexes) {
            this.info = info;
            this.indexes = indexes;
        }
    }
}
