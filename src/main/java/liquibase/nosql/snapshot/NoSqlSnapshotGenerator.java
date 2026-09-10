package liquibase.nosql.snapshot;

import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Schema;

import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static liquibase.plugin.Plugin.PRIORITY_SPECIALIZED;

/**
 * Blanket guard for MongoDB targets: Liquibase's JDBC-based snapshot machinery does not apply to Mongo, so this
 * claims specialized priority for every object type and throws a clear error instead of letting core's JDBC
 * snapshot generators fail with a confusing ClassCastException on the non-JDBC connection.
 * <p>
 * {@link Catalog}, {@link Schema}, {@link Collection}, and {@link Index} are excluded from the blanket claim:
 * Catalog/Schema resolve to Mongo's default database without touching JDBC (see this extension's own
 * MongoCatalogSnapshotGenerator and MongoSchemaSnapshotGenerator), and Collection/Index are snapshotted by this
 * extension's own generators to support {@code generateChangelog}.
 */
public class NoSqlSnapshotGenerator implements SnapshotGenerator {
    private static final ResourceBundle mongoBundle = ResourceBundle.getBundle("liquibase/i18n/liquibase-mongo");

    private static final List<Class<? extends DatabaseObject>> SUPPORTED_TYPES =
            Arrays.asList(Catalog.class, Schema.class, Collection.class, Index.class);

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof MongoLiquibaseDatabase && !SUPPORTED_TYPES.contains(objectType)) {
            return PRIORITY_SPECIALIZED;
        }
        return PRIORITY_NONE;
    }

    @Override
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) throws DatabaseException, InvalidExampleException {
        // DatabaseObjectFactory.parseTypes() matches diffTypes tokens against ALL classpath-registered
        // DatabaseObject simple names, so "indexes" also resolves to core's liquibase.structure.core.Index -
        // that type is never actually applicable to Mongo, so treat it as simply not found rather than an error.
        if (example instanceof liquibase.structure.core.Index) {
            return null;
        }
        throw new DatabaseException(String.format(mongoBundle.getString("command.unsupported"), "db-doc, diff*, generate-changelog, and snapshot*"));
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        return new Class[0];
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[0];
    }
}
