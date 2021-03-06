/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.qpid.server.store.berkeleydb;

import static org.apache.qpid.server.store.berkeleydb.BDBUtils.DEFAULT_DATABASE_CONFIG;
import static org.apache.qpid.server.store.berkeleydb.BDBUtils.abortTransactionSafely;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import org.slf4j.Logger;

import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ModelVersion;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.berkeleydb.tuple.MapBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.UUIDTupleBinding;
import org.apache.qpid.server.store.preferences.PreferenceRecord;
import org.apache.qpid.server.store.preferences.PreferenceRecordImpl;
import org.apache.qpid.server.store.preferences.PreferenceStore;
import org.apache.qpid.server.store.preferences.PreferenceStoreUpdater;

abstract class AbstractBDBPreferenceStore implements PreferenceStore
{
    private static final String PREFERENCES_DB_NAME = "USER_PREFERENCES";
    private static final String PREFERENCES_VERSION_DB_NAME = "USER_PREFERENCES_VERSION";
    private AtomicReference<StoreState> _storeState = new AtomicReference<>(StoreState.CLOSED);

    @Override
    public Collection<PreferenceRecord> openAndLoad(final PreferenceStoreUpdater updater) throws StoreException
    {
        if (!_storeState.compareAndSet(StoreState.CLOSED, StoreState.OPENING))
        {
            throw new IllegalStateException(String.format("PreferenceStore cannot be opened when in state '%s'",
                                                          getStoreState()));
        }

        EnvironmentFacade environmentFacade = getEnvironmentFacade();

        try
        {
            Collection<PreferenceRecord> records = new LinkedHashSet<>();

            Cursor cursor = null;

            try
            {
                cursor = getPreferencesDb().openCursor(null, null);
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry value = new DatabaseEntry();
                UUIDTupleBinding keyBinding = UUIDTupleBinding.getInstance();
                MapBinding valueBinding = MapBinding.getInstance();

                while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
                {
                    UUID preferenceId = keyBinding.entryToObject(key);
                    Map<String, Object> preferenceAttributes = valueBinding.entryToObject(value);
                    PreferenceRecord record = new PreferenceRecordImpl(preferenceId, preferenceAttributes);
                    records.add(record);
                }
            }
            catch (RuntimeException e)
            {
                throw environmentFacade.handleDatabaseException("Cannot visit messages", e);
            }
            finally
            {
                if (cursor != null)
                {
                    try
                    {
                        cursor.close();
                    }
                    catch (RuntimeException e)
                    {
                        throw environmentFacade.handleDatabaseException("Cannot close cursor", e);
                    }
                }
            }

            ModelVersion currentVersion =
                    new ModelVersion(BrokerModel.MODEL_MAJOR_VERSION, BrokerModel.MODEL_MINOR_VERSION);
            ModelVersion storedVersion = getStoredVersion();
            if (storedVersion.lessThan(currentVersion))
            {
                final Collection<UUID> ids = new HashSet<>();
                for (PreferenceRecord record : records)
                {
                    ids.add(record.getId());
                }

                records = updater.updatePreferences(storedVersion.toString(), records);
                replace(ids, records);
            }

            _storeState.set(StoreState.OPENED);
            return records;
        }
        catch (Exception e)
        {
            close();
            throw e;
        }
    }

    @Override
    public void updateOrCreate(final Collection<PreferenceRecord> preferenceRecords)
    {
        if (!getStoreState().equals(StoreState.OPENED))
        {
            throw new IllegalStateException("PreferenceStore is not opened");
        }

        if (preferenceRecords.isEmpty())
        {
            return;
        }

        EnvironmentFacade environmentFacade = getEnvironmentFacade();
        Transaction txn = null;
        try
        {
            txn = environmentFacade.beginTransaction(null);
            updateOrCreateInternal(txn, preferenceRecords);
            txn.commit();
            txn = null;
        }
        catch (RuntimeException e)
        {
            throw environmentFacade.handleDatabaseException("Error on preferences updateOrCreate: " + e.getMessage(),
                                                            e);
        }
        finally
        {
            if (txn != null)
            {
                abortTransactionSafely(txn, environmentFacade);
            }
        }
    }

    @Override
    public void replace(final Collection<UUID> preferenceRecordsToRemove,
                        final Collection<PreferenceRecord> preferenceRecordsToAdd)
    {
        if (!getStoreState().equals(StoreState.OPENED))
        {
            throw new IllegalStateException("PreferenceStore is not opened");
        }

        if (preferenceRecordsToRemove.isEmpty() && preferenceRecordsToAdd.isEmpty())
        {
            return;
        }

        EnvironmentFacade environmentFacade = getEnvironmentFacade();
        Transaction txn = null;
        try
        {
            txn = environmentFacade.beginTransaction(null);
            Database preferencesDb = getPreferencesDb();
            DatabaseEntry key = new DatabaseEntry();
            UUIDTupleBinding keyBinding = UUIDTupleBinding.getInstance();
            for (UUID id : preferenceRecordsToRemove)
            {
                getLogger().debug("Removing preference {}", id);
                keyBinding.objectToEntry(id, key);
                OperationStatus status = preferencesDb.delete(txn, key);
                if (status == OperationStatus.NOTFOUND)
                {
                    getLogger().debug("Preference {} not found", id);
                }
            }
            updateOrCreateInternal(txn, preferenceRecordsToAdd);
            txn.commit();
            txn = null;
        }
        catch (RuntimeException e)
        {
            throw environmentFacade.handleDatabaseException("Error on replacing of preferences: " + e.getMessage(), e);
        }
        finally
        {
            if (txn != null)
            {
                abortTransactionSafely(txn, environmentFacade);
            }
        }
    }

    protected abstract EnvironmentFacade getEnvironmentFacade();

    protected abstract Logger getLogger();

    boolean closeInternal()
    {
        while (true)
        {
            StoreState storeState = getStoreState();
            if (storeState.equals(StoreState.OPENED) || storeState.equals(StoreState.OPENING))
            {
                if (_storeState.compareAndSet(storeState, StoreState.CLOSING))
                {
                    break;
                }
            }
            else if (storeState.equals(StoreState.CLOSED) || storeState.equals(StoreState.CLOSING))
            {
                return false;
            }
        }

        getEnvironmentFacade().closeDatabase(PREFERENCES_DB_NAME);

        _storeState.set(StoreState.CLOSED);
        return true;
    }

    StoreState getStoreState()
    {
        return _storeState.get();
    }

    private void updateOrCreateInternal(final Transaction txn,
                                        final Collection<PreferenceRecord> preferenceRecords)
    {
        Database preferencesDb = getPreferencesDb();
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();
        UUIDTupleBinding keyBinding = UUIDTupleBinding.getInstance();
        MapBinding valueBinding = MapBinding.getInstance();
        for (PreferenceRecord record : preferenceRecords)
        {
            keyBinding.objectToEntry(record.getId(), key);
            valueBinding.objectToEntry(record.getAttributes(), value);
            OperationStatus status = preferencesDb.put(txn, key, value);
            if (status != OperationStatus.SUCCESS)
            {
                throw new StoreException(String.format("Error writing preference with id '%s' (status %s)",
                                                       record.getId(),
                                                       status.name()));
            }
        }
    }

    private Database getPreferencesDb()
    {
        return getEnvironmentFacade().openDatabase(PREFERENCES_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getPreferencesVersionDb()
    {
        Database preferencesVersionDb;
        try
        {
            DatabaseConfig config = new DatabaseConfig().setTransactional(true).setAllowCreate(false);
            preferencesVersionDb = getEnvironmentFacade().openDatabase(PREFERENCES_VERSION_DB_NAME, config);
        }
        catch (DatabaseNotFoundException e)
        {
            preferencesVersionDb = getEnvironmentFacade().openDatabase(PREFERENCES_VERSION_DB_NAME, DEFAULT_DATABASE_CONFIG);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            StringBinding.stringToEntry(BrokerModel.MODEL_VERSION, key);
            LongBinding.longToEntry(System.currentTimeMillis(), value);
            preferencesVersionDb.put(null, key, value);
        }

        return preferencesVersionDb;
    }

    private ModelVersion getStoredVersion() throws RuntimeException
    {
        Cursor cursor = null;

        try
        {
            cursor = getPreferencesVersionDb().openCursor(null, null);

            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();

            ModelVersion storedVersion = null;
            while (cursor.getNext(key, value, null) == OperationStatus.SUCCESS)
            {
                String versionString = StringBinding.entryToString(key);
                ModelVersion version = ModelVersion.fromString(versionString);
                if (storedVersion == null || storedVersion.lessThan(version))
                {
                    storedVersion = version;
                }
            }
            if (storedVersion == null)
            {
                throw new StoreException("No preference version information.");
            }
            return storedVersion;
        }
        catch (RuntimeException e)
        {
            throw getEnvironmentFacade().handleDatabaseException("Cannot visit preference version", e);
        }
        finally
        {
            if (cursor != null)
            {
                try
                {
                    cursor.close();
                    getEnvironmentFacade().closeDatabase(PREFERENCES_VERSION_DB_NAME);
                }
                catch (RuntimeException e)
                {
                    throw getEnvironmentFacade().handleDatabaseException("Cannot close cursor", e);
                }
            }
        }
    }

    enum StoreState
    {
        CLOSED, OPENING, OPENED, CLOSING;
    }
}
