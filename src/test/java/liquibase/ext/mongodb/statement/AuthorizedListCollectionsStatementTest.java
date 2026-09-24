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
import liquibase.ext.mongodb.database.MongoConnection;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedListCollectionsStatementTest {

    @Mock
    private MongoLiquibaseDatabase database;

    @Mock
    private MongoDatabase mongoDatabase;

    @Mock
    private MongoConnection connection;

    @Mock
    private MongoClient mongoClient;

    @Mock
    private ClientSession session;

    @BeforeEach
    void setUp() {
        lenient().when(database.getMongoDatabase()).thenReturn(mongoDatabase);
    }

    /** Give the statement a connection it can start a real (mocked) session from. */
    private void withSessions() {
        when(database.getConnection()).thenReturn(connection);
        when(connection.getMongoClient()).thenReturn(mongoClient);
        when(mongoClient.startSession()).thenReturn(session);
    }

    private static Document cursorResponse(final long cursorId, final String batchKey, final List<Document> batch) {
        final Document cursor = new Document("id", cursorId).append(batchKey, batch);
        if ("firstBatch".equals(batchKey)) {
            cursor.append("ns", "testdb.$cmd.listCollections");
        }
        return new Document("cursor", cursor).append("ok", 1.0);
    }

    @Test
    void queryForListReturnsFirstBatchWhenCursorIsExhausted() {
        final Document collectionA = new Document("name", "orders").append("type", "collection");
        final Document response = new Document("cursor", new Document("id", 0L)
                .append("ns", "testdb.$cmd.listCollections")
                .append("firstBatch", Arrays.asList(collectionA)))
                .append("ok", 1.0);
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(response);

        final List<Document> result = new AuthorizedListCollectionsStatement().queryForList(database);

        assertThat(result).containsExactly(collectionA);
        verify(mongoDatabase, times(1)).runCommand(any(Document.class));
    }

    @Test
    void queryForListDrainsGetMoreUntilCursorIdIsZero() {
        final Document collectionA = new Document("name", "orders").append("type", "collection");
        final Document collectionB = new Document("name", "users").append("type", "collection");

        final Document firstResponse = new Document("cursor", new Document("id", 42L)
                .append("ns", "testdb.$cmd.listCollections")
                .append("firstBatch", Arrays.asList(collectionA)))
                .append("ok", 1.0);
        final Document getMoreResponse = new Document("cursor", new Document("id", 0L)
                .append("nextBatch", Arrays.asList(collectionB)))
                .append("ok", 1.0);

        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        when(mongoDatabase.runCommand(captor.capture())).thenReturn(firstResponse, getMoreResponse);

        final List<Document> result = new AuthorizedListCollectionsStatement().queryForList(database);

        assertThat(result).containsExactly(collectionA, collectionB);
        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(1).getLong("getMore")).isEqualTo(42L);
        assertThat(captor.getAllValues().get(1).getString("collection")).isEqualTo("$cmd.listCollections");
    }

    @Test
    void constructorSetsAuthorizedCollectionsAndFilterOnTheCommand() {
        final AuthorizedListCollectionsStatement statement = new AuthorizedListCollectionsStatement(new Document("name", "orders"));

        assertThat(statement.getCommand().getBoolean("authorizedCollections")).isTrue();
        assertThat(statement.getCommand().get("filter", Document.class)).isEqualTo(new Document("name", "orders"));
    }

    @Test
    void queryForListPropagatesMongoExceptionFromInitialCommand() {
        when(mongoDatabase.runCommand(any(Document.class))).thenThrow(new MongoException("boom"));

        assertThatThrownBy(() -> new AuthorizedListCollectionsStatement().queryForList(database))
                .isInstanceOf(MongoException.class);
    }

    @Test
    void queryForListRunsTheWholeDrainInOneSession() {
        withSessions();
        final Document collectionA = new Document("name", "orders");
        final Document collectionB = new Document("name", "users");
        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        when(mongoDatabase.runCommand(eq(session), captor.capture())).thenReturn(
                cursorResponse(42L, "firstBatch", Arrays.asList(collectionA)),
                cursorResponse(0L, "nextBatch", Arrays.asList(collectionB)));

        final List<Document> result = new AuthorizedListCollectionsStatement().queryForList(database);

        assertThat(result).containsExactly(collectionA, collectionB);
        // Every command carries the same session; the sessionless overload is never used, because the
        // server rejects a getMore whose lsid differs from the one that opened the cursor.
        assertThat(captor.getAllValues()).hasSize(2);
        verify(mongoDatabase, never()).runCommand(any(Document.class));
        verify(session).close();
    }

    @Test
    void queryForListKillsAnOpenCursorWhenTheDrainFails() {
        withSessions();
        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        when(mongoDatabase.runCommand(eq(session), captor.capture())).thenAnswer(invocation -> {
            final Document command = invocation.getArgument(1);
            if (command.containsKey("listCollections")) {
                return cursorResponse(42L, "firstBatch", Arrays.asList(new Document("name", "orders")));
            }
            if (command.containsKey("getMore")) {
                throw new MongoException("cursor died");
            }
            return new Document("ok", 1.0);
        });

        assertThatThrownBy(() -> new AuthorizedListCollectionsStatement().queryForList(database))
                .isInstanceOf(MongoException.class)
                .hasMessageContaining("cursor died");

        final Document killCursors = captor.getAllValues().get(2);
        assertThat(killCursors.getString("killCursors")).isEqualTo("$cmd.listCollections");
        assertThat(killCursors.getList("cursors", Long.class)).containsExactly(42L);
        verify(session).close();
    }

    @Test
    void queryForListDoesNotKillAnExhaustedCursor() {
        withSessions();
        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        when(mongoDatabase.runCommand(eq(session), captor.capture()))
                .thenReturn(cursorResponse(0L, "firstBatch", Arrays.asList(new Document("name", "orders"))));

        new AuthorizedListCollectionsStatement().queryForList(database);

        assertThat(captor.getAllValues()).hasSize(1);
        assertThat(captor.getAllValues().get(0).containsKey("killCursors")).isFalse();
    }

    @Test
    void queryForListDoesNotMaskTheOriginalFailureWhenKillCursorsAlsoFails() {
        withSessions();
        when(mongoDatabase.runCommand(eq(session), any(Document.class))).thenAnswer(invocation -> {
            final Document command = invocation.getArgument(1);
            if (command.containsKey("listCollections")) {
                return cursorResponse(42L, "firstBatch", Arrays.asList(new Document("name", "orders")));
            }
            throw new MongoException(command.containsKey("getMore") ? "cursor died" : "kill refused");
        });

        assertThatThrownBy(() -> new AuthorizedListCollectionsStatement().queryForList(database))
                .isInstanceOf(MongoException.class)
                .hasMessageContaining("cursor died");
    }

    @Test
    void queryForListDrainsWithoutASessionWhenTheDeploymentCannotStartOne() {
        when(database.getConnection()).thenReturn(connection);
        when(connection.getMongoClient()).thenReturn(mongoClient);
        when(mongoClient.startSession()).thenThrow(new MongoClientException("sessions unsupported"));
        when(mongoDatabase.runCommand(any(Document.class)))
                .thenReturn(cursorResponse(42L, "firstBatch", Arrays.asList(new Document("name", "orders"))),
                        cursorResponse(0L, "nextBatch", Arrays.asList(new Document("name", "users"))));

        // A deployment without sessions does not bind cursors to one either, so an unbound drain is correct there.
        assertThat(new AuthorizedListCollectionsStatement().queryForList(database)).hasSize(2);
        verify(mongoDatabase, times(2)).runCommand(any(Document.class));
    }

    @Test
    void queryForListKillsAnOpenCursorWhenTheDrainFailsWithoutASession() {
        when(database.getConnection()).thenReturn(connection);
        when(connection.getMongoClient()).thenReturn(mongoClient);
        when(mongoClient.startSession()).thenThrow(new MongoClientException("sessions unsupported"));
        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        when(mongoDatabase.runCommand(captor.capture())).thenAnswer(invocation -> {
            final Document command = invocation.getArgument(0);
            if (command.containsKey("listCollections")) {
                return cursorResponse(42L, "firstBatch", Arrays.asList(new Document("name", "orders")));
            }
            if (command.containsKey("getMore")) {
                throw new MongoException("cursor died");
            }
            return new Document("ok", 1.0);
        });

        assertThatThrownBy(() -> new AuthorizedListCollectionsStatement().queryForList(database))
                .isInstanceOf(MongoException.class)
                .hasMessageContaining("cursor died");

        final Document killCursors = captor.getAllValues().get(2);
        assertThat(killCursors.getString("killCursors")).isEqualTo("$cmd.listCollections");
        assertThat(killCursors.getList("cursors", Long.class)).containsExactly(42L);
        verify(mongoDatabase, never()).runCommand(eq(session), any(Document.class));
    }

    @Test
    void queryForListAcceptsAnInt32CursorId() {
        final Document firstResponse = new Document("cursor", new Document("id", 42)
                .append("ns", "testdb.$cmd.listCollections")
                .append("firstBatch", Arrays.asList(new Document("name", "orders"))))
                .append("ok", 1.0);
        when(mongoDatabase.runCommand(any(Document.class)))
                .thenReturn(firstResponse, cursorResponse(0, "nextBatch", Arrays.asList(new Document("name", "users"))));

        assertThat(new AuthorizedListCollectionsStatement().queryForList(database)).hasSize(2);
    }

    @Test
    void queryForListFailsWithContextWhenTheResponseHasNoCursor() {
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1.0));

        assertThatThrownBy(() -> new AuthorizedListCollectionsStatement().queryForList(database))
                .isInstanceOf(MongoException.class)
                .hasMessageContaining("no 'cursor' document");
    }

    @Test
    void queryForListFailsWithContextWhenTheCursorHasNoBatch() {
        when(mongoDatabase.runCommand(any(Document.class)))
                .thenReturn(new Document("cursor", new Document("id", 0L)).append("ok", 1.0));

        assertThatThrownBy(() -> new AuthorizedListCollectionsStatement().queryForList(database))
                .isInstanceOf(MongoException.class)
                .hasMessageContaining("no 'firstBatch' array");
    }

    @Test
    void queryForListFailsWithContextWhenAnOpenCursorHasNoNamespace() {
        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        when(mongoDatabase.runCommand(captor.capture())).thenReturn(
                new Document("cursor",
                        new Document("id", 42L).append("firstBatch", Arrays.asList(new Document("name", "orders"))))
                        .append("ok", 1.0),
                new Document("ok", 1.0));

        assertThatThrownBy(() -> new AuthorizedListCollectionsStatement().queryForList(database))
                .isInstanceOf(MongoException.class)
                .hasMessageContaining("no 'ns' namespace");

        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(1).getString("killCursors")).isEqualTo("$cmd.listCollections");
        assertThat(captor.getAllValues().get(1).getList("cursors", Long.class)).containsExactly(42L);
    }

    @Test
    void queryForListFailsWithContextWhenTheCursorIdIsNotNumeric() {
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("cursor",
                new Document("id", "nope").append("ns", "testdb.$cmd.listCollections")
                        .append("firstBatch", Arrays.asList(new Document("name", "orders"))))
                .append("ok", 1.0));

        assertThatThrownBy(() -> new AuthorizedListCollectionsStatement().queryForList(database))
                .isInstanceOf(MongoException.class)
                .hasMessageContaining("no numeric 'id'");
    }
}
