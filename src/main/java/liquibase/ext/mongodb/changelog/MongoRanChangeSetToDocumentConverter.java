package liquibase.ext.mongodb.changelog;

import liquibase.ContextExpression;
import liquibase.Labels;
import liquibase.change.CheckSum;
import liquibase.changelog.ChangeSet;
import liquibase.nosql.changelog.AbstractNoSqlItemToDocumentConverter;
import org.bson.Document;

import java.util.Collection;
import java.util.Date;

import static java.util.Optional.ofNullable;

public class MongoRanChangeSetToDocumentConverter extends AbstractNoSqlItemToDocumentConverter<MongoRanChangeSet, Document> {

    @Override
    public Document toDocument(final MongoRanChangeSet item) {

        final Document document = new Document();
        document.append(MongoRanChangeSet.Fields.fileName, item.getChangeLog());
        document.append(MongoRanChangeSet.Fields.changeSetId, item.getId());
        document.append(MongoRanChangeSet.Fields.author, item.getAuthor());
        document.append(MongoRanChangeSet.Fields.md5sum,
                ofNullable(item.getLastCheckSum()).map(CheckSum::toString).orElse(null));
        document.append(MongoRanChangeSet.Fields.dateExecuted,
                ofNullable(item.getDateExecuted()).orElse(null));
        document.append(MongoRanChangeSet.Fields.tag, item.getTag());
        document.append(MongoRanChangeSet.Fields.execType,
                ofNullable(item.getExecType()).map(e -> e.value).orElse(null));
        document.append(MongoRanChangeSet.Fields.description, item.getDescription());
        document.append(MongoRanChangeSet.Fields.comments, item.getComments());
        document.append(MongoRanChangeSet.Fields.contexts, buildFullContext(item.getContextExpression(), item.getInheritableContexts()));
        document.append(MongoRanChangeSet.Fields.labels, buildLabels(item.getLabels()));
        document.append(MongoRanChangeSet.Fields.deploymentId, item.getDeploymentId());
        document.append(MongoRanChangeSet.Fields.orderExecuted, item.getOrderExecuted());
        document.append(MongoRanChangeSet.Fields.liquibase, item.getLiquibaseVersion());

        return document;
    }

    @Override
    public MongoRanChangeSet fromDocument(final Document document) {

        return new MongoRanChangeSet(
                (String) document.get(MongoRanChangeSet.Fields.fileName),
                // Change Set Id which is populated to id POJO field
                (String) document.get(MongoRanChangeSet.Fields.changeSetId),
                (String) document.get(MongoRanChangeSet.Fields.author),
                CheckSum.parse((String) document.get(MongoRanChangeSet.Fields.md5sum)),
                (Date) document.get(MongoRanChangeSet.Fields.dateExecuted),
                (String) document.get(MongoRanChangeSet.Fields.tag),
                ofNullable(document.get(MongoRanChangeSet.Fields.execType))
                        .map(s -> ChangeSet.ExecType.valueOf((String) s)).orElse(null),
                (String) document.get(MongoRanChangeSet.Fields.description),
                (String) document.get(MongoRanChangeSet.Fields.comments),
                new ContextExpression((String)document.get(MongoRanChangeSet.Fields.contexts)),
                // not parsed out
                null,
                new Labels((String)document.get(MongoRanChangeSet.Fields.labels)),
                (String) document.get(MongoRanChangeSet.Fields.deploymentId),
                convertToInteger(document.get(MongoRanChangeSet.Fields.orderExecuted)),
                (String) document.get(MongoRanChangeSet.Fields.liquibase)
        );
    }

    /**
     * Converts BSON numeric values to Integer with overflow protection.
     *
     * @param value BSON value (Integer, Long, Double, etc.)
     * @return Integer value or null
     * @throws IllegalStateException if value exceeds Integer range or is non-numeric
     */
    private Integer convertToInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (!(value instanceof Number)) {
            throw new IllegalStateException(
                String.format("orderExecuted has invalid type %s (value: %s)",
                    value.getClass().getName(), value)
            );
        }
        long longValue = ((Number) value).longValue();
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                String.format("orderExecuted value %d exceeds Integer range [%d, %d]",
                    longValue, Integer.MIN_VALUE, Integer.MAX_VALUE)
            );
        }
        
        return (int) longValue;
    }

    /**
     * TODO: raise with liquibase to move into {@link Labels} class
     */
    public String buildLabels(Labels labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        return labels.toString();
    }

    /**
     * TODO: raise with liquibase to move into {@link ContextExpression} class
     */
    public String buildFullContext(final ContextExpression contextExpression, final Collection<ContextExpression> inheritableContexts) {
        if ((contextExpression == null) || contextExpression.isEmpty()) {
            return null;
        }

        StringBuilder contextExpressionString = new StringBuilder();
        boolean notFirstContext = false;
        for (ContextExpression inheritableContext : inheritableContexts) {
            appendContext(contextExpressionString, inheritableContext.toString(), notFirstContext);
            notFirstContext = true;
        }
        appendContext(contextExpressionString, contextExpression.toString(), notFirstContext);

        return contextExpressionString.toString();
    }

    /**
     * TODO: raise with liquibase to move into {@link ContextExpression} class
     */
    private void appendContext(StringBuilder contextExpression, String contextToAppend, boolean notFirstContext) {
        boolean complexExpression = contextToAppend.contains(COMMA) || contextToAppend.contains(WHITESPACE);
        if (notFirstContext) {
            contextExpression.append(AND);
        }
        if (complexExpression) {
            contextExpression.append(OPEN_BRACKET);
        }
        contextExpression.append(contextToAppend);
        if (complexExpression) {
            contextExpression.append(CLOSE_BRACKET);
        }
    }
}
