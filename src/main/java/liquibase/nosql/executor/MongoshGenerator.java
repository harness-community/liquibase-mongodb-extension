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

import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.ext.mongodb.statement.MongoshStatement;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.AbstractSqlGenerator;

public class MongoshGenerator extends AbstractSqlGenerator<MongoshStatement> {

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT;
    }

    @Override
    public boolean supports(MongoshStatement statement, Database database) {
        return statement instanceof MongoshStatement;
    }

    @Override
    public ValidationErrors validate(MongoshStatement statement, Database database,
                                     SqlGeneratorChain<MongoshStatement> sqlGeneratorChain) {
        ValidationErrors validationErrors = new ValidationErrors();
        if (statement.getJavaScript() == null || statement.getJavaScript().trim().isEmpty()) {
            validationErrors.addError("JavaScript content is required for mongosh statement");
        }
        return validationErrors;
    }

    @Override
    public Sql[] generateSql(MongoshStatement statement, Database database,
                             SqlGeneratorChain<MongoshStatement> sqlGeneratorChain) {
        String javascript = statement.getJavaScript();
        if (javascript == null || javascript.trim().isEmpty()) {
            return new Sql[0];
        }

        String formattedJs = formatJavaScriptForDisplay(javascript);
        return new Sql[] { new UnparsedSql(formattedJs) };
    }

    private String formatJavaScriptForDisplay(String javascript) {
        if (javascript == null) {
            return "";
        }

        String formatted = javascript.trim();
        if (formatted.endsWith(";")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }

        return "// MongoDB JavaScript:\n" + formatted;
    }
}
