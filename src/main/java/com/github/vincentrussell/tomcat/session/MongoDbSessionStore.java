package com.github.vincentrussell.tomcat.session;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.StoreBase;
import org.bson.Document;
import org.bson.types.Binary;

import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MongoDbSessionStore extends StoreBase {

    public static final String ID_FIELD = "_id";
    public static final String PRINCIPAL_NAME_FIELD = "principalName";
    public static final String CREATION_TIME_FIELD = "creationTime";
    public static final String EXPIRATION_TIME = "expirationTime";
    public static final String DATA_FIELD = "data";
    public static final String LAST_MODIFIED_FIELD = "lastModified";
    private final MongoClient mongoClient;
    private final MongoDatabase mongoDatabase;
    private final MongoCollection<Document> mongoCollection;

    protected MongoDbSessionStore(final Manager manager, final MongoClient mongoClient,
                        final String databaseName, final String collectionName) {
        setManager(manager);
        this.mongoClient = mongoClient;
        this.mongoDatabase = mongoClient.getDatabase(databaseName);
        this.mongoCollection = mongoDatabase.getCollection(collectionName);

        if (!Lists.newArrayList(mongoDatabase.listCollectionNames()).contains(mongoCollection)) {
            try {
                mongoDatabase.createCollection(collectionName);
            } catch (MongoCommandException e) {
                if (e.getErrorCode() == 48) {
                    manager.getContext().getLogger().info("collection" +
                            " already exists");
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    public String getStoreName() {
        return getClass().getName();
    }


    @Override
    public int getSize() throws IOException {
        return Long.valueOf(mongoCollection.count()).intValue();
    }


    @Override
    public String[] expiredKeys() throws IOException {
        return keys(true);
    }

    @Override
    public String[] keys() throws IOException {
        return keys(false);
    }

    private String[] keys(boolean expiredOnly) {
        BasicDBObject query = new BasicDBObject();
        if (expiredOnly) {
            query.append(EXPIRATION_TIME, new BasicDBObject("$lt", System.currentTimeMillis()));
        }
        List<String> keys = Lists.newArrayList(Iterables.transform(
                this.mongoCollection.find(getDocument(query))
                        .sort(getDocument(new BasicDBObject(ID_FIELD, 1))), new Function<Document, String>() {
                    @Override
                    public String apply(Document document) {
                        return document.getString(ID_FIELD);
                    }
                }));

        return keys.toArray(new String[keys.size()]);
    }


    @Override
    public Session load(String id) throws IOException {
        final ArrayList<Document> mongoSession = Lists.newArrayList(mongoCollection.find(
                getDocument(new BasicDBObject(ID_FIELD, id))));
        if (mongoSession != null && !mongoSession.isEmpty()) {
            final Binary data = (Binary) mongoSession.get(0).get(DATA_FIELD);
            if (data != null) {
                return deserializeSession(data.getData());
            }
        }
        throw new IOException("count of find record with id " + id);
    }

    private StandardSession deserializeSession(final byte[] data) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
             ObjectInputStream ois = getObjectInputStream(bis)) {
            StandardSession session = (StandardSession) this.manager.createEmptySession();
            try {
                session.readObjectData(ois);
                session.setManager(this.manager);
                return session;
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        }
    }



    @Override
    public void remove(String id) throws IOException {
        try {
            this.mongoCollection.deleteMany(getDocument(new BasicDBObject(ID_FIELD, id)));
        } catch (MongoException e) {
            this.manager.getContext().getLogger().fatal(
                    "Unable to remove sessions for [" + id + ":"
                            + this.manager.getContext().getName() + "] from MongoDB", e);
            throw e;
        }
    }

    @Override
    public void clear() throws IOException {
        try {
            this.mongoCollection.deleteMany(getDocument(new BasicDBObject()));
        } catch (MongoException e) {
            /* for some reason we couldn't save the data */
            this.manager.getContext().getLogger().fatal("Unable to remove sessions for ["
                    + this.manager.getContext().getName() + "] from MongoDB", e);
            throw e;
        }
    }


    @Override
    public void save(Session session) throws IOException {
        byte[] serializedObject = serializeSession(session);
        BasicDBObject mongoSession = new BasicDBObject();
        mongoSession.put(ID_FIELD, session.getIdInternal());
        mongoSession.put(PRINCIPAL_NAME_FIELD, session.getPrincipal() != null
                ? session.getPrincipal().getName() : "unknownPrincipal");
        mongoSession.put(CREATION_TIME_FIELD, session.getCreationTime());
        mongoSession.put(EXPIRATION_TIME, session.getLastAccessedTime()
                + TimeUnit.SECONDS.toMillis(session.getMaxInactiveInterval()));
        mongoSession.put(DATA_FIELD, serializedObject);
        mongoSession.put(LAST_MODIFIED_FIELD, Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime());

        try {
            /* update the object in the collection, inserting if necessary */
            this.mongoCollection.replaceOne(getDocument(new BasicDBObject(ID_FIELD, session.getId())),
                    getDocument(mongoSession), new UpdateOptions().upsert(true));
        } catch (MongoException e) {
            /* for some reason we couldn't save the data */
            this.manager.getContext().getLogger().fatal("Unable to save session to MongoDB", e);
            throw e;
        }
    }

    private static Document getDocument(DBObject doc) {
        if (doc == null) {
            return null;
        }
        return new Document(doc.toMap());
    }

    private byte[] serializeSession(Session session) throws IOException {
        try ( ByteArrayOutputStream bos = new ByteArrayOutputStream();
              ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            ((StandardSession)session).writeObjectData(oos);
            return bos.toByteArray();
        } catch (Exception e) {
            if (IOException.class.isInstance(e)) {
                throw (IOException) e;
            }
            throw new IOException(e);
        }
    }
}
