package liquibase.ext.mongodb.change;

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
import liquibase.change.AbstractSQLChange;
import liquibase.change.DatabaseChange;
import liquibase.change.DatabaseChangeProperty;
import liquibase.database.Database;
import liquibase.exception.SetupException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.exception.ValidationErrors;
import liquibase.ext.mongodb.statement.MongoshStatement;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.util.StreamUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@DatabaseChange(
    name = "mongoFile",
    description = "Execute JS/MongoDB shell commands from external file via mongosh",
    priority = 1
)
@NoArgsConstructor
@Getter
@Setter
public class MongoshFileChange extends AbstractSQLChange {

    private static final String PATH_PROPERTY_NAME = "path";
    private static final String RELATIVE_TO_CHANGELOG_FILE_PROPERTY_NAME = "relativeToChangelogFile";
    private static final String DBMS_PROPERTY_NAME = "dbms";

    private String path;
    private Boolean relativeToChangelogFile;

    @DatabaseChangeProperty(exampleValue = "utf8")
    public String getEncoding() {
        return this.encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    public void finishInitialization() throws SetupException {
        if (path == null) {
            throw new SetupException("'path' is required for mongoFile changes.");
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Mongosh commands in file " + path + " executed";
    }

    @Override
    public String getSql() {
        String sql = super.getSql();
        if (sql != null) {
            return sql;
        }

        try (InputStream sqlStream = openSqlStream()) {
            if (sqlStream == null) {
                return null;
            }

            String content = StreamUtil.readStreamAsString(sqlStream, getEncoding());
            setSql(content);
            return super.getSql();
        } catch (IOException e) {
            throw new UnexpectedLiquibaseException("Error reading mongosh script file: " + path, e);
        }
    }

    @Override
    public void setSql(String sql) {
        setNoSql(sql);
    }

    public void setNoSql(String sql) {
        if (getChangeSet() != null && getChangeSet().getChangeLogParameters() != null) {
            sql = getChangeSet().getChangeLogParameters().expandExpressions(sql, getChangeSet().getChangeLog());
        }
        super.setSql(sql);
    }

    @Override
    public SqlStatement[] generateStatements(Database database) {
        String sql = StringUtils.trimToNull(getSql());
        if (sql == null) {
            return SqlStatement.EMPTY_SQL_STATEMENT;
        }

        return new SqlStatement[] {
            new MongoshStatement(normalizeLineEndings(sql), getEndDelimiter())
        };
    }

    @Override
    public InputStream openSqlStream() throws IOException {
        return openNoSqlStream();
    }

    public InputStream openNoSqlStream() throws IOException {
        if (path == null) {
            return null;
        }

        ResourceAccessor resourceAccessor = Scope.getCurrentScope().getResourceAccessor();
        if (Boolean.TRUE.equals(getRelativeToChangelogFile())) {
            return resourceAccessor.get(getChangeSet().getChangeLog().getPhysicalFilePath())
                    .resolveSibling(path)
                    .openInputStream();
        }

        return resourceAccessor.getExisting(path).openInputStream();
    }

    @Override
    public ValidationErrors validate(Database database) {
        ValidationErrors validationErrors = new ValidationErrors();
        if (StringUtils.trimToNull(getPath()) == null) {
            validationErrors.addError("'path' is required for mongoFile changes.");
        }
        return validationErrors;
    }

    public Set<String> getSerializableFields() {
        return new HashSet<>(Arrays.asList(
            RELATIVE_TO_CHANGELOG_FILE_PROPERTY_NAME,
            PATH_PROPERTY_NAME,
            DBMS_PROPERTY_NAME
        ));
    }
}
