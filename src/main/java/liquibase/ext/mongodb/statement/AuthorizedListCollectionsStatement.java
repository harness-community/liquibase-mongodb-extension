package liquibase.ext.mongodb.statement;

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

import com.mongodb.MongoClientException;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import liquibase.Scope;
import liquibase.database.DatabaseConnection;
import liquibase.ext.mongodb.database.MongoConnection;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.nosql.statement.NoSqlQueryForListStatement;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static liquibase.ext.mongodb.statement.BsonUtils.toCommand;

/**
 * Lists full collection info (including {@code options}, needed for generate-changelog) restricted to
 * collections the current user is authorized to see: {@code authorizedCollections: true} avoids an
 * authorization error when the caller lacks the {@code listCollections} privilege on the whole
 * database, instead returning just the subset it can see. Unlike {@link ListCollectionNamesStatement},
 * this drains every {@code getMore} batch instead of only {@code cursor.firstBatch}, so databases with
 * more collections than fit in one 16MB batch are not silently truncated.
 *
 * @see <a href="https://docs.mongodb.com/manual/reference/command/listCollections/">listCollections</a>
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class AuthorizedListCollectionsStatement extends AbstractRunCommandStatement
        implements NoSqlQueryForListStatement<MongoLiquibaseDatabase, Document> {

    static final String RUN_COMMAND_NAME = "listCollections";
    public static final String FILTER = "filter";
    public static final String AUTHORIZED_COLLECTIONS = "authorizedCollections";
    public static final String CURSOR = "cursor";
    public static final String FIRST_BATCH = "firstBatch";
    public static final String NEXT_BATCH = "nextBatch";
    public static final String CURSOR_ID = "id";
    public static final String CURSOR_NS = "ns";
    public static final String GET_MORE = "getMore";
    public static final String COLLECTION = "collection";
    public static final String KILL_CURSORS = "killCursors";
    public static final String CURSORS = "cursors";
    /** Fallback for {@code killCursors}/{@code getMore} when the server omits {@code cursor.ns}. */
    static final String LIST_COLLECTIONS_CURSOR = "$cmd.listCollections";

    /**
     * Create a listCollections statement with no filter, i.e. to return all authorized collections.
     */
    public AuthorizedListCollectionsStatement() {
        this(new Document());
    }

    /**
     * Create a listCollections statement with the supplied filter.
     *
     * @param filter the filter to apply
     */
    public AuthorizedListCollectionsStatement(final Document filter) {
        super(toCommand(RUN_COMMAND_NAME, 1, combine(filter)));
    }

    @Override
    public String getRunCommandName() {
        return RUN_COMMAND_NAME;
    }

    /**
     * The whole drain runs inside one session. The server rejects a {@code getMore} whose {@code lsid}
     * differs from the one that opened the cursor (error 50738), and each bare {@code runCommand} borrows
     * its own implicit session, so anything else using this client concurrently can hand the getMore a
     * different session. Any cursor still open when this fails is killed rather than left to time out.
     */
    @Override
    public List<Document> queryForList(final MongoLiquibaseDatabase database) {
        final MongoDatabase mongoDatabase = database.getMongoDatabase();
        final List<Document> results = new ArrayList<>();

        try (ClientSession session = startSession(database)) {
            long cursorId = 0L;
            String cursorCollection = null;
            try {
                Document cursor = cursorOf(runCommand(mongoDatabase, session, getCommand()));
                // Resolve ns and id before the batch so finally can kill even if firstBatch is missing,
                // or if ns is missing: fall back to the listCollections cursor collection rather than no-op.
                cursorCollection = cursorCollectionOf(cursor);
                cursorId = cursorIdOf(cursor);
                results.addAll(batchOf(cursor, FIRST_BATCH));
                if (cursorId != 0L && !hasNamespace(cursor)) {
                    throw unexpectedResponse("no '" + CURSOR_NS + "' namespace to continue the open cursor from", cursor);
                }

                while (cursorId != 0L) {
                    cursor = cursorOf(runCommand(mongoDatabase, session,
                            new Document(GET_MORE, cursorId).append(COLLECTION, cursorCollection)));
                    results.addAll(batchOf(cursor, NEXT_BATCH));
                    cursorId = cursorIdOf(cursor);
                }
            } finally {
                killCursor(mongoDatabase, session, cursorId, cursorCollection);
            }
        }
        return results;
    }

    /**
     * Prefers an explicit session so every command in the drain carries the same {@code lsid}. A
     * deployment that cannot start one does not bind cursors to a session either, so draining without
     * one is correct there rather than merely a fallback.
     */
    private ClientSession startSession(final MongoLiquibaseDatabase database) {
        final DatabaseConnection connection = database.getConnection();
        if (!(connection instanceof MongoConnection)) {
            return null;
        }
        final MongoClient mongoClient = ((MongoConnection) connection).getMongoClient();
        if (mongoClient == null) {
            return null;
        }
        try {
            return mongoClient.startSession();
        } catch (MongoClientException e) {
            Scope.getCurrentScope().getLog(getClass())
                    .fine("Unable to start a session, draining " + RUN_COMMAND_NAME + " without one", e);
            return null;
        }
    }

    private Document runCommand(final MongoDatabase mongoDatabase, final ClientSession session, final Document command) {
        final Document response = session == null
                ? mongoDatabase.runCommand(command)
                : mongoDatabase.runCommand(session, command);
        checkResponse(response);
        return response;
    }

    private void killCursor(final MongoDatabase mongoDatabase, final ClientSession session,
                            final long cursorId, final String cursorCollection) {
        if (cursorId == 0L || cursorCollection == null) {
            return;
        }
        try {
            runCommand(mongoDatabase, session, new Document(KILL_CURSORS, cursorCollection)
                    .append(CURSORS, Collections.singletonList(cursorId)));
        } catch (RuntimeException e) {
            Scope.getCurrentScope().getLog(getClass())
                    .fine("Unable to kill " + RUN_COMMAND_NAME + " cursor " + cursorId + ", leaving it to time out", e);
        }
    }

    private static Document cursorOf(final Document response) {
        final Object cursor = response.get(CURSOR);
        if (!(cursor instanceof Document)) {
            throw unexpectedResponse("no '" + CURSOR + "' document", response);
        }
        return (Document) cursor;
    }

    private static List<Document> batchOf(final Document cursor, final String batchKey) {
        final List<Document> batch = cursor.getList(batchKey, Document.class);
        if (batch == null) {
            throw unexpectedResponse("no '" + batchKey + "' array", cursor);
        }
        return batch;
    }

    /** {@code cursor.id} is an int64 on a conformant server; read it as a Number so an int32 still works. */
    private static long cursorIdOf(final Document cursor) {
        final Object cursorId = cursor.get(CURSOR_ID);
        if (!(cursorId instanceof Number)) {
            throw unexpectedResponse("no numeric '" + CURSOR_ID + "'", cursor);
        }
        return ((Number) cursorId).longValue();
    }

    private static boolean hasNamespace(final Document cursor) {
        final String namespace = cursor.getString(CURSOR_NS);
        return namespace != null && namespace.indexOf('.') >= 0;
    }

    private static String cursorCollectionOf(final Document cursor) {
        if (!hasNamespace(cursor)) {
            return LIST_COLLECTIONS_CURSOR;
        }
        final String namespace = cursor.getString(CURSOR_NS);
        return namespace.substring(namespace.indexOf('.') + 1);
    }

    /** Keys only, never the document: a first batch can be up to 16MB. */
    private static MongoException unexpectedResponse(final String problem, final Document actual) {
        return new MongoException("Unexpected " + RUN_COMMAND_NAME + " response, " + problem + " in " + actual.keySet());
    }

    private static Document combine(final Document filter) {
        return new Document(FILTER, filter).append(AUTHORIZED_COLLECTIONS, true);
    }
}
