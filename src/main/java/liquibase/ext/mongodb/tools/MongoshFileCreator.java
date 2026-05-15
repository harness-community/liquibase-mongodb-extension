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
import liquibase.changelog.ChangeSet;
import liquibase.ext.mongodb.configuration.MongoConfiguration;
import liquibase.logging.Logger;
import liquibase.util.FilenameUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MongoshFileCreator {

    private final ChangeSet changeSet;
    private final String filenameOverride;
    private final String pathOverride;
    private final boolean overwriteFile;
    private final boolean keepFile;

    public MongoshFileCreator(ChangeSet changeSet, String filenameOverride, String pathOverride,
                              boolean overwriteFile, boolean keepFile) {
        this.changeSet = changeSet;
        this.filenameOverride = filenameOverride;
        this.pathOverride = pathOverride;
        this.overwriteFile = overwriteFile;
        this.keepFile = keepFile;
    }

    public MongoshFileCreator(ChangeSet changeSet) {
        this(changeSet,
             MongoConfiguration.MONGOSH_TEMP_FILENAME.getCurrentValue(),
             MongoConfiguration.MONGOSH_TEMP_DIRECTORY.getCurrentValue(),
             true,
             Boolean.TRUE.equals(MongoConfiguration.MONGOSH_KEEP_TEMP_FILES.getCurrentValue()));
    }

    public File generateTemporaryFile(String fileExtension) throws IOException {
        Logger log = Scope.getCurrentScope().getLog(getClass());
        String tempFileName = buildFilename(fileExtension);
        log.info("Creating temporary " + fileExtension + " file for changeset '" +
                 changeSet.getId() + ":" + changeSet.getAuthor() + "'");

        File tempFile;
        if (isBlank(pathOverride)) {
            if (isBlank(filenameOverride)) {
                tempFile = File.createTempFile(tempFileName, fileExtension);
            } else {
                String tempDir = System.getProperty("java.io.tmpdir");
                tempFile = new File(tempDir, tempFileName + fileExtension);
                if (!overwriteFile && tempFile.exists()) {
                    throw new IOException("File already exists and overwrite is disabled: " + tempFile.getAbsolutePath());
                }
                if (!tempFile.createNewFile() && !overwriteFile) {
                    throw new IOException("Failed to create new file: " + tempFile.getAbsolutePath());
                }
            }
        } else {
            Path customPath = Paths.get(pathOverride);
            if (!customPath.toFile().exists() && !customPath.toFile().mkdirs()) {
                throw new IOException("Failed to create directory: " + pathOverride);
            }

            tempFile = new File(customPath.toFile(), tempFileName + fileExtension);
            if (!overwriteFile && tempFile.exists()) {
                throw new IOException("File already exists and overwrite is disabled: " + tempFile.getAbsolutePath());
            }
            if (!tempFile.createNewFile() && !overwriteFile) {
                throw new IOException("Failed to create new file: " + tempFile.getAbsolutePath());
            }
        }

        configureFileCleanup(tempFile, log);
        log.info("Created temporary file: " + tempFile.getAbsolutePath());
        return tempFile;
    }

    private String buildFilename(String fileExtension) {
        String filename;
        if (hasText(filenameOverride)) {
            filename = filenameOverride;
        } else {
            filename = "liquibase" + fileExtension + "-"
                    + changeSet.getId() + "-"
                    + changeSet.getAuthor() + "-"
                    + System.currentTimeMillis();
        }

        return FilenameUtil.sanitizeFileName(filename);
    }

    private void configureFileCleanup(File tempFile, Logger log) {
        if (keepFile) {
            log.info("Temporary file will be retained for debugging: " + tempFile.getAbsolutePath());
        } else {
            tempFile.deleteOnExit();
            log.fine("Temporary file scheduled for cleanup on exit: " + tempFile.getAbsolutePath());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean hasText(String value) {
        return !isBlank(value);
    }
}
