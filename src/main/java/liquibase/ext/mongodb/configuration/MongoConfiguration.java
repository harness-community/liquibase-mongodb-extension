package liquibase.ext.mongodb.configuration;

import liquibase.configuration.AutoloadedConfigurations;
import liquibase.configuration.ConfigurationDefinition;

import static java.lang.Boolean.TRUE;

public class MongoConfiguration implements AutoloadedConfigurations {


    public static final String LIQUIBASE_MONGO_NAMESPACE = "liquibase.mongodb";
    public static final ConfigurationDefinition<Boolean>  ADJUST_TRACKING_TABLES_ON_STARTUP;
    public static final ConfigurationDefinition<Boolean>  SUPPORTS_VALIDATOR;
    public static final ConfigurationDefinition<Boolean>  RETRY_WRITES;
    public static final ConfigurationDefinition<String>   MONGOSH_PATH;
    public static final ConfigurationDefinition<Integer>  MONGOSH_TIMEOUT_SECONDS;
    public static final ConfigurationDefinition<Boolean>  MONGOSH_KEEP_TEMP_FILES;
    public static final ConfigurationDefinition<String>   MONGOSH_TEMP_FILENAME;
    public static final ConfigurationDefinition<String>   MONGOSH_TEMP_DIRECTORY;
    public static final ConfigurationDefinition<String>   MONGOSH_EXTRA_ARGS;
    public static final ConfigurationDefinition<String>   MONGOSH_LOG_FILE;
    public static final ConfigurationDefinition<Boolean>  MONGOSH_TEMP_KEEP;
    public static final ConfigurationDefinition<String>   MONGOSH_TEMP_NAME;

    static {
        ConfigurationDefinition.Builder builder = new ConfigurationDefinition.Builder(LIQUIBASE_MONGO_NAMESPACE);


        ADJUST_TRACKING_TABLES_ON_STARTUP = builder.define("adjustTrackingTablesOnStartup", Boolean.class)
                                .setDescription("Enabling this property will validate History Change Log and Log Lock Collections " +
                "on Startup and adjust if are not up to date with current release." +
                "Worth keeping it disabled and re-enable when upgraded to a new Liquibase version.")
                .setDefaultValue(TRUE)
        .build();

        SUPPORTS_VALIDATOR = builder.define("supportsValidator", Boolean.class)
                .setDescription("Disabling this property will let create the Tracking Collections without validators." +
                        "This will permit usage on Mongo Versions not supporting Validators")
                .setDefaultValue(TRUE)
                .build();

        RETRY_WRITES = builder.define("retryWrites", Boolean.class)
                .setDescription("Setting this property to false will add retryWrites=false to connection URL." +
                        "This will permit usage on Mongo Versions not supporting retryWrites, like Amazon DocumentDB")
                .setDefaultValue(TRUE)
                .build();

        MONGOSH_PATH = builder.define("mongoshPath", String.class)
                .setDescription("Path to mongosh executable. If not specified, searches system PATH and LIQUIBASE_MONGOSH_PATH environment variable.")
                .build();

        MONGOSH_TIMEOUT_SECONDS = builder.define("mongoshTimeoutSeconds", Integer.class)
                .setDescription("Maximum time in seconds to wait for mongosh script execution")
                .setDefaultValue(300)
                .build();

        MONGOSH_KEEP_TEMP_FILES = builder.define("mongoshKeepTempFiles", Boolean.class)
                .setDescription("Whether to retain temporary mongosh script files after execution for debugging")
                .setDefaultValue(false)
                .build();

        MONGOSH_TEMP_FILENAME = builder.define("mongoshTempFilename", String.class)
                .setDescription("Custom filename pattern for temporary mongosh script files")
                .build();

        MONGOSH_TEMP_DIRECTORY = builder.define("mongoshTempDirectory", String.class)
                .setDescription("Directory for temporary mongosh script files. If not specified, uses system temp directory")
                .setDefaultValue(System.getProperty("java.io.tmpdir"))
                .build();

        MONGOSH_EXTRA_ARGS = builder.define("mongoshExtraArgs", String.class)
                .setDescription("Additional command line arguments passed to mongosh executable")
                .setDefaultValue("--quiet --norc")
                .build();

        MONGOSH_LOG_FILE = builder.define("mongoshLogFile", String.class)
                .setDescription("File path for capturing mongosh execution output and errors")
                .build();

        MONGOSH_TEMP_KEEP = builder.define("mongoshTempKeep", Boolean.class)
                .setDescription("Whether to retain temporary mongosh script files after execution")
                .setDefaultValue(false)
                .build();

        MONGOSH_TEMP_NAME = builder.define("mongoshTempName", String.class)
                .setDescription("Custom filename pattern for temporary mongosh script files")
                .build();
    }
}
