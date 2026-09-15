package liquibase.nosql.executor;

import liquibase.executor.Executor;
import liquibase.executor.LoggingExecutor;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringWriter;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class NoSqlLoggingExecutorUnwrapperTest {

    @Mock
    private NoSqlExecutor delegatedExecutor;

    private MongoLiquibaseDatabase database;

    @BeforeEach
    void setUp() {
        database = new MongoLiquibaseDatabase();
    }

    @Test
    void unwrapIfLogging_withNonLoggingExecutor_returnsSameInstance() {
        Executor executor = mock(Executor.class);

        assertThat(NoSqlLoggingExecutorUnwrapper.unwrapIfLogging(executor)).isSameAs(executor);
    }

    @Test
    void unwrapIfLogging_withLoggingExecutor_returnsDelegatedExecutor() {
        LoggingExecutor loggingExecutor = new LoggingExecutor(delegatedExecutor, new StringWriter(), database);

        assertThat(NoSqlLoggingExecutorUnwrapper.unwrapIfLogging(loggingExecutor)).isSameAs(delegatedExecutor);
    }

    @Test
    void unwrapIfLogging_whenDelegatedReadExecutorFieldUnavailable_returnsLoggingExecutorUnchanged() {
        LoggingExecutor loggingExecutor = new LoggingExecutor(delegatedExecutor, new StringWriter(), database);

        assertThat(NoSqlLoggingExecutorUnwrapper.unwrapIfLogging(loggingExecutor, null))
                .isSameAs(loggingExecutor);
    }

    @Test
    void unwrapIfLogging_whenDelegatedExecutorIsNull_returnsLoggingExecutorUnchanged() throws Exception {
        LoggingExecutor loggingExecutor = new LoggingExecutor(delegatedExecutor, new StringWriter(), database);
        Field delegatedField = LoggingExecutor.class.getDeclaredField("delegatedReadExecutor");
        delegatedField.setAccessible(true);
        delegatedField.set(loggingExecutor, null);

        assertThat(NoSqlLoggingExecutorUnwrapper.unwrapIfLogging(loggingExecutor)).isSameAs(loggingExecutor);
    }
}
