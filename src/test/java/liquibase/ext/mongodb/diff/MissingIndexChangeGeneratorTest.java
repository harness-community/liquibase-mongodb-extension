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
import liquibase.diff.output.DiffOutputControl;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.ext.mongodb.change.CreateIndexChange;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissingIndexChangeGeneratorTest {

    private final MissingIndexChangeGenerator generator = new MissingIndexChangeGenerator();
    private final Collection collection = new Collection("orders", null);

    @Test
    void fixMissingPreservesCompoundKeyOrder() {
        final Index index = new Index("ab_idx", collection)
                .setKeys(Document.parse("{\"a\": 1, \"b\": -1}"))
                .setIndexInfo(Document.parse("{\"v\": 2, \"key\": {\"a\": 1, \"b\": -1}, \"name\": \"ab_idx\", \"ns\": \"db.orders\"}"));

        final Change[] changes = generator.fixMissing(index, new DiffOutputControl(), null, null, null);

        assertThat(changes).hasSize(1);
        final CreateIndexChange change = (CreateIndexChange) changes[0];
        assertThat(change.getCollectionName()).isEqualTo("orders");
        assertThat(change.getKeys()).isEqualTo("{\"a\": 1, \"b\": -1}");
    }

    @Test
    void fixMissingExcludesInternalMetadataFields() {
        final Index index = new Index("ab_idx", collection)
                .setKeys(Document.parse("{\"a\": 1}"))
                .setIndexInfo(Document.parse(
                        "{\"v\": 2, \"key\": {\"a\": 1}, \"name\": \"ab_idx\", \"ns\": \"db.orders\", "
                                + "\"textIndexVersion\": 3, \"2dsphereIndexVersion\": 3, \"background\": true}"));

        final Change[] changes = generator.fixMissing(index, new DiffOutputControl(), null, null, null);

        final CreateIndexChange change = (CreateIndexChange) changes[0];
        assertThat(change.getOptions())
                .doesNotContain("\"v\"")
                .doesNotContain("\"key\"")
                .doesNotContain("\"ns\"")
                .doesNotContain("textIndexVersion")
                .doesNotContain("2dsphereIndexVersion")
                .doesNotContain("background")
                .contains("\"name\": \"ab_idx\"");
    }

    @Test
    void fixMissingIncludesUniqueAndName() {
        final Index index = new Index("email_unique", collection)
                .setKeys(Document.parse("{\"email\": 1}"))
                .setIndexInfo(Document.parse("{\"v\": 2, \"key\": {\"email\": 1}, \"name\": \"email_unique\", \"unique\": true}"));

        final Change[] changes = generator.fixMissing(index, new DiffOutputControl(), null, null, null);

        final CreateIndexChange change = (CreateIndexChange) changes[0];
        assertThat(change.getOptions())
                .contains("\"unique\": true")
                .contains("\"name\": \"email_unique\"");
    }

    @Test
    void fixMissingPreservesLegacyGeoAndPrepareUniqueOptions() {
        final Index index = new Index("loc_2d", collection)
                .setKeys(Document.parse("{\"loc\": \"2d\"}"))
                .setIndexInfo(Document.parse(
                        "{\"v\": 2, \"key\": {\"loc\": \"2d\"}, \"name\": \"loc_2d\", "
                                + "\"bits\": 26, \"min\": -180, \"max\": 180, \"prepareUnique\": true}"));

        final Change[] changes = generator.fixMissing(index, new DiffOutputControl(), null, null, null);

        final CreateIndexChange change = (CreateIndexChange) changes[0];
        assertThat(change.getOptions())
                .contains("\"bits\": 26")
                .contains("\"min\": -180")
                .contains("\"max\": 180")
                .contains("\"prepareUnique\": true");
    }

    @Test
    void fixMissingWithNullIndexInfoStillSetsName() {
        final Index index = new Index("plain_idx", collection)
                .setKeys(Document.parse("{\"a\": 1}"));

        final Change[] changes = generator.fixMissing(index, new DiffOutputControl(), null, null, null);

        final CreateIndexChange change = (CreateIndexChange) changes[0];
        assertThat(change.getOptions()).contains("\"name\": \"plain_idx\"");
    }

    @Test
    void fixMissingRejectsNullKeys() {
        final Index index = new Index("broken_idx", collection);

        assertThatThrownBy(() -> generator.fixMissing(index, new DiffOutputControl(), null, null, null))
                .isInstanceOf(UnexpectedLiquibaseException.class)
                .hasMessageContaining("broken_idx")
                .hasMessageContaining("orders")
                .hasMessageContaining("index keys are missing");
    }

    @Test
    void fixMissingRejectsEmptyKeys() {
        final Index index = new Index("empty_idx", collection).setKeys(new Document());

        assertThatThrownBy(() -> generator.fixMissing(index, new DiffOutputControl(), null, null, null))
                .isInstanceOf(UnexpectedLiquibaseException.class)
                .hasMessageContaining("empty_idx")
                .hasMessageContaining("index keys are missing");
    }
}
