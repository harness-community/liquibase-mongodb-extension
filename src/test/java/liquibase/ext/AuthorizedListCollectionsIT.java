package liquibase.ext;

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

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationOptions;
import liquibase.database.DatabaseFactory;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.statement.AuthorizedListCollectionsStatement;
import lombok.SneakyThrows;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static liquibase.ext.mongodb.TestUtils.DB_CONNECTION_PATH;
import static liquibase.ext.mongodb.TestUtils.PROPERTY_FILE;
import static liquibase.ext.mongodb.TestUtils.loadProperty;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mocked pair of runCommand calls cannot catch the way this drain really breaks: the server rejects a
 * {@code getMore} carrying a different {@code lsid} than the one that opened the cursor, and bare
 * runCommand calls each borrow their own implicit session. It only shows up against a real server, with
 * enough collections to need a second batch, while something else uses the same client.
 */
class AuthorizedListCollectionsIT {

    private static final int COLLECTIONS = 150;
    /** listCollections batches are bounded by the 16MB BSON limit, so 150 x ~130KB of options needs a getMore. */
    private static final int VALIDATOR_PADDING = 130_000;
    private static final String PREFIX = "listCollectionsIt_";
    private static final int LOAD_THREADS = 16;

    private MongoLiquibaseDatabase database;
    private MongoDatabase mongoDatabase;

    @SneakyThrows
    @BeforeEach
    void setUpEach() {
        // The suite's connection string uses 100ms timeouts to fail fast; this test deliberately moves
        // ~20MB, so it opens its own connection with the driver defaults.
        final String url = loadProperty(PROPERTY_FILE, DB_CONNECTION_PATH).replaceAll("\\?.*$", "");
        database = (MongoLiquibaseDatabase) DatabaseFactory.getInstance().openDatabase(url, null, null, null, null);
        mongoDatabase = database.getMongoDatabase();
        mongoDatabase.drop();

        final Document padded = new Document("$jsonSchema", new Document("bsonType", "object")
                .append("description", new String(new char[VALIDATOR_PADDING]).replace('\0', 'x'))
                .append("properties", new Document("a", new Document("bsonType", "string"))));
        for (int i = 0; i < COLLECTIONS; i++) {
            mongoDatabase.createCollection(PREFIX + i, new CreateCollectionOptions()
                    .validationOptions(new ValidationOptions().validator(padded)));
        }
    }

    @SneakyThrows
    @AfterEach
    void tearDownEach() {
        mongoDatabase.drop();
        database.close();
    }

    @SneakyThrows
    @Test
    void drainsEveryBatchWhileTheSameClientIsBusyElsewhere() {
        final Document cursor = mongoDatabase.runCommand(new Document("listCollections", 1)
                .append("filter", new Document())
                .append("authorizedCollections", true)).get("cursor", Document.class);
        // Guards the fixture itself: if one batch held everything, the getMore path would go untested.
        assertThat(cursor.getList("firstBatch", Document.class)).hasSizeLessThan(COLLECTIONS);
        assertThat(cursor.getLong("id")).isNotZero();

        final AtomicBoolean stop = new AtomicBoolean(false);
        final ExecutorService pool = Executors.newFixedThreadPool(LOAD_THREADS);
        final CountDownLatch hot = new CountDownLatch(LOAD_THREADS);
        final List<Document> drained;
        try {
            for (int i = 0; i < LOAD_THREADS; i++) {
                pool.submit(() -> {
                    mongoDatabase.runCommand(new Document("ping", 1));
                    hot.countDown();
                    while (!stop.get()) {
                        mongoDatabase.runCommand(new Document("ping", 1));
                        mongoDatabase.getCollection(PREFIX + "0").countDocuments();
                    }
                });
            }
            // Do not start draining until every loader is already churning the implicit session pool.
            assertThat(hot.await(30, TimeUnit.SECONDS)).isTrue();
            drained = new AuthorizedListCollectionsStatement().queryForList(database);
        } finally {
            stop.set(true);
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        final List<String> names = drained.stream().map(d -> d.getString("name")).collect(Collectors.toList());
        assertThat(names).containsAll(IntStream.range(0, COLLECTIONS)
                .mapToObj(i -> PREFIX + i).collect(Collectors.toList()));
    }

    @SneakyThrows
    @Test
    void drainedListingCarriesOptionsForEveryCollectionAcrossBatches() {
        final List<Document> drained = new AuthorizedListCollectionsStatement().queryForList(database);

        final List<String> missingOptions = new ArrayList<>();
        for (Document collectionInfo : drained) {
            final String name = collectionInfo.getString("name");
            if (name != null && name.startsWith(PREFIX)
                    && collectionInfo.get("options", new Document()).get("validator") == null) {
                missingOptions.add(name);
            }
        }
        // Options are the whole reason this statement does not use nameOnly, and the reason a batch fills up.
        assertThat(missingOptions).isEmpty();
        assertThat(drained).hasSizeGreaterThanOrEqualTo(COLLECTIONS);
    }
}
