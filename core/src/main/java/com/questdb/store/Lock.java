/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (c) 2014-2016 Appsicle
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.questdb.store;

import com.questdb.ex.JournalRuntimeException;
import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressFBWarnings({"EXS_EXCEPTION_SOFTENING_NO_CONSTRAINTS"})
public final class Lock {

    private static final Log LOG = LogFactory.getLog(Lock.class);

    private final AtomicInteger refCount = new AtomicInteger(0);
    private RandomAccessFile file;
    private FileLock lock;
    private File lockName;
    private File location;

    @SuppressFBWarnings({"PATH_TRAVERSAL_IN"})
    Lock(File location, boolean shared) {

        try {
            this.location = location;
            this.lockName = new File(location + ".lock");
            this.file = new RandomAccessFile(lockName, "rw");
            int i = 0;
            while (true) {
                try {
                    this.lock = file.getChannel().tryLock(i, 1, shared);
                    break;
                } catch (OverlappingFileLockException e) {
                    if (shared) {
                        i++;
                    } else {
                        this.lock = null;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new JournalRuntimeException(e);
        }
    }

    public synchronized boolean isValid() {
        return lock != null && lock.isValid();
    }

    @Override
    public String toString() {
        return "Lock{" +
                "lockName=" + lockName +
                ", isShared=" + (lock == null ? "NULL" : lock.isShared()) +
                ", isValid=" + (lock == null ? "NULL" : lock.isValid()) +
                ", refCount=" + refCount.get() +
                '}';
    }

    void decrementRefCount() {
        refCount.decrementAndGet();
    }

    synchronized void delete() {
        if (!lockName.delete()) {
            LOG.error().$("Could not delete lock: ").$(lockName).$();
//            throw new JournalRuntimeException("Could not delete lock: %s", lockName);
        }
    }

    File getLocation() {
        return location;
    }

    int getRefCount() {
        return refCount.get();
    }

    void incrementRefCount() {
        refCount.incrementAndGet();
    }

    synchronized void release() {
        try {
            if (isValid()) {
                lock.release();
                lock = null;
            }

            if (file != null) {
                file.close();
                file = null;
            }
        } catch (IOException e) {
            throw new JournalRuntimeException(e);
        }
    }
}
