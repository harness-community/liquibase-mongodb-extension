package liquibase.ext.mongodb.diff.compare;

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

import liquibase.diff.compare.DatabaseObjectComparator;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexComparatorTest {

    private final IndexComparator comparator = new IndexComparator();
    private final Collection users = new Collection("users", null);
    private final Collection orders = new Collection("orders", null);

    @Test
    void getPriorityIsTypeForMongoIndex() {
        assertThat(comparator.getPriority(Index.class, null)).isEqualTo(DatabaseObjectComparator.PRIORITY_TYPE);
        assertThat(comparator.getPriority(Collection.class, null)).isEqualTo(DatabaseObjectComparator.PRIORITY_NONE);
        assertThat(comparator.getPriority(liquibase.structure.core.Index.class, null))
                .isEqualTo(DatabaseObjectComparator.PRIORITY_NONE);
    }

    @Test
    void sameNameOnDifferentCollectionsAreDistinct() {
        final Index usersEmail = new Index("email_1", users);
        final Index ordersEmail = new Index("email_1", orders);

        assertThat(comparator.isSameObject(usersEmail, ordersEmail, null, null)).isFalse();
        assertThat(comparator.hash(usersEmail, null, null)).isNotEqualTo(comparator.hash(ordersEmail, null, null));
    }

    @Test
    void sameNameOnSameCollectionIsTheSameObject() {
        final Index first = new Index("email_1", users);
        final Index second = new Index("email_1", new Collection("users", null));

        assertThat(comparator.isSameObject(first, second, null, null)).isTrue();
    }

    @Test
    void nameMatchIsCaseSensitive() {
        final Index lower = new Index("email_1", users);
        final Index mixed = new Index("Email_1", users);

        assertThat(comparator.isSameObject(lower, mixed, null, null)).isFalse();
    }

    @Test
    void toStringIncludesCollectionSoTreeSetDoesNotCollapseSharedNames() {
        assertThat(new Index("email_1", users).toString()).isEqualTo("users.email_1");
        assertThat(new Index("email_1", orders).toString()).isEqualTo("orders.email_1");
        assertThat(new Index("email_1", users).toString())
                .isNotEqualTo(new Index("email_1", orders).toString());
    }
}
