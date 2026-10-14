package liquibase.ext.mongodb.change;

import liquibase.change.Change;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.RanChangeSet;
import liquibase.changelog.visitor.ValidatingVisitor;
import liquibase.exception.ValidationFailedException;
import liquibase.ext.mongodb.tools.MongoshRunner;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MongoshOidcChangeLogValidationTest extends AbstractMongoChangeTest {

    private static final String OIDC_URL =
            "mongodb://localhost:27017/test_db?authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT:k8s";

    private DatabaseChangeLog changeLog;

    @BeforeEach
    void setUp() {
        super.setUp();
        changeLog = new DatabaseChangeLog("changelog.xml");
        connectWith(OIDC_URL);
    }

    @Test
    @SneakyThrows
    void alreadyRanMongoChangeSetIsNotRevalidated() {
        final ChangeSet ran = mongoChangeSet("ran", null);
        final ValidatingVisitor visitor = new ValidatingVisitor(Collections.singletonList(new RanChangeSet(ran)));

        visitor.visit(ran, changeLog, database, new HashSet<>());

        assertThat(visitor.validationPassed()).isTrue();
    }

    @Test
    @SneakyThrows
    void alreadyRanRunOnChangeAndRunAlwaysChangeSetsAreStillValidated() {
        final ChangeSet runOnChange = changeSet("runOnChange", false, true, mongoChange());
        final ChangeSet runAlways = changeSet("runAlways", true, false, mongoChange());
        final ValidatingVisitor visitor = new ValidatingVisitor(
                Arrays.asList(new RanChangeSet(runOnChange), new RanChangeSet(runAlways)));

        visitor.visit(runOnChange, changeLog, database, new HashSet<>());
        visitor.visit(runAlways, changeLog, database, new HashSet<>());

        assertThat(visitor.getValidationErrors().getErrorMessages())
                .hasSize(2)
                .allMatch(msg -> msg.contains(MongoshRunner.MSG_MONGOSH_UNSUPPORTED_WITH_OIDC));
    }

    @Test
    @SneakyThrows
    void pendingChangeSetWithOnValidationFailMarkRanPasses() {
        final ChangeSet pending = mongoChangeSet("markRan", null);
        pending.setOnValidationFail(ChangeSet.ValidationFailOption.MARK_RAN);
        final ValidatingVisitor visitor = new ValidatingVisitor(Collections.emptyList());

        visitor.visit(pending, changeLog, database, new HashSet<>());

        assertThat(visitor.validationPassed()).isTrue();
    }

    @Test
    @SneakyThrows
    void pendingMongoFileChangeSetFails() {
        final MongoshFileChange change = new MongoshFileChange();
        change.setPath("scripts/test.js");
        assertPendingFails(changeSet("mongoFile", false, false, change));
    }

    @Test
    @SneakyThrows
    void pendingMongoChangeSetFailsNamingTheChangeSet() {
        assertPendingFails(mongoChangeSet("pending", "mongosh"));
    }

    @Test
    @SneakyThrows
    void pendingMongoChangeSetWithoutRunWithStillFails() {
        assertPendingFails(mongoChangeSet("noRunWith", null));
    }

    @SneakyThrows
    private void assertPendingFails(final ChangeSet pending) {
        final ValidatingVisitor visitor = new ValidatingVisitor(Collections.emptyList());

        visitor.visit(pending, changeLog, database, new HashSet<>());

        assertThat(visitor.validationPassed()).isFalse();
        assertThatExceptionOfType(ValidationFailedException.class)
                .isThrownBy(() -> {
                    throw new ValidationFailedException(visitor);
                })
                .withMessageContaining(pending.getId())
                .withMessageContaining(MongoshRunner.MSG_MONGOSH_UNSUPPORTED_WITH_OIDC);
    }

    private ChangeSet mongoChangeSet(final String id, final String runWith) {
        final ChangeSet changeSet = new ChangeSet(id, "author", false, false, "changelog.xml", null, null, runWith, null, true, null, changeLog);
        changeSet.addChange(mongoChange());
        return changeSet;
    }

    private ChangeSet changeSet(final String id, final boolean alwaysRun, final boolean runOnChange, final Change change) {
        final ChangeSet changeSet = new ChangeSet(id, "author", alwaysRun, runOnChange, "changelog.xml", null, null, null, null, true, null, changeLog);
        changeSet.addChange(change);
        return changeSet;
    }

    private static MongoshChange mongoChange() {
        final MongoshChange change = new MongoshChange();
        change.setMongo("db.users.find({});");
        return change;
    }
}
