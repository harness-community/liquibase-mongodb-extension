package liquibase.ext.mongodb.command;

import com.mongodb.client.MongoDatabase;
import liquibase.GlobalConfiguration;
import liquibase.command.CommandResultsBuilder;
import liquibase.command.CommandScope;
import liquibase.exception.CommandValidationException;
import liquibase.exception.LiquibaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.lockservice.LockService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecuteNativeCommandStepTest {

    @TempDir
    Path tempDir;

    @Test
    void commandIsDiscoverable() {
        assertThatCode(() -> new CommandScope("executeNative"))
                .doesNotThrowAnyException();
    }

    @Test
    void run_withCommandObject_runsMongoRunCommand() throws Exception {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1));

        MongoLiquibaseDatabase database = mock(MongoLiquibaseDatabase.class);
        when(database.getMongoDatabase()).thenReturn(mongoDatabase);

        LockService lockService = mock(LockService.class);

        CommandScope scope = new CommandScope("executeNative")
                .addArgumentValue(ExecuteNativeCommandStep.COMMAND_ARG, "{\"ping\": 1}")
                .provideDependency(liquibase.database.Database.class, database)
                .provideDependency(LockService.class, lockService);

        ByteArrayOutputStream commandOut = new ByteArrayOutputStream();
        CommandResultsBuilder resultsBuilder = newResultsBuilder(scope, commandOut);
        new ExecuteNativeCommandStep().run(resultsBuilder);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(mongoDatabase, times(1)).runCommand(captor.capture());
        assertThat(captor.getValue()).containsEntry("ping", 1);

        Object outputObj = resultsBuilder.getResult("output");
        assertThat(outputObj).isInstanceOf(String.class);
        assertThat((String) outputObj).contains("Successfully Executed").contains("ping").contains("Response");

        assertThat(commandOut.toString(GlobalConfiguration.OUTPUT_FILE_ENCODING.getCurrentValue()))
                .contains("Successfully Executed");
    }

    @Test
    void run_withCommandArray_runsEachCommand() throws Exception {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1));

        MongoLiquibaseDatabase database = mock(MongoLiquibaseDatabase.class);
        when(database.getMongoDatabase()).thenReturn(mongoDatabase);

        LockService lockService = mock(LockService.class);

        CommandScope scope = new CommandScope("executeNative")
                .addArgumentValue(ExecuteNativeCommandStep.COMMAND_ARG, "[{\"ping\": 1}, {\"buildInfo\": 1}]")
                .provideDependency(liquibase.database.Database.class, database)
                .provideDependency(LockService.class, lockService);

        CommandResultsBuilder resultsBuilder = newResultsBuilder(scope, new ByteArrayOutputStream());
        new ExecuteNativeCommandStep().run(resultsBuilder);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(mongoDatabase, times(2)).runCommand(captor.capture());

        List<Document> executedCommands = captor.getAllValues();
        assertThat(executedCommands)
                .anySatisfy(d -> assertThat(d).containsEntry("ping", 1))
                .anySatisfy(d -> assertThat(d).containsEntry("buildInfo", 1));
    }

    @Test
    void run_withFile_readsAndExecutes() throws Exception {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1));

        MongoLiquibaseDatabase database = mock(MongoLiquibaseDatabase.class);
        when(database.getMongoDatabase()).thenReturn(mongoDatabase);

        LockService lockService = mock(LockService.class);

        Path file = tempDir.resolve("execute-native.json");
        Files.write(file, "{\"ping\": 1}".getBytes(StandardCharsets.UTF_8));

        CommandScope scope = new CommandScope("executeNative")
                .addArgumentValue(ExecuteNativeCommandStep.FILE_ARG, file.toString())
                .provideDependency(liquibase.database.Database.class, database)
                .provideDependency(LockService.class, lockService);

        CommandResultsBuilder resultsBuilder = newResultsBuilder(scope, new ByteArrayOutputStream());
        new ExecuteNativeCommandStep().run(resultsBuilder);

        verify(mongoDatabase, times(1)).runCommand(any(Document.class));
    }

    @Test
    void run_withCommandAndFile_throwsValidationError() throws Exception {
        MongoLiquibaseDatabase database = mock(MongoLiquibaseDatabase.class);
        LockService lockService = mock(LockService.class);

        Path file = tempDir.resolve("execute-native.json");
        Files.write(file, "{\"ping\": 1}".getBytes(StandardCharsets.UTF_8));

        CommandScope scope = new CommandScope("executeNative")
                .addArgumentValue(ExecuteNativeCommandStep.COMMAND_ARG, "{\"ping\": 1}")
                .addArgumentValue(ExecuteNativeCommandStep.FILE_ARG, file.toString())
                .provideDependency(liquibase.database.Database.class, database)
                .provideDependency(LockService.class, lockService);

        CommandResultsBuilder resultsBuilder = newResultsBuilder(scope, new ByteArrayOutputStream());
        assertThatThrownBy(() -> new ExecuteNativeCommandStep().run(resultsBuilder))
                .isInstanceOf(CommandValidationException.class)
                .hasMessageContaining("--command")
                .hasMessageContaining("--file");
    }

    @Test
    void run_withNoCommandOrFile_throwsValidationError() throws Exception {
        MongoLiquibaseDatabase database = mock(MongoLiquibaseDatabase.class);
        LockService lockService = mock(LockService.class);

        CommandScope scope = new CommandScope("executeNative")
                .provideDependency(liquibase.database.Database.class, database)
                .provideDependency(LockService.class, lockService);

        CommandResultsBuilder resultsBuilder = newResultsBuilder(scope, new ByteArrayOutputStream());
        assertThatThrownBy(() -> new ExecuteNativeCommandStep().run(resultsBuilder))
                .isInstanceOf(CommandValidationException.class)
                .hasMessageContaining("--command")
                .hasMessageContaining("--file");
    }

    @Test
    void run_withNonMongoDatabase_throwsHelpfulError() throws Exception {
        liquibase.database.Database nonMongoDb = mock(liquibase.database.Database.class);
        LockService lockService = mock(LockService.class);

        CommandScope scope = new CommandScope("executeNative")
                .addArgumentValue(ExecuteNativeCommandStep.COMMAND_ARG, "{\"ping\": 1}")
                .provideDependency(liquibase.database.Database.class, nonMongoDb)
                .provideDependency(LockService.class, lockService);

        CommandResultsBuilder resultsBuilder = newResultsBuilder(scope, new ByteArrayOutputStream());
        assertThatThrownBy(() -> new ExecuteNativeCommandStep().run(resultsBuilder))
                .isInstanceOf(LiquibaseException.class)
                .hasMessage("The execute-native command is only supported for MongoDB databases.");
    }

    private static CommandResultsBuilder newResultsBuilder(CommandScope commandScope, OutputStream outputStream) {
        try {
            Constructor<CommandResultsBuilder> ctor =
                    CommandResultsBuilder.class.getDeclaredConstructor(CommandScope.class, OutputStream.class);
            ctor.setAccessible(true);
            return ctor.newInstance(commandScope, outputStream);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create CommandResultsBuilder for tests", e);
        }
    }
}
