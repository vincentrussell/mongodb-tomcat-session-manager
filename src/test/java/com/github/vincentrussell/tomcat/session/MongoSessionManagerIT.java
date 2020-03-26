package com.github.vincentrussell.tomcat.session;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.apache.juli.logging.Log;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.*;
import org.junit.rules.Timeout;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SocketUtils;

import javax.print.Doc;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MongoSessionManagerIT {


    public static final String USERNAME = "my_user";
    public static final String PASSWORD = "password";
    public static final String VERSION = "3.6.5";
    private static int port = SocketUtils.findAvailableTcpPort();
    private static EmbeddedMongo embeddedMongo;
    private Context mockContext;
    private Engine mockEngine;
    private Log mockLog;

    @Rule
    public Timeout timeout = new Timeout(180000);
    private MongoDatabase mongoDatabase;
    private MongoCollection<Document> mongoCollection;

    @BeforeClass
    public static void beforeClass() throws IOException {
        embeddedMongo = new EmbeddedMongo(port, VERSION, USERNAME, PASSWORD);
    }

    @AfterClass
    public static void afterClass() throws IOException {
        embeddedMongo.close();
    }

    @Before
    public void before() {
        mockContext = mock(Context.class);
        mockEngine = mock(Engine.class);
        mockLog = mock(Log.class);
        when(mockContext.getName()).thenReturn("name");
        when(mockContext.getSessionTimeout()).thenReturn(1);
        when(mockContext.getParent()).thenReturn(mockEngine);
        when(mockContext.getLogger()).thenReturn(mockLog);
        mongoDatabase = embeddedMongo.getMongoClient().getDatabase(EmbeddedMongo.DEFAULT_DATABASE_NAME);
        mongoCollection = mongoDatabase.getCollection(MongoSessionManager.USER_SESSIONS);
        mongoCollection.drop();
    }

    @Test
    public void createCollectionOnStartup() {
        MongoClient mongoClient = embeddedMongo.getMongoClient();
        MongoDatabase mongoDatabase = mongoClient.getDatabase(EmbeddedMongo.DEFAULT_DATABASE_NAME);
        MongoCollection mongoCollection = mongoDatabase.getCollection(MongoSessionManager.USER_SESSIONS);
        assertThat(mongoDatabase.listCollectionNames(), not((hasItems(MongoSessionManager.USER_SESSIONS))));

        MongoSessionManager mongoSessionManager = new MongoSessionManager.Builder()
                .setMongoClient(mongoClient)
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();

        assertThat(mongoDatabase.listCollectionNames(), (hasItems(MongoSessionManager.USER_SESSIONS)));
    }

    @Test
    public void saveLoadAndRemoveSession() throws LifecycleException, IOException {
        MongoSessionManager mongoSessionManager = new MongoSessionManager.Builder()
                .setMongoClient(embeddedMongo.getMongoClient())
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();
        mongoSessionManager.start();
        assertEquals(0, mongoSessionManager.getSessionCounter());
        String sessionId = mongoSessionManager.getSessionIdGenerator().generateSessionId();
        assertNotNull(sessionId);
        StandardSession session = (StandardSession) mongoSessionManager.createSession(sessionId);
        String key = "key";
        String value = "value";
        session.setAttribute(key, value);
        assertNotNull(session);
        assertEquals(1, mongoSessionManager.getSessionCounter());
        HashMap<String, String> loadedSession = mongoSessionManager.getSession(sessionId);
        assertEquals(value, loadedSession.get(key));
        assertEquals(0, mongoCollection.count());
        mongoSessionManager.processPersistenceChecks();
        assertEquals(1, mongoCollection.count());
        Document document = mongoCollection.find(new Document(new BasicDBObject("_id", sessionId))).iterator().next();
        assertEquals(sessionId, document.get("_id"));
        mongoSessionManager = new MongoSessionManager.Builder()
                .setMongoClient(embeddedMongo.getMongoClient())
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();
        mongoSessionManager.start();
        Session sessionOfDatabase = mongoSessionManager.findSession(sessionId);
        assertEquals(sessionId, sessionOfDatabase.getId());
        assertEquals(1, mongoCollection.count());
        mongoSessionManager.remove(sessionOfDatabase);
        assertEquals(0, mongoCollection.count());
    }


    @Test
    public void processExpired() throws LifecycleException, IOException {
        MongoSessionManager mongoSessionManager = new MongoSessionManager.Builder()
                .setMongoClient(embeddedMongo.getMongoClient())
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();
        mongoSessionManager.start();
        assertEquals(0, mongoSessionManager.getSessionCounter());
        String sessionId = mongoSessionManager.getSessionIdGenerator().generateSessionId();
        assertNotNull(sessionId);
        StandardSession session = (StandardSession) mongoSessionManager.createSession(sessionId);
        long now = System.currentTimeMillis();
        long newLastAccessedTime = now - TimeUnit.HOURS.toMillis(5);
        ReflectionTestUtils.setField(session, "lastAccessedTime", newLastAccessedTime);
        mongoSessionManager.processPersistenceChecks();
        assertEquals(1, mongoCollection.count());
        Document document = mongoCollection.find(new Document(new BasicDBObject("_id", sessionId))).iterator().next();
        StandardSession loadedSession = ReflectionTestUtils.invokeMethod(mongoSessionManager.getStore(), "deserializeSession", ((Binary)document.get(MongoDbSessionStore.DATA_FIELD)).getData());
        ReflectionTestUtils.setField(loadedSession, "thisAccessedTime", newLastAccessedTime);
        byte[] serializedObject = ReflectionTestUtils.invokeMethod(mongoSessionManager.getStore(), "serializeSession", loadedSession);
        document.put(MongoDbSessionStore.DATA_FIELD, serializedObject);
        mongoCollection.replaceOne(new Document(new BasicDBObject("_id", document.get("_id"))), document);
        mongoSessionManager.processExpires();
        assertEquals(0, mongoCollection.count());
    }

    @Test
    public void clearStore() throws LifecycleException, IOException {
        MongoSessionManager mongoSessionManager = new MongoSessionManager.Builder()
                .setMongoClient(embeddedMongo.getMongoClient())
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();
        mongoSessionManager.start();
        assertEquals(0, mongoSessionManager.getSessionCounter());
        String sessionId = mongoSessionManager.getSessionIdGenerator().generateSessionId();
        assertNotNull(sessionId);
        StandardSession session = (StandardSession) mongoSessionManager.createSession(sessionId);
        mongoSessionManager.processPersistenceChecks();
        assertEquals(1, mongoCollection.count());
        mongoSessionManager.clearStore();
        assertEquals(0, mongoCollection.count());
    }



    @Test
    public void loadOnStartup() throws LifecycleException, IOException {
        MongoSessionManager mongoSessionManager = new MongoSessionManager.Builder()
                .setMongoClient(embeddedMongo.getMongoClient())
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();
        mongoSessionManager.start();
        assertEquals(0, mongoSessionManager.getSessionCounter());
        String sessionId = mongoSessionManager.getSessionIdGenerator().generateSessionId();
        assertNotNull(sessionId);
        StandardSession session = (StandardSession) mongoSessionManager.createSession(sessionId);
        mongoSessionManager.processPersistenceChecks();
        mongoSessionManager = new MongoSessionManager.Builder()
                .setMongoClient(embeddedMongo.getMongoClient())
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();
        mongoSessionManager.start();
        Session[] sessions = mongoSessionManager.findSessions();
        assertEquals(1, sessions.length);
    }


    @Test
    public void usernameAndPassword() {
        MongoClient mongoClient = embeddedMongo.getMongoClient();
        MongoDatabase mongoDatabase = mongoClient.getDatabase(EmbeddedMongo.DEFAULT_DATABASE_NAME);
        MongoCollection mongoCollection = mongoDatabase.getCollection(MongoSessionManager.USER_SESSIONS);
        assertThat(mongoDatabase.listCollectionNames(), not((hasItems(MongoSessionManager.USER_SESSIONS))));

        MongoSessionManager mongoSessionManager = new MongoSessionManager.Builder()
                .setUsername(USERNAME)
                .setPassword(PASSWORD)
                .setHosts("localhost:" + port)
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();

        assertThat(mongoDatabase.listCollectionNames(), (hasItems(MongoSessionManager.USER_SESSIONS)));
    }


}
