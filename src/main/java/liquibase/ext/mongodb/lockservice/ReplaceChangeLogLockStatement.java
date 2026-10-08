package liquibase.ext.mongodb.lockservice;

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

import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.statement.AbstractCollectionStatement;
import liquibase.nosql.statement.NoSqlUpdateStatement;
import lombok.Getter;
import org.bson.conversions.Bson;

import java.util.Date;

import static liquibase.ext.mongodb.statement.AbstractRunCommandStatement.SHELL_DB_PREFIX;

@Getter
public class ReplaceChangeLogLockStatement extends AbstractCollectionStatement
        implements NoSqlUpdateStatement<MongoLiquibaseDatabase> {

    public static final String COMMAND_NAME = "updateLock";
    private static final Integer DUPLICATE_KEY_ERROR_CODE = 11000;

    private final boolean locked;
    /**
     * When non-null and {@link #locked} is true, acquire by matching this exact {@code lockedBy}
     * (Cassandra/Bigtable-style steal branch). Null means free-row acquire only ({@code locked=false}).
     */
    private final String stealPreviousLockedBy;
    /**
     * When unlocking ({@link #locked} false), skip host ownership check — used by {@code release-locks}
     * / {@code forceReleaseLock}, matching JDBC and Cassandra unconditional clear.
     */
    private final boolean forceUnlock;

    public ReplaceChangeLogLockStatement(String collectionName, boolean locked) {
        this(collectionName, locked, null, false);
    }

    public ReplaceChangeLogLockStatement(String collectionName, boolean locked, String stealPreviousLockedBy) {
        this(collectionName, locked, stealPreviousLockedBy, false);
    }

    public ReplaceChangeLogLockStatement(
            String collectionName, boolean locked, String stealPreviousLockedBy, boolean forceUnlock) {
        super(collectionName);
        this.locked = locked;
        this.stealPreviousLockedBy = stealPreviousLockedBy;
        this.forceUnlock = forceUnlock;
    }

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String toJs() {
        return SHELL_DB_PREFIX +
                getCollectionName() +
                "." +
                getCommandName() +
                "(" +
                locked +
                ");";
    }

    @Override
    public int update(final MongoLiquibaseDatabase database) {
        final String ownerForDoc = (!locked && forceUnlock) ? "" : MongoChangeLogLock.formLockedBy();
        final MongoChangeLogLock lock = new MongoChangeLogLock(
                1,
                new Date(),
                ownerForDoc,
                locked
        );
        if (this.locked) {
            if (stealPreviousLockedBy != null) {
                // Exact previous owner only (Cassandra/Bigtable branch — not free OR steal)
                Bson stealFilter = Filters.and(
                        Filters.eq(MongoChangeLogLock.Fields.id, lock.getId()),
                        Filters.eq(MongoChangeLogLock.Fields.locked, true),
                        Filters.eq(MongoChangeLogLock.Fields.lockedBy, stealPreviousLockedBy)
                );
                return this.update(database, stealFilter, lock);
            }
            Bson freeFilter = Filters.and(
                    Filters.eq(MongoChangeLogLock.Fields.id, lock.getId()),
                    Filters.eq(MongoChangeLogLock.Fields.locked, false)
            );
            return this.update(database, freeFilter, lock);
        }
        if (forceUnlock) {
            // Unconditional clear of ID=1 — same semantics as JDBC/Cassandra release-locks
            return this.update(database, Filters.eq(MongoChangeLogLock.Fields.id, lock.getId()), lock);
        }
        return this.update(
                database,
                Filters.and(
                        Filters.eq(MongoChangeLogLock.Fields.id, lock.getId()),
                        Filters.eq(MongoChangeLogLock.Fields.locked, true),
                        Filters.eq(MongoChangeLogLock.Fields.lockedBy, lock.getLockedBy())
                ),
                lock
        );
    }

    private int update(final MongoLiquibaseDatabase database, final Bson filters, final MongoChangeLogLock lock) {
        try {
            database.getMongoDatabase()
                    .getCollection(collectionName)
                    .findOneAndReplace(
                            filters,
                            new MongoChangeLogLockToDocumentConverter().toDocument(lock),
                            new FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
                    );
            return 1;
        } catch (MongoException e) {
            if (e.getCode() == DUPLICATE_KEY_ERROR_CODE) {
                return 0;
            }
            throw e;
        }
    }
}
