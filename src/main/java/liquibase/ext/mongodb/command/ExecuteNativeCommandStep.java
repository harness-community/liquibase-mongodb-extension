package liquibase.ext.mongodb.command;

import liquibase.GlobalConfiguration;
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
import liquibase.ext.mongodb.statement.BsonUtils;
import liquibase.ext.mongodb.statement.RunCommandStatement;
import liquibase.lockservice.LockService;
import liquibase.resource.PathHandlerFactory;
import liquibase.resource.Resource;
import liquibase.util.FileUtil;
import liquibase.util.StreamUtil;
import liquibase.util.StringUtil;
import org.bson.Document;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Execute raw MongoDB commands expressed as JSON.
 * <p>
 * Accepts either:
 * - a single JSON object, e.g. {@code {"buildInfo": 1}}
 * - a JSON array of objects, e.g. {@code [{"ping": 1}, {"buildInfo": 1}]}
 */
public class ExecuteNativeCommandStep extends AbstractCommandStep {

    public static final String[] COMMAND_NAME = {"executeNative"};
    public static final CommandArgumentDefinition<String> COMMAND_ARG;
    public static final CommandArgumentDefinition<String> FILE_ARG;

    static {
        CommandBuilder builder = new CommandBuilder(COMMAND_NAME);
        COMMAND_ARG = builder.argument("command", String.class)
                .description("MongoDB command JSON to execute (object or array of objects)")
                .build();
        FILE_ARG = builder.argument("file", String.class)
                .description("MongoDB command JSON file to execute (object or array of objects)")
                .build();
    }

    @Override
    public String[][] defineCommandNames() {
        return new String[][]{COMMAND_NAME};
    }

    @Override
    public void adjustCommandDefinition(CommandDefinition commandDefinition) {
        commandDefinition.setShortDescription("Execute a raw MongoDB command JSON string or file");
    }

    @Override
    public List<Class<?>> requiredDependencies() {
        return Arrays.asList(Database.class, LockService.class);
    }

    @Override
    public void run(CommandResultsBuilder resultsBuilder) throws Exception {
        final CommandScope commandScope = resultsBuilder.getCommandScope();
        final Database database = (Database) commandScope.getDependency(Database.class);

        final String commandJson = StringUtil.trimToNull(commandScope.getArgumentValue(COMMAND_ARG));
        final String file = StringUtil.trimToNull(commandScope.getArgumentValue(FILE_ARG));

        if (commandJson != null && file != null) {
            throw new CommandValidationException("Both --command and --file were provided. Please specify only one.");
        }

        final String commandText = getCommandFromSource(commandJson, file);
        final List<Document> commands = parseCommands(commandText);

        final StringBuilder out = new StringBuilder();
        for (Document command : commands) {
            Document response = executeRunCommand(database, command);
            out.append("Successfully Executed: ").append(BsonUtils.toJson(command)).append("\n");
            out.append("Response: ").append(response == null ? "-- No Response --" : response.toJson()).append("\n\n");
        }

        database.commit();
        handleOutput(resultsBuilder, out.toString());
        resultsBuilder.addResult("output", out.toString());
    }

    private static List<Document> parseCommands(String commandText) throws LiquibaseException {
        String trimmed = StringUtil.trimToNull(commandText);
        if (trimmed == null) {
            throw new CommandValidationException("No MongoDB command was provided. Specify --command or --file.");
        }

        trimmed = trimmed.trim();
        if (trimmed.startsWith("[")) {
            List<Document> docs = BsonUtils.orEmptyList(trimmed);
            if (docs.isEmpty()) {
                throw new CommandValidationException("No MongoDB commands found in the provided JSON array.");
            }
            return docs;
        }

        return Collections.singletonList(BsonUtils.orEmptyDocument(trimmed));
    }

    private static Document executeRunCommand(Database database, Document command) throws LiquibaseException {
        if (!(database instanceof MongoLiquibaseDatabase)) {
            throw new LiquibaseException("The execute-native command is only supported for MongoDB databases.");
        }

        MongoLiquibaseDatabase mongoDatabase = (MongoLiquibaseDatabase) database;
        return new RunCommandStatement(command).run(mongoDatabase);
    }

    private static String getCommandFromSource(String commandJson, String file) throws IOException, LiquibaseException {
        if (file == null) {
            return commandJson;
        }

        final PathHandlerFactory pathHandlerFactory = Scope.getCurrentScope().getSingleton(PathHandlerFactory.class);
        Resource resource = pathHandlerFactory.getResource(file);
        if (!resource.exists()) {
            throw new LiquibaseException(FileUtil.getFileNotFoundMessage(file));
        }
        return StreamUtil.readStreamAsString(resource.openInputStream());
    }
}

