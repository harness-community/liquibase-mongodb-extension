package liquibase.ext.mongodb.statement;

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

import liquibase.statement.AbstractSqlStatement;

public class MongoshStatement extends AbstractSqlStatement {

    private final String javascript;
    private final String endDelimiter;

    public MongoshStatement(String javascript) {
        this(javascript, ";");
    }

    public MongoshStatement(String javascript, String endDelimiter) {
        this.javascript = javascript;
        this.endDelimiter = endDelimiter != null ? endDelimiter : ";";
    }

    public String getJavaScript() {
        return javascript;
    }

    public String getEndDelimiter() {
        return endDelimiter.replace("\\r", "\r").replace("\\n", "\n");
    }

    @Override
    public String toString() {
        return javascript;
    }

    public String toJs() {
        return javascript + getEndDelimiter();
    }
}
