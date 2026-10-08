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

import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.ChangeLogLockOwner;
import lombok.Getter;

import java.util.Date;

public class MongoChangeLogLock extends DatabaseChangeLogLock {

    public static class Fields {
        public static final String id = "_id";
        public static final String lockGranted = "lockGranted";
        public static final String lockedBy = "lockedBy";
        public static final String locked = "locked";
    }

    @Getter
    private final Boolean locked;

    public MongoChangeLogLock() {
        this(1, new Date(), "NoArgConstructor", true);
    }

    public MongoChangeLogLock(final Integer id, final Date lockGranted, final String lockedBy, final Boolean locked) {
        super(id, lockGranted, lockedBy);
        this.locked = locked;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public static String formLockedBy() {
        return ChangeLogLockOwner.format();
    }

}
