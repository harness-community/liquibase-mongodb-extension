package liquibase.ext.mongodb.tools;

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
import liquibase.change.core.ExecuteShellCommandChange;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.exception.LiquibaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.ext.mongodb.configuration.MongoConfiguration;
import liquibase.ext.mongodb.database.MongoConnection;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.logging.Logger;
import liquibase.resource.Resource;
import liquibase.resource.ResourceAccessor;
import liquibase.servicelocator.LiquibaseService;
import liquibase.sql.Sql;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.TimeoutException;

@LiquibaseService(skip = true)
public class MongoshRunner extends ExecuteShellCommandChange {

    private static final String EXECUTABLE_NAME = "mongosh";
    private static final String MONGOSH_CONF = "liquibase.mongosh.conf";
    private static final ResourceBundle MONGOSH_BUNDLE;
    private static final String MSG_UNABLE_TO_RUN_MONGOSH;
    private static final String MSG_MONGOSH_UNSUPPORTED_WITH_OIDC;

    static {
        try {
            MONGOSH_BUNDLE = ResourceBundle.getBundle("liquibase/i18n/liquibase-mongosh");
            MSG_UNABLE_TO_RUN_MONGOSH = MONGOSH_BUNDLE.getString("unable.to.run.mongosh");
            MSG_MONGOSH_UNSUPPORTED_WITH_OIDC = MONGOSH_BUNDLE.getString("mongosh.unsupported.with.oidc");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load mongosh resource bundle", e);
        }
    }

    private ChangeSet changeSet;
    private Sql[] sqlStrings;
    private File outFile;
    private Boolean keepTempFile = false;
    private List<String> args = new ArrayList<>();
    private String tempName;
    private String tempPath;
    private String logFile;
    private Integer timeout;
    private File mongoshExec;

    public MongoshRunner() {
    }

    public MongoshRunner(ChangeSet changeSet, Sql[] sqlStrings) {
        this.changeSet = changeSet;
        this.sqlStrings = sqlStrings;
        this.setTimeout("1800");
    }

    @Override
    protected List<String> createFinalCommandArray(Database database) {
        MongoConnection connection = (MongoConnection) ((MongoLiquibaseDatabase) database).getConnection();
        if (connection.isOidcAuth()) {
            throw new UnexpectedLiquibaseException(MSG_MONGOSH_UNSUPPORTED_WITH_OIDC);
        }

        loadMongoshProperties();
        List<String> commandArray = super.createFinalCommandArray(database);

        try {
            writeSqlStrings();
        } catch (Exception e) {
            throw new UnexpectedLiquibaseException(e);
        }

        if (!args.isEmpty()) {
            commandArray.addAll(args);
        }

        if (sqlStrings != null) {
            commandArray.add(connection.getConnectionString().getConnectionString());

            if (outFile != null) {
                commandArray.add("--file");
                commandArray.add(outFile.getAbsolutePath());
            } else {
                commandArray.add("--eval");
                commandArray.add(sqlStrings[0].toSql());
            }
        } else {
            commandArray.add("--version");
        }

        String commandLine = String.join(" ", commandArray);
        Scope.getCurrentScope().getLog(getClass()).info(
                "mongosh command:\n" + commandLine.replaceAll("://.*:.*@", "://<credentials>@"));
        return commandArray;
    }

    public void executeCommand(Database database) throws Exception {
        try {
            this.finalCommandArray = this.createFinalCommandArray(database);
            super.executeCommand(database);
        } catch (TimeoutException e) {
            try {
                Thread.sleep(10000L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }

            this.processResult(0, null, null, database);
            String message = e.getMessage() + System.lineSeparator()
                    + "Error: The mongosh executable failed to return a response with the configured timeout. "
                    + "Please check liquibase.mongosh.timeout specified in liquibase.mongosh.conf file, "
                    + "the LIQUIBASE_MONGOSH_TIMEOUT environment variable, or other config locations. "
                    + System.lineSeparator();
            Scope.getCurrentScope().getUI().sendMessage("WARNING: " + message);
            Scope.getCurrentScope().getLog(MongoshRunner.class).warning(message);
            throw new LiquibaseException(e);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("mongosh")) {
                throw new LiquibaseException(MSG_UNABLE_TO_RUN_MONGOSH, e);
            }
            throw new LiquibaseException(e);
        } catch (Exception e) {
            throw new LiquibaseException(e);
        } finally {
            if (outFile != null && outFile.exists() && Boolean.TRUE.equals(keepTempFile)) {
                Scope.getCurrentScope().getLog(getClass()).info(
                        "Mongosh run script can be located at: " + outFile.getAbsolutePath());
            }
        }
    }

    @Override
    protected void processResult(int returnCode, String errorStreamOut, String infoStreamOut, Database database) {
        if (logFile != null && outFile != null) {
            try {
                if (hasText(infoStreamOut)) {
                    Files.write(Paths.get(logFile), infoStreamOut.getBytes(),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                if (hasText(errorStreamOut)) {
                    Files.write(Paths.get(logFile), errorStreamOut.getBytes(),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            } catch (IOException e) {
                throw new UnexpectedLiquibaseException(e);
            }
        }

        if (returnCode != 0 && hasText(infoStreamOut)) {
            String returnString = getCommandString() + " returned a code of " + returnCode + "\n" + infoStreamOut;
            throw new UnexpectedLiquibaseException(returnString);
        }
        super.processResult(returnCode, errorStreamOut, infoStreamOut, database);
    }

    private void writeSqlStrings() throws Exception {
        if (sqlStrings != null && sqlStrings.length != 0) {
            Logger log = Scope.getCurrentScope().getLog(getClass());
            log.info("Creating the mongosh run script");

            MongoshFileCreator mongoshFileCreator = new MongoshFileCreator(
                    changeSet,
                    tempName,
                    tempPath,
                    true,
                    keepTempFile == null
                            ? Boolean.TRUE.equals(MongoConfiguration.MONGOSH_TEMP_KEEP.getCurrentValue())
                            : keepTempFile);
            try {
                outFile = mongoshFileCreator.generateTemporaryFile(".txt");
            } catch (IOException e) {
                throw new UnexpectedLiquibaseException(e);
            }

            Path path = Paths.get(outFile.getAbsolutePath());
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                for (Sql sql : sqlStrings) {
                    String line = sql.toSql();
                    line = line.replace("\r", "");
                    writer.write(line);
                }
                writer.write(";\n");
            }
        }
    }

    private void loadMongoshProperties() {
        this.setExecutable(EXECUTABLE_NAME);

        Properties properties = getPropertiesFromConf(MONGOSH_CONF);
        setupConfProperties(properties);
        assignPropertiesFromConfiguration();
        handleMongoShExecutable(mongoshExec);
        handleTimeout(timeout);
        logProperties();
    }

    public Properties getPropertiesFromConf(String configFile) {
        Properties properties = new Properties();
        ResourceAccessor resourceAccessor = Scope.getCurrentScope().getResourceAccessor();
        InputStream is = null;

        try {
            Resource resource = resourceAccessor.get(configFile);
            if (!resource.exists()) {
                Scope.getCurrentScope().getLog(getClass()).info(
                        String.format("No configuration file named '%s' found.", configFile));
            } else {
                Scope.getCurrentScope().getLog(getClass()).info(
                        String.format("%s configuration file located at '%s'.", configFile, resource.getUri()));
                is = resource.openInputStream();
                properties.load(is);
            }
        } catch (IOException e) {
            throw new UnexpectedLiquibaseException(e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception ignored) {
                // Ignore.
            }
        }

        return properties;
    }

    private void setupConfProperties(Properties properties) {
        if (properties.containsKey("liquibase.mongosh.keep.temp")) {
            keepTempFile = Boolean.parseBoolean(properties.getProperty("liquibase.mongosh.keep.temp"));
        }
        if (properties.containsKey("liquibase.mongosh.keep.temp.name")) {
            tempName = properties.getProperty("liquibase.mongosh.keep.temp.name");
        }
        if (properties.containsKey("liquibase.mongosh.keep.temp.path")) {
            tempPath = properties.getProperty("liquibase.mongosh.keep.temp.path");
        }
        if (properties.containsKey("liquibase.mongosh.logFile")) {
            logFile = properties.getProperty("liquibase.mongosh.logFile");
        }
        if (properties.containsKey("liquibase.mongosh.path")) {
            mongoshExec = new File(properties.getProperty("liquibase.mongosh.path"));
        }
        if (properties.containsKey("liquibase.mongosh.timeout")) {
            timeout = determineTimeout(properties);
        }
        if (properties.containsKey("liquibase.mongosh.args")) {
            handleArgs(properties.getProperty("liquibase.mongosh.args"));
        }
    }

    private void assignPropertiesFromConfiguration() {
        keepTempFile = MongoConfiguration.MONGOSH_TEMP_KEEP.getCurrentValue() != null
                ? (Boolean) MongoConfiguration.MONGOSH_TEMP_KEEP.getCurrentValue()
                : keepTempFile;
        tempName = MongoConfiguration.MONGOSH_TEMP_NAME.getCurrentValue() != null
                ? (String) MongoConfiguration.MONGOSH_TEMP_NAME.getCurrentValue()
                : tempName;
        tempPath = MongoConfiguration.MONGOSH_TEMP_DIRECTORY.getCurrentValue() != null
                ? (String) MongoConfiguration.MONGOSH_TEMP_DIRECTORY.getCurrentValue()
                : tempPath;
        logFile = MongoConfiguration.MONGOSH_LOG_FILE.getCurrentValue() != null
                ? (String) MongoConfiguration.MONGOSH_LOG_FILE.getCurrentValue()
                : logFile;
        timeout = MongoConfiguration.MONGOSH_TIMEOUT_SECONDS.getCurrentValue() != null
                ? MongoConfiguration.MONGOSH_TIMEOUT_SECONDS.getCurrentValue()
                : timeout;
        if (MongoConfiguration.MONGOSH_PATH.getCurrentValue() != null) {
            mongoshExec = new File((String) MongoConfiguration.MONGOSH_PATH.getCurrentValue());
        }
        if (MongoConfiguration.MONGOSH_EXTRA_ARGS.getCurrentValue() != null) {
            handleArgs((String) MongoConfiguration.MONGOSH_EXTRA_ARGS.getCurrentValue());
        }
    }

    private int determineTimeout(Properties properties) {
        String timeoutString = properties.getProperty("liquibase.mongosh.timeout");
        if (timeoutString == null) {
            return -1;
        }

        try {
            return Integer.parseInt(timeoutString);
        } catch (Exception e) {
            throw new UnexpectedLiquibaseException("Invalid value '" + timeoutString
                    + "' for property 'liquibase.mongosh.timeout'. Must be a valid integer.");
        }
    }

    private void logProperties() {
        if (keepTempFile != null) {
            Scope.getCurrentScope().getLog(getClass()).info(
                    "Executing 'mongosh' with a keep temp file value of '" + keepTempFile + "'");
        }
        if (tempPath != null) {
            Scope.getCurrentScope().getLog(getClass()).info(
                    "Executing 'mongosh' with a keep temp file path value of '" + tempPath + "'");
        }
        if (tempName != null) {
            Scope.getCurrentScope().getLog(getClass()).info(
                    "Executing 'mongosh' with a keep temp file name value of '" + tempName + "'");
        }
        if (logFile != null) {
            Scope.getCurrentScope().getLog(getClass()).info(
                    "Executing 'mongosh' with a log file value of '" + logFile + "'");
        }
    }

    private void handleArgs(String argsString) {
        if (argsString != null) {
            argsString = argsString.trim();
            Scope.getCurrentScope().getLog(getClass()).info(
                    "Executing 'mongosh' with extra arguments of '" + argsString + "'");
            args = splitAndTrim(argsString);
        }
    }

    private void handleTimeout(Integer timeout) {
        if (timeout != null) {
            this.setTimeout(String.valueOf(timeout));
            Scope.getCurrentScope().getLog(getClass()).info(
                    "Executing 'mongosh' with a timeout of '" + timeout + "'");
        }
    }

    private void handleMongoShExecutable(File mongoshExec) {
        if (mongoshExec != null) {
            if (!mongoshExec.exists()) {
                throw new UnexpectedLiquibaseException(
                        "The executable for the native executor 'mongosh' cannot be found at path '"
                                + mongoshExec.getAbsolutePath()
                                + "' as specified in the liquibase.mongosh.conf file, "
                                + "the LIQUIBASE_MONGOSH_* environment variables, or other config locations.");
            } else if (!mongoshExec.canExecute()) {
                throw new UnexpectedLiquibaseException(
                        "The 'mongosh' executable in the liquibase.mongosh.conf file at "
                                + mongoshExec.getAbsolutePath() + " cannot be executed.");
            } else {
                try {
                    this.setExecutable(mongoshExec.getCanonicalPath());
                    Scope.getCurrentScope().getLog(getClass()).info(
                            "Using the 'mongosh' executable located at: '" + mongoshExec.getCanonicalPath() + "'");
                    this.mongoshExec = mongoshExec;
                } catch (IOException e) {
                    throw new UnexpectedLiquibaseException(e);
                }
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static List<String> splitAndTrim(String value) {
        List<String> parts = new ArrayList<>();
        for (String token : value.split(" ")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }
}
