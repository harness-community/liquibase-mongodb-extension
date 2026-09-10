package liquibase.ext;

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

import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ValidationOptions;
import liquibase.Liquibase;
import liquibase.command.CommandScope;
import liquibase.command.core.GenerateChangelogCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.resource.SearchPathResourceAccessor;
import lombok.SneakyThrows;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static liquibase.ext.mongodb.TestUtils.DB_CONNECTION_PATH;
import static liquibase.ext.mongodb.TestUtils.PROPERTY_FILE;
import static liquibase.ext.mongodb.TestUtils.loadProperty;
import static org.assertj.core.api.Assertions.assertThat;

class MongoGenerateChangelogIT extends AbstractMongoIntegrationTest {

    @TempDir
    Path tempDir;

    @SneakyThrows
    @Test
    void generateChangelogProducesCollectionsAndIndexesButSkipsViews() {
        mongoDatabase.createCollection("orders", new CreateCollectionOptions()
                .validationOptions(new ValidationOptions()
                        .validator(Document.parse("{\"$jsonSchema\": {\"required\": [\"customerId\"]}}"))));
        mongoDatabase.getCollection("orders")
                .createIndex(Indexes.compoundIndex(Indexes.ascending("a"), Indexes.descending("b")),
                        new IndexOptions().name("ab_unique").unique(true));
        mongoDatabase.createView("ordersView", "orders", java.util.Collections.emptyList());

        final Path changelogFile = tempDir.resolve("generated-changelog.yaml");
        runGenerateChangelog(changelogFile);

        final String content = Files.readString(changelogFile);

        assertThat(content)
                .contains("createCollection")
                .contains("orders")
                .contains("customerId")
                .contains("createIndex")
                .contains("ab_unique")
                .doesNotContain("ordersView")
                .doesNotContain("DATABASECHANGELOG");

        final int aIndex = content.indexOf("\"a\"");
        final int bIndex = content.indexOf("\"b\"");
        assertThat(aIndex).isGreaterThan(-1);
        assertThat(bIndex).isGreaterThan(aIndex);
    }

    @SneakyThrows
    @Test
    void generatedChangelogCanBeReplayedWithUpdate() {
        mongoDatabase.createCollection("orders", new CreateCollectionOptions()
                .validationOptions(new ValidationOptions()
                        .validator(Document.parse("{\"$jsonSchema\": {\"required\": [\"customerId\"]}}"))));
        mongoDatabase.getCollection("orders")
                .createIndex(Indexes.compoundIndex(Indexes.ascending("a"), Indexes.descending("b")),
                        new IndexOptions().name("ab_unique").unique(true));

        final Path changelogFile = tempDir.resolve("replay-changelog.yaml");
        runGenerateChangelog(changelogFile);

        mongoDatabase.getCollection("orders").drop();
        assertThat(mongoDatabase.listCollectionNames()).doesNotContain("orders");

        final Liquibase liquibase = new Liquibase(changelogFile.getFileName().toString(),
                new SearchPathResourceAccessor(tempDir.toString()), database);
        liquibase.update();

        assertThat(mongoDatabase.listCollectionNames()).contains("orders");

        Document ordersInfo = null;
        for (Document collectionInfo : mongoDatabase.listCollections().filter(Document.parse("{\"name\": \"orders\"}"))) {
            ordersInfo = collectionInfo;
        }
        assertThat(ordersInfo).isNotNull();
        final Document validator = ordersInfo.get("options", Document.class).get("validator", Document.class);
        assertThat(validator).isNotNull();
        assertThat(validator.toJson()).contains("customerId");

        Document abUniqueIndex = null;
        for (Document indexInfo : mongoDatabase.getCollection("orders").listIndexes()) {
            if ("ab_unique".equals(indexInfo.getString("name"))) {
                abUniqueIndex = indexInfo;
            }
        }
        assertThat(abUniqueIndex).isNotNull();
        assertThat(Boolean.TRUE.equals(abUniqueIndex.getBoolean("unique"))).isTrue();
        assertThat(abUniqueIndex.get("key", Document.class).toJson()).isEqualTo("{\"a\": 1, \"b\": -1}");
    }

    @SneakyThrows
    private void runGenerateChangelog(Path changelogFile) {
        final String url = loadProperty(PROPERTY_FILE, DB_CONNECTION_PATH);
        final CommandScope commandScope = new CommandScope(GenerateChangelogCommandStep.COMMAND_NAME);
        commandScope.addArgumentValue(DbUrlConnectionArgumentsCommandStep.URL_ARG, url);
        commandScope.addArgumentValue(GenerateChangelogCommandStep.CHANGELOG_FILE_ARG, changelogFile.toString());
        commandScope.addArgumentValue(GenerateChangelogCommandStep.OVERWRITE_OUTPUT_FILE_ARG, true);
        commandScope.addArgumentValue(GenerateChangelogCommandStep.AUTHOR_ARG, "test");
        commandScope.execute();
    }
}
