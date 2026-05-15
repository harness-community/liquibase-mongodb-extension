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

import liquibase.change.AbstractSQLChange;
import liquibase.change.DatabaseChange;
import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.ext.mongodb.statement.MongoshStatement;
import liquibase.statement.SqlStatement;
import liquibase.util.StringUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@DatabaseChange(
    name = "mongo",
    description = "Execute JS/MongoDB shell commands via mongosh",
    priority = 1
)
@NoArgsConstructor
@Getter
public class MongoshChange extends AbstractSQLChange {

    private static final String MONGO_PROPERTY_NAME = "mongo";
    private static final String DBMS_PROPERTY_NAME = "dbms";

    private String mongo;

    public void setMongo(String mongo) {
        this.mongo = mongo;
        this.setSql(mongo);
    }

    @Override
    public String getConfirmationMessage() {
        return "Mongosh command executed";
    }

    @Override
    public SqlStatement[] generateStatements(Database database) {
        String javascript = StringUtils.trimToNull(this.getMongo());
        if (javascript == null) {
            return SqlStatement.EMPTY_SQL_STATEMENT;
        }

        return new SqlStatement[] {
            new MongoshStatement(normalizeLineEndings(javascript), this.getEndDelimiter())
        };
    }

    @Override
    public ValidationErrors validate(Database database) {
        ValidationErrors validationErrors = new ValidationErrors();
        if (StringUtils.trimToNull(this.getMongo()) == null) {
            validationErrors.addError("'mongo' property is required for mongosh changes.");
        }
        return validationErrors;
    }

    public Set<String> getSerializableFields() {
        return new HashSet<>(Arrays.asList(MONGO_PROPERTY_NAME, DBMS_PROPERTY_NAME));
    }
}
