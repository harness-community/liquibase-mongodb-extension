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
import liquibase.diff.output.DiffOutputControl;
import liquibase.ext.mongodb.change.CreateCollectionChange;
import liquibase.ext.mongodb.structure.Collection;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingCollectionChangeGeneratorTest {

    private final MissingCollectionChangeGenerator generator = new MissingCollectionChangeGenerator();

    @Test
    void fixMissingWithNoOptionsLeavesOptionsUnset() {
        final Collection collection = new Collection("orders", null);

        final Change[] changes = generator.fixMissing(collection, new DiffOutputControl(), null, null, null);

        assertThat(changes).hasSize(1);
        final CreateCollectionChange change = (CreateCollectionChange) changes[0];
        assertThat(change.getCollectionName()).isEqualTo("orders");
        assertThat(change.getOptions()).isNull();
    }

    @Test
    void fixMissingWithEmptyOptionsLeavesOptionsUnset() {
        final Collection collection = new Collection("orders", null);
        collection.setOptions(new Document());

        final Change[] changes = generator.fixMissing(collection, new DiffOutputControl(), null, null, null);

        final CreateCollectionChange change = (CreateCollectionChange) changes[0];
        assertThat(change.getOptions()).isNull();
    }

    @Test
    void fixMissingWithOptionsPreservesValidator() {
        final Collection collection = new Collection("orders", null);
        collection.setOptions(Document.parse("{\"validator\": {\"$jsonSchema\": {\"required\": [\"a\"]}}}"));

        final Change[] changes = generator.fixMissing(collection, new DiffOutputControl(), null, null, null);

        final CreateCollectionChange change = (CreateCollectionChange) changes[0];
        assertThat(change.getOptions()).contains("validator").contains("jsonSchema");
    }
}
