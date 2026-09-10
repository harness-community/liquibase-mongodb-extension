package liquibase.ext.mongodb.structure;

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

import liquibase.structure.AbstractDatabaseObject;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import org.bson.Document;

public class Index extends AbstractDatabaseObject {

    public Index() {
    }

    public Index(String name, Collection collection) {
        setName(name);
        setCollection(collection);
    }

    @Override
    public String getName() {
        return getAttribute("name", String.class);
    }

    @Override
    public Index setName(String name) {
        setAttribute("name", name);
        return this;
    }

    @Override
    public Schema getSchema() {
        return getCollection() == null ? null : getCollection().getSchema();
    }

    public Collection getCollection() {
        return getAttribute("collection", Collection.class);
    }

    public Index setCollection(Collection collection) {
        setAttribute("collection", collection);
        return this;
    }

    public Document getKeys() {
        final String json = getAttribute("keys", String.class);
        return json == null ? null : Document.parse(json);
    }

    public Index setKeys(Document keys) {
        // Stored as JSON rather than a Document (a Map) because DatabaseSnapshot.replaceObject()
        // rebuilds any Map-typed attribute via a HashSet, scrambling compound-index field order.
        setAttribute("keys", keys == null ? null : keys.toJson());
        return this;
    }

    public Document getIndexInfo() {
        final String json = getAttribute("indexInfo", String.class);
        return json == null ? null : Document.parse(json);
    }

    public Index setIndexInfo(Document indexInfo) {
        // Stored as JSON rather than a Document (a Map) because DatabaseSnapshot.replaceObject()
        // rebuilds any Map-typed attribute via a HashSet, scrambling field order.
        setAttribute("indexInfo", indexInfo == null ? null : indexInfo.toJson());
        return this;
    }

    public boolean isUnique() {
        return Boolean.TRUE.equals(getAttribute("unique", Boolean.class));
    }

    public Index setUnique(boolean unique) {
        setAttribute("unique", unique);
        return this;
    }

    @Override
    public DatabaseObject[] getContainingObjects() {
        return new DatabaseObject[]{getCollection()};
    }
}
