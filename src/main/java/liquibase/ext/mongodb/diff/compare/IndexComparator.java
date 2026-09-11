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

import liquibase.database.Database;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.compare.DatabaseObjectComparator;
import liquibase.diff.compare.DatabaseObjectComparatorChain;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import liquibase.structure.DatabaseObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Identity for {@link Index}: Mongo default index names ({@code email_1}) are reused across collections.
 * Core's {@code IndexComparator} only applies to {@code liquibase.structure.core.Index} (name + table);
 * the default comparator is name + schema, and Mongo {@link Index#getSchema()} is the database, not
 * the collection — so {@code users.email_1} and {@code orders.email_1} would collapse without this.
 */
public class IndexComparator implements DatabaseObjectComparator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (Index.class.isAssignableFrom(objectType)) {
            return PRIORITY_TYPE;
        }
        return PRIORITY_NONE;
    }

    @Override
    public String[] hash(DatabaseObject databaseObject, Database accordingTo, DatabaseObjectComparatorChain chain) {
        final List<String> hashes = new ArrayList<>();
        if (databaseObject.getName() != null) {
            hashes.add(databaseObject.getName());
        }
        final Collection collection = ((Index) databaseObject).getCollection();
        if (collection != null && collection.getName() != null) {
            hashes.add(collection.getName());
        }
        return hashes.toArray(new String[0]);
    }

    @Override
    public boolean isSameObject(DatabaseObject databaseObject1, DatabaseObject databaseObject2, Database accordingTo,
                                DatabaseObjectComparatorChain chain) {
        if (!(databaseObject1 instanceof Index) || !(databaseObject2 instanceof Index)) {
            return false;
        }
        final Index thisIndex = (Index) databaseObject1;
        final Index otherIndex = (Index) databaseObject2;
        if (thisIndex.getName() == null || !thisIndex.getName().equals(otherIndex.getName())) {
            return false;
        }
        final Collection thisCollection = thisIndex.getCollection();
        final Collection otherCollection = otherIndex.getCollection();
        if (thisCollection == null || otherCollection == null) {
            return thisCollection == otherCollection;
        }
        return Objects.equals(thisCollection.getName(), otherCollection.getName());
    }

    @Override
    public ObjectDifferences findDifferences(DatabaseObject databaseObject1, DatabaseObject databaseObject2,
                                             Database accordingTo, CompareControl compareControl,
                                             DatabaseObjectComparatorChain chain, Set<String> exclude) {
        exclude.add("schema");
        exclude.add("collection");
        return chain.findDifferences(databaseObject1, databaseObject2, accordingTo, compareControl, exclude);
    }
}
