package liquibase.nosql.snapshot;

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

import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import static liquibase.plugin.Plugin.PRIORITY_SPECIALIZED;
import static liquibase.snapshot.SnapshotGenerator.PRIORITY_NONE;
import static org.assertj.core.api.Assertions.assertThat;

class NoSqlSnapshotGeneratorTest {

    private final NoSqlSnapshotGenerator generator = new NoSqlSnapshotGenerator();
    private final MongoLiquibaseDatabase database = new MongoLiquibaseDatabase();

    @Test
    void getPriorityIsNoneForNativelySupportedTypes() {
        assertThat(generator.getPriority(Catalog.class, database)).isEqualTo(PRIORITY_NONE);
        assertThat(generator.getPriority(Schema.class, database)).isEqualTo(PRIORITY_NONE);
        assertThat(generator.getPriority(Collection.class, database)).isEqualTo(PRIORITY_NONE);
        assertThat(generator.getPriority(Index.class, database)).isEqualTo(PRIORITY_NONE);
    }

    @Test
    void getPriorityIsSpecializedForJdbcOnlyTypes() {
        assertThat(generator.getPriority(Table.class, database)).isEqualTo(PRIORITY_SPECIALIZED);
        assertThat(generator.getPriority(liquibase.structure.core.Index.class, database)).isEqualTo(PRIORITY_SPECIALIZED);
    }

    @Test
    void snapshotOfCoreIndexReturnsNullInsteadOfThrowing() throws Exception {
        assertThat(generator.snapshot(new liquibase.structure.core.Index(), null, null)).isNull();
    }

    @Test
    void snapshotOfJdbcOnlyTypeThrows() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> generator.snapshot(new Table(), null, null))
                .isInstanceOf(liquibase.exception.DatabaseException.class);
    }
}
