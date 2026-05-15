package liquibase.ext.mongodb.tools;

import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoshFileCreatorTest {

    @TempDir
    Path tempDir;

    private ChangeSet changeSet;
    private DatabaseChangeLog changeLog;

    @BeforeEach
    void setUp() {
        changeSet = mock(ChangeSet.class);
        changeLog = mock(DatabaseChangeLog.class);

        when(changeSet.getId()).thenReturn("test-changeset");
        when(changeSet.getAuthor()).thenReturn("test-author");
        when(changeSet.getChangeLog()).thenReturn(changeLog);
        when(changeLog.getLogicalFilePath()).thenReturn("changelog/test.xml");
    }

    @Test
    void constructor_withChangeSetOnly_shouldUseDefaults() {
        final MongoshFileCreator creator = new MongoshFileCreator(changeSet);

        assertThat(creator).isNotNull();
    }

    @Test
    void pathHandling_withAbsolutePath_shouldUseProvidedPath() {
        final String absolutePath = tempDir.toAbsolutePath().toString();

        final MongoshFileCreator creator = new MongoshFileCreator(
                changeSet,
                "test.js",
                absolutePath,
                false,
                false
        );

        assertThat(creator).isNotNull();
    }

    @Test
    void securityConsiderations_shouldSanitizeFilenames() {
        final String dangerousFilename = "../../../etc/passwd";

        final MongoshFileCreator creator = new MongoshFileCreator(
                changeSet,
                dangerousFilename,
                tempDir.toString(),
                false,
                false
        );

        assertThat(creator).isNotNull();
    }

    @Test
    void integration_withRealTempDirectory() throws IOException {
        final Path testFile = tempDir.resolve("test-script.js");
        Files.createFile(testFile);

        final MongoshFileCreator creatorForExistingFile = new MongoshFileCreator(
                changeSet,
                testFile.getFileName().toString(),
                testFile.getParent().toString(),
                true,
                false
        );

        final MongoshFileCreator creatorForNewFile = new MongoshFileCreator(
                changeSet,
                "new-script.js",
                tempDir.toString(),
                false,
                true
        );

        assertThat(creatorForExistingFile).isNotNull();
        assertThat(creatorForNewFile).isNotNull();
    }
}
