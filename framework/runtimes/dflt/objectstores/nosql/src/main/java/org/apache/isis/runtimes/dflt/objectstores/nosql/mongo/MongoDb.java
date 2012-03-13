/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.runtimes.dflt.objectstores.nosql.mongo;

import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;

import org.apache.isis.runtimes.dflt.objectstores.nosql.KeyCreator;
import org.apache.isis.runtimes.dflt.objectstores.nosql.NoSqlCommandContext;
import org.apache.isis.runtimes.dflt.objectstores.nosql.NoSqlDataDatabase;
import org.apache.isis.runtimes.dflt.objectstores.nosql.NoSqlStoreException;
import org.apache.isis.runtimes.dflt.objectstores.nosql.StateReader;
import org.apache.isis.runtimes.dflt.objectstores.nosql.StateWriter;
import org.apache.isis.runtimes.dflt.runtime.persistence.objectstore.transaction.PersistenceCommand;
import org.apache.log4j.Logger;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.ObjectId;
import com.mongodb.DB.WriteConcern;

public class MongoDb implements NoSqlDataDatabase {

	private static final String SERIALNUMBERS_COLLECTION_NAME = "serialnumbers";

	private static final Logger LOG = Logger.getLogger(MongoDb.class);
	
	private static final int DEFAULT_PORT = 27017;

    private final String host;
    private final int port;
    private final String dbName;
    private final KeyCreator keyCreator;
    
	private Mongo mongo;
	private DB db;

    public MongoDb(final String host, final int port, final String name, final KeyCreator keyCreator) {
        this.host = host;
        this.port = port == 0 ? DEFAULT_PORT : port;
        this.dbName = name;
        this.keyCreator = keyCreator;
    }

    public KeyCreator getKeyCreator() {
        return keyCreator;
    }

    @Override
    public void open() {
        try {
        	mongo = new Mongo(host, port);
            db = mongo.getDB(dbName);
            db.setWriteConcern(WriteConcern.STRICT);
            
            LOG.info("opened database (" + dbName + "): " + db);
        } catch (final UnknownHostException e) {
            throw new NoSqlStoreException(e);
        } catch (final MongoException e) {
            throw new NoSqlStoreException(e);
        }
    }

    @Override
    public void close() {
        // TODO is there a close mechanism?
    }

    public NoSqlCommandContext createTransactionContext() {
        return null;
    }

    //////////////////////////////////////////////////
    // contains data
    //////////////////////////////////////////////////

    @Override
    public boolean containsData() {
        return db.getCollectionNames().size() > 0;
    }

    
    //////////////////////////////////////////////////
    // serial numbers
    //////////////////////////////////////////////////
    
    @Override
    public long nextSerialNumberBatch(final String name, final int batchSize) {
        long next = readSerialNumber();
        writeSerialNumber(next + batchSize);
        return next + 1;
    }

    private void writeSerialNumber(final long serialNumber) {
        final DBCollection system = db.getCollection(SERIALNUMBERS_COLLECTION_NAME);
        DBObject object = system.findOne();
        if (object == null) {
            object = new BasicDBObject();
        }
        object.put("next-id", Long.toString(serialNumber));
        system.save(object);
        LOG.info("serial number written: " + serialNumber);
    }

    private long readSerialNumber() {
        final DBCollection system = db.getCollection(SERIALNUMBERS_COLLECTION_NAME);
        final DBObject data = system.findOne();
        if (data == null) {
            return 0;
        } else {
            final String number = (String) data.get("next-id");
            LOG.info("serial number read: " + number);
            return Long.valueOf(number);
        }
    }

    //////////////////////////////////////////////////
    // hasInstances, instancesOf
    //////////////////////////////////////////////////

    @Override
    public boolean hasInstances(final String specificationName) {
        final DBCollection instances = db.getCollection(specificationName);
        return instances.getCount() > 0;
    }

    @Override
    public Iterator<StateReader> instancesOf(final String specificationName) {
        final DBCollection instances = db.getCollection(specificationName);
        final DBCursor cursor = instances.find();
        LOG.info("searching for instances of: " + specificationName);
        return new Iterator<StateReader>() {
            @Override
            public boolean hasNext() {
                return cursor.hasNext();
            }

            @Override
            public StateReader next() {
                return new MongoStateReader(cursor.next());
            }

            @Override
            public void remove() {
                throw new NoSqlStoreException("Can't remove elements");
            }

        };
    }

    @Override
    public StateReader getInstance(final String key, final String specName) {
        return new MongoStateReader(db, specName, key);
    }

    //////////////////////////////////////////////////
    // write, delete
    //////////////////////////////////////////////////

    public StateWriter createStateWriter(final String specName) {
        return new MongoStateWriter(db, specName);
    }


    @Override
    public void write(final List<PersistenceCommand> commands) {
        final NoSqlCommandContext context = new MongoClientCommandContext(db);
        for (final PersistenceCommand command : commands) {
            command.execute(context);
        }
    }

    public void delete(final String specificationName, final String key) {
        final DBCollection instances = db.getCollection(specificationName);
        final ObjectId id = new ObjectId(key);
        final DBObject object = instances.findOne(id);
        instances.remove(object);
        LOG.info("removed " + key);
    }


    //////////////////////////////////////////////////
    // services
    //////////////////////////////////////////////////

    @Override
    public void addService(final String name, final String key) {
        final DBCollection services = db.getCollection("services");
        services.insert(new BasicDBObject().append("name", name).append("key", key));
        LOG.info("service added " + name + ":" + key);
    }

    @Override
    public String getService(final String name) {
        final DBCollection services = db.getCollection("services");
        final DBObject object = services.findOne(new BasicDBObject().append("name", name));
        if (object == null) {
            return null;
        } else {
            final String id = (String) object.get("key");
            LOG.info("service found " + name + ":" + id);
            return id;
        }
    }
}
