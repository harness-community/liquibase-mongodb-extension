package liquibase.nosql.executor;

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

import liquibase.Scope;
import liquibase.change.Change;
import liquibase.change.core.EmptyChange;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.exception.ValidationErrors;
import liquibase.executor.AbstractExecutor;
import liquibase.ext.mongodb.change.MongoshChange;
import liquibase.ext.mongodb.change.MongoshFileChange;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.statement.MongoshStatement;
import liquibase.ext.mongodb.tools.MongoshRunner;
import liquibase.logging.Logger;
import liquibase.nosql.database.AbstractNoSqlDatabase;
import liquibase.servicelocator.LiquibaseService;
import liquibase.sql.Sql;
import liquibase.sql.visitor.SqlVisitor;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.SqlStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@LiquibaseService
public class MongoshExecutor extends AbstractExecutor {

    public static final String EXECUTOR_NAME = "mongosh";

    private final Logger log = Scope.getCurrentScope().getLog(getClass());
    private ChangeSet currentChangeSet;

    @Override
    public String getName() {
        return EXECUTOR_NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY_SPECIALIZED;
    }

    @Override
    public boolean supports(Database database) {
        return database instanceof MongoLiquibaseDatabase;
    }

    @Override
    public ValidationErrors validate(ChangeSet changeSet) {
        this.currentChangeSet = changeSet;
        ValidationErrors validationErrors = new ValidationErrors();

        if (changeSet != null) {
            for (Change change : changeSet.getChanges()) {
                validateChange(changeSet, validationErrors, change, EXECUTOR_NAME);
            }
        }

        return validationErrors;
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractNoSqlDatabase> T getDatabase() {
        return (T) this.database;
    }

    protected void validateChange(ChangeSet changeSet, ValidationErrors validationErrors,
                                  Change change, String executorType) {
        if (change instanceof MongoshChange || change instanceof MongoshFileChange || change instanceof EmptyChange) {
            return;
        }

        String details = "In changeset '" + changeSet.getId() + "::" + changeSet.getAuthor()
                + "' there is an unsupported change type '" + change.getClass().getSimpleName() + "'";
        String message = String.format(
                "Changeset validation failed: %s. A changeset with runWith='%s' attribute may only contain "
                        + "'mongo' or 'mongoFile' change types.",
                details,
                executorType);
        validationErrors.addError(message);
    }

    @Override
    public void execute(SqlStatement statement, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        if (!(statement instanceof MongoshStatement)) {
            log.warning("Statement should be type MongoshStatement, but is " + statement.getClass().getName());
        }

        try {
            Sql[] generatedSql = SqlGeneratorFactory.getInstance().generateSql(statement, getDatabase());
            if (generatedSql != null && generatedSql.length > 0) {
                MongoshRunner runner = new MongoshRunner(currentChangeSet, generatedSql);
                runner.executeCommand(getDatabase());

                if (currentChangeSet != null) {
                    log.info(String.format("Successfully executed changeset '%s' by '%s' via mongosh",
                            currentChangeSet.getId(), currentChangeSet.getAuthor()));
                }
            }
        } catch (Exception e) {
            String errorMsg;
            if (currentChangeSet != null) {
                errorMsg = String.format("Changeset '%s' by '%s' failed to deploy with mongosh.",
                        currentChangeSet.getId(), currentChangeSet.getAuthor());
            } else {
                errorMsg = "Statement failed to deploy with mongosh.";
            }
            throw new DatabaseException(errorMsg, e);
        }
    }

    @Override
    public void execute(SqlStatement sql) throws DatabaseException {
        execute(sql, new ArrayList<>());
    }

    @Override
    public <T> T queryForObject(SqlStatement sql, Class<T> requiredType) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public <T> T queryForObject(SqlStatement sql, Class<T> requiredType, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public long queryForLong(SqlStatement sql) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public long queryForLong(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public int queryForInt(SqlStatement sql) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public int queryForInt(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public List<Object> queryForList(SqlStatement sql, Class elementType) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public List<Object> queryForList(SqlStatement sql, Class elementType, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public List<Map<String, ?>> queryForList(SqlStatement sql) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public List<Map<String, ?>> queryForList(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new DatabaseException("Query operations are not supported by mongosh executor. Use runWith='jdbc' for query operations.");
    }

    @Override
    public int update(SqlStatement sql) throws DatabaseException {
        throw new DatabaseException("Update operations are not supported by mongosh executor. Use runWith='jdbc' for update operations or use 'mongo'/'mongoFile' change types.");
    }

    @Override
    public int update(SqlStatement sql, List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new DatabaseException("Update operations are not supported by mongosh executor. Use runWith='jdbc' for update operations or use 'mongo'/'mongoFile' change types.");
    }

    @Override
    public void comment(String message) throws DatabaseException {
        log.info("Mongosh executor comment: " + message);
    }

    @Override
    public boolean updatesDatabase() {
        return true;
    }
}
