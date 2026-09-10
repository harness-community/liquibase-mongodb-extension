package liquibase.ext.mongodb.command;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import liquibase.Scope;
import liquibase.command.AbstractCommandStep;
import liquibase.command.CommandArgumentDefinition;
import liquibase.command.CommandBuilder;
import liquibase.command.CommandDefinition;
import liquibase.command.CommandResultsBuilder;
import liquibase.command.CommandScope;
import liquibase.database.Database;
import liquibase.exception.CommandValidationException;
import liquibase.exception.LiquibaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.logging.Logger;
import liquibase.resource.OpenOptions;
import liquibase.resource.PathHandlerFactory;
import liquibase.resource.Resource;
import liquibase.util.StringUtil;
import org.bson.Document;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a baseline MongoDB changelog from the collections and indexes that already exist on the target
 * database. Ports the behavior of the manual MongoDB.py baseline-generation script to this extension, since
 * Liquibase's JDBC-based snapshot/diff machinery does not apply to MongoDB targets.
 */
public class MongoGenerateChangelogCommandStep extends AbstractCommandStep {

    public static final String[] COMMAND_NAME = {"mongoGenerateChangelog"};

    public static final CommandArgumentDefinition<String> CHANGELOG_FILE_ARG;
    public static final CommandArgumentDefinition<String> AUTHOR_ARG;
    public static final CommandArgumentDefinition<String> DIFF_TYPES_ARG;
    public static final CommandArgumentDefinition<Boolean> OVERWRITE_OUTPUT_FILE_ARG;

    private static final String DEFAULT_AUTHOR = "Harness";
    private static final String CHANGE_SET_ID_PREFIX = "baseline-collections";

    private static final String DIFF_TYPE_COLLECTIONS = "collections";
    private static final String DIFF_TYPE_INDEXES = "indexes";
    private static final Set<String> SUPPORTED_DIFF_TYPES = new HashSet<>(Arrays.asList(DIFF_TYPE_COLLECTIONS, DIFF_TYPE_INDEXES));

    private static final Set<String> SKIPPED_COLLECTION_NAMES = new HashSet<>(Arrays.asList("DATABASECHANGELOG", "DATABASECHANGELOGLOCK"));
    private static final String SYSTEM_COLLECTION_PREFIX = "system.";
    private static final String ID_INDEX_NAME = "_id_";
    private static final Set<String> INDEX_METADATA_EXCLUDED_KEYS = new HashSet<>(Arrays.asList("key", "v", "ns", "name"));

    static {
        CommandBuilder builder = new CommandBuilder(COMMAND_NAME);
        CHANGELOG_FILE_ARG = builder.argument("changelogFile", String.class)
                .required()
                .description("Path to write the generated changelog file to, relative to the working directory")
                .build();
        AUTHOR_ARG = builder.argument("author", String.class)
                .defaultValue(DEFAULT_AUTHOR)
                .description("Author to record on each generated changeSet")
                .build();
        DIFF_TYPES_ARG = builder.argument("diffTypes", String.class)
                .defaultValue(DIFF_TYPE_COLLECTIONS + "," + DIFF_TYPE_INDEXES)
                .description("Comma-separated change categories to generate: collections, indexes")
                .build();
        OVERWRITE_OUTPUT_FILE_ARG = builder.argument("overwriteOutputFile", Boolean.class)
                .defaultValue(false)
                .description("Overwrite the changelog file if it already exists")
                .build();
    }

    @Override
    public String[][] defineCommandNames() {
        return new String[][]{COMMAND_NAME};
    }

    @Override
    public void adjustCommandDefinition(CommandDefinition commandDefinition) {
        commandDefinition.setShortDescription("Generate a baseline MongoDB changelog from existing collections and indexes");
    }

    @Override
    public List<Class<?>> requiredDependencies() {
        return Arrays.asList(Database.class);
    }

    @Override
    public void run(CommandResultsBuilder resultsBuilder) throws Exception {
        final CommandScope commandScope = resultsBuilder.getCommandScope();
        final Database database = (Database) commandScope.getDependency(Database.class);

        if (!(database instanceof MongoLiquibaseDatabase)) {
            throw new LiquibaseException("The mongoGenerateChangelog command is only supported for MongoDB databases.");
        }

        final String changelogFile = commandScope.getArgumentValue(CHANGELOG_FILE_ARG);
        final String author = StringUtil.trimToNull(commandScope.getArgumentValue(AUTHOR_ARG)) == null
                ? DEFAULT_AUTHOR : commandScope.getArgumentValue(AUTHOR_ARG);
        final Set<String> diffTypes = parseDiffTypes(commandScope.getArgumentValue(DIFF_TYPES_ARG));
        final boolean overwriteOutputFile = Boolean.TRUE.equals(commandScope.getArgumentValue(OVERWRITE_OUTPUT_FILE_ARG));

        final PathHandlerFactory pathHandlerFactory = Scope.getCurrentScope().getSingleton(PathHandlerFactory.class);
        final Resource resource = pathHandlerFactory.getResource(changelogFile);
        if (resource.exists() && !overwriteOutputFile) {
            throw new LiquibaseException("Changelog file already exists: " + changelogFile
                    + ". Set overwriteOutputFile to true to overwrite it.");
        }

        final MongoDatabase mongoDatabase = ((MongoLiquibaseDatabase) database).getMongoDatabase();
        final List<Map<String, Object>> changeSets = generateChangeSets(mongoDatabase, author, diffTypes);
        final String yaml = toYaml(changeSets);

        try (OutputStream outputStream = resource.openOutputStream(new OpenOptions().setCreateIfNeeded(true).setTruncate(true))) {
            outputStream.write(yaml.getBytes(StandardCharsets.UTF_8));
        }

        final String output = "Generated changelog with " + changeSets.size() + " changeSet(s) at " + changelogFile;
        handleOutput(resultsBuilder, output);
        resultsBuilder.addResult("output", output);
        resultsBuilder.addResult("changelogFile", changelogFile);
    }

    private static Set<String> parseDiffTypes(String diffTypesArg) throws CommandValidationException {
        final Set<String> result = new LinkedHashSet<>();
        for (String type : diffTypesArg.split(",")) {
            final String trimmed = type.trim().toLowerCase();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!SUPPORTED_DIFF_TYPES.contains(trimmed)) {
                throw new CommandValidationException("Unsupported diffTypes value '" + trimmed
                        + "'. Supported values: " + DIFF_TYPE_COLLECTIONS + ", " + DIFF_TYPE_INDEXES + ".");
            }
            result.add(trimmed);
        }
        if (result.isEmpty()) {
            throw new CommandValidationException("diffTypes must contain at least one of: "
                    + DIFF_TYPE_COLLECTIONS + ", " + DIFF_TYPE_INDEXES + ".");
        }
        return result;
    }

    private static List<Map<String, Object>> generateChangeSets(MongoDatabase mongoDatabase, String author, Set<String> diffTypes) {
        final Logger log = Scope.getCurrentScope().getLog(MongoGenerateChangelogCommandStep.class);
        final List<Map<String, Object>> changeSets = new ArrayList<>();

        for (Document collectionInfo : mongoDatabase.listCollections()) {
            final String collectionName = collectionInfo.getString("name");
            try {
                if (shouldSkipCollection(collectionName, collectionInfo)) {
                    continue;
                }

                final List<Map<String, Object>> changes = new ArrayList<>();
                if (diffTypes.contains(DIFF_TYPE_COLLECTIONS)) {
                    changes.add(createCollectionChange(collectionName, collectionInfo.get("options", Document.class)));
                }
                if (diffTypes.contains(DIFF_TYPE_INDEXES)) {
                    changes.addAll(createIndexChanges(mongoDatabase, collectionName));
                }

                if (!changes.isEmpty()) {
                    changeSets.add(buildChangeSetWrapper(collectionName, author, changes));
                }
            } catch (MongoException e) {
                log.warning("Skipping collection '" + collectionName + "' while generating changelog: " + e.getMessage(), e);
            }
        }

        return changeSets;
    }

    private static boolean shouldSkipCollection(String collectionName, Document collectionInfo) {
        return SKIPPED_COLLECTION_NAMES.contains(collectionName)
                || collectionName.startsWith(SYSTEM_COLLECTION_PREFIX)
                || !"collection".equals(collectionInfo.getString("type"));
    }

    private static Map<String, Object> createCollectionChange(String collectionName, Document options) {
        final Map<String, Object> createCollection = new LinkedHashMap<>();
        createCollection.put("collectionName", collectionName);
        createCollection.put("options", (options == null ? new Document() : options).toJson());

        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("createCollection", createCollection);
        return change;
    }

    private static List<Map<String, Object>> createIndexChanges(MongoDatabase mongoDatabase, String collectionName) {
        final List<Map<String, Object>> indexChanges = new ArrayList<>();
        for (Document index : mongoDatabase.getCollection(collectionName).listIndexes()) {
            if (ID_INDEX_NAME.equals(index.getString("name"))) {
                continue;
            }
            indexChanges.add(createIndexChange(collectionName, index));
        }
        return indexChanges;
    }

    private static Map<String, Object> createIndexChange(String collectionName, Document index) {
        final Document keys = index.get("key", Document.class);

        final Document options = new Document();
        for (Map.Entry<String, Object> entry : index.entrySet()) {
            if (!INDEX_METADATA_EXCLUDED_KEYS.contains(entry.getKey())) {
                options.put(entry.getKey(), entry.getValue());
            }
        }
        options.put("name", index.getString("name"));

        final Map<String, Object> createIndex = new LinkedHashMap<>();
        createIndex.put("collectionName", collectionName);
        createIndex.put("keys", (keys == null ? new Document() : keys).toJson());
        createIndex.put("options", options.toJson());

        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("createIndex", createIndex);
        if (Boolean.TRUE.equals(index.getBoolean("unique"))) {
            change.put("unique", true);
        }
        return change;
    }

    private static Map<String, Object> buildChangeSetWrapper(String collectionName, String author, List<Map<String, Object>> changes) {
        final Map<String, Object> changeSet = new LinkedHashMap<>();
        changeSet.put("id", CHANGE_SET_ID_PREFIX + "-" + collectionName);
        changeSet.put("author", author);
        changeSet.put("changes", changes);

        final Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("changeSet", changeSet);
        return wrapper;
    }

    private static String toYaml(List<Map<String, Object>> changeSets) {
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("databaseChangeLog", changeSets);

        final DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        return new Yaml(dumperOptions).dump(root);
    }
}
