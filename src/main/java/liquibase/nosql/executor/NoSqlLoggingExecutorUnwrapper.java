package liquibase.nosql.executor;

/*-
 * #%L
 * Liquibase NoSql Extension
 * %%
 * Copyright (C) 2020 Mastercard
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

import liquibase.executor.Executor;
import liquibase.executor.LoggingExecutor;

import java.lang.reflect.Field;

/**
 * For *-sql commands (updateSql/rollbackSql/rollbackCountSql), Liquibase core unconditionally
 * swaps the "jdbc" executor for a LoggingExecutor before any command-specific logic runs,
 * regardless of database type. History and lock services here never generate SQL - they issue
 * real reads/writes against MongoDB directly - so they need the real NoSqlExecutor underneath,
 * not the LoggingExecutor wrapper. LoggingExecutor exposes no public accessor for the executor it
 * wraps, so this reads its private delegatedReadExecutor field via reflection. If that field can't
 * be resolved or opened (e.g. a future liquibase-core rename, or module/reflection restrictions),
 * the original executor is returned unchanged and callers fall back to their existing
 * "*sql not supported" behavior.
 */
public final class NoSqlLoggingExecutorUnwrapper {

    private static final Field DELEGATED_READ_EXECUTOR_FIELD = resolveDelegatedReadExecutorField();

    private NoSqlLoggingExecutorUnwrapper() {
    }

    public static Executor unwrapIfLogging(Executor executor) {
        return unwrapIfLogging(executor, DELEGATED_READ_EXECUTOR_FIELD);
    }

    static Executor unwrapIfLogging(Executor executor, Field delegatedReadExecutorField) {
        if (!(executor instanceof LoggingExecutor) || delegatedReadExecutorField == null) {
            return executor;
        }
        try {
            Executor realExecutor = (Executor) delegatedReadExecutorField.get(executor);
            return realExecutor != null ? realExecutor : executor;
        } catch (IllegalAccessException e) {
            return executor;
        }
    }

    private static Field resolveDelegatedReadExecutorField() {
        try {
            Field field = LoggingExecutor.class.getDeclaredField("delegatedReadExecutor");
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }
}
