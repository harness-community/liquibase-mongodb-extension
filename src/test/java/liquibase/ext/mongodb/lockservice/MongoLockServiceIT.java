package liquibase.ext.mongodb.lockservice;

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

import liquibase.exception.LockException;
import liquibase.ext.AbstractMongoIntegrationTest;
import liquibase.lockservice.ChangeLogLockOwner;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockServiceFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoLockServiceIT extends AbstractMongoIntegrationTest {

    public MongoLockService lockService;

    @BeforeEach
    protected void setUpEach() {
        super.setUpEach();
        System.setProperty(ChangeLogLockOwner.ENV_HARNESS_LOCKEDBY_PREFIX, "pfx");
        lockService = (MongoLockService) LockServiceFactory.getInstance().getLockService(database);
        lockService.reset();
    }

    @AfterEach
    void tearDownPrefix() {
        System.clearProperty(ChangeLogLockOwner.ENV_HARNESS_LOCKEDBY_PREFIX);
    }

    @SneakyThrows
    @Test
    void init() {
        assertThat(lockService.getHasDatabaseChangeLogLockTable()).isNull();
        assertThat(lockService.hasChangeLogLock()).isFalse();
        assertThat(lockService.existsRepository()).isFalse();
        lockService.init();
        assertThat(lockService.getHasDatabaseChangeLogLockTable()).isTrue();
        assertThat(lockService.existsRepository()).isTrue();
        assertThat(lockService.hasChangeLogLock()).isFalse();
        assertThat(lockService.isLocked()).isFalse();
    }

    @SneakyThrows
    @Test
    void acquireLock() {
        lockService.reset();
        assertThat(lockService.hasChangeLogLock()).isFalse();
        assertThat(lockService.existsRepository()).isFalse();
        lockService.init();
        assertThat(lockService.hasChangeLogLock()).isFalse();
        assertThat(lockService.existsRepository()).isTrue();
        final boolean acquiredLock = lockService.acquireLock();
        assertThat(acquiredLock).isTrue();
        assertThat(lockService.hasChangeLogLock()).isTrue();
        assertThat(lockService.isLocked()).isTrue();
        final DatabaseChangeLogLock[] locks = lockService.listLocks();
        assertThat(locks).hasSize(1);
    }

    @Test
    void waitForLock() throws LockException {
        assertThat(lockService.hasChangeLogLock()).isFalse();
        lockService.waitForLock();
        assertThat(lockService.hasChangeLogLock()).isTrue();
        assertThat(lockService.listLocks()).hasSize(1);
    }

    @SneakyThrows
    @Test
    void releaseLock() {
        assertThat(lockService.hasChangeLogLock()).isFalse();
        assertThat(lockService.acquireLock()).isTrue();
        assertThat(lockService.hasChangeLogLock()).isTrue();
        assertThat(lockService.listLocks()).hasSize(1);
        lockService.releaseLock();
        assertThat(lockService.hasChangeLogLock()).isFalse();
        assertThat(lockService.isLocked()).isFalse();
        assertThat(lockService.listLocks()).isEmpty();
    }

    @SneakyThrows
    @Test
    void forceReleaseLock() {
        assertThat(lockService.hasChangeLogLock()).isFalse();
        lockService.forceReleaseLock();
        assertThat(lockService.getHasDatabaseChangeLogLockTable()).isTrue();
        assertThat(lockService.hasChangeLogLock()).isFalse();
        assertThat(lockService.listLocks()).isEmpty();

        // Force release with existing lock
        lockService.reset();
        assertThat(lockService.acquireLock()).isTrue();
        assertThat(lockService.listLocks()).hasSize(1);
        lockService.reset();
        lockService.forceReleaseLock();
        assertThat(lockService.hasChangeLogLock()).isFalse();
        assertThat(lockService.isLocked()).isFalse();
        assertThat(lockService.listLocks()).isEmpty();
    }

    @SneakyThrows
    @Test
    void destroy() {
        lockService.init();
        assertThat(lockService.getHasDatabaseChangeLogLockTable()).isTrue();
        assertThat(lockService.existsRepository()).isTrue();
        lockService.reset();
        lockService.destroy();
        assertThat(lockService.getHasDatabaseChangeLogLockTable()).isNull();
        assertThat(lockService.existsRepository()).isFalse();
    }
}
