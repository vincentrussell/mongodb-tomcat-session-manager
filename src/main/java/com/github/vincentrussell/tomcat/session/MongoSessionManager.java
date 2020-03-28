package com.github.vincentrussell.tomcat.session;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.session.PersistentManagerBase;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * Extension of {@link PersistentManagerBase} meant to be used with the
 * {@link MongoSessionStore}.  The only thing that this class really does
 * is that it loads the sessions from the database on startup.
 *
 * @author Vincent Russell
 */
public class MongoSessionManager extends PersistentManagerBase {

    public MongoSessionManager() {
        maxIdleSwap = 0;
        maxIdleBackup = 5;
        saveOnRestart = true;
        processExpiresFrequency = 6;
    }

    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();
        // Load unloaded sessions, if any
        try {
            load();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            getContext().getLogger().error(sm.getString("standardManager.managerLoad"), t);
        }
    }

    /**
     * Builder for {@link MongoSessionManager}
     */
    public static class Builder {

        private String databaseName;
        private String adminDatabase = MongoSessionStore.DEFAULT_ADMIN_DATABASE;
        private String collectionName = MongoSessionStore.USER_SESSIONS;
        private SessionIdGenerator sessionIdGenerator = new StandardSessionIdGenerator();
        private String username;
        private String password;
        private String hosts;
        private Context context;

        /**
         * the mongo database to use
         * @param databaseName
         * @return
         */
        public Builder setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * the mongo admin database to use for login
         * @param adminDatabase
         * @return
         */
        public Builder setAdminDatabase(String adminDatabase) {
            this.adminDatabase = adminDatabase;
            return this;
        }

        /**
         * the collection to store the session data in
         * @param collectionName
         * @return
         */
        public Builder setCollectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        /**
         * override the session id generator
         * @param sessionIdGenerator
         * @return
         */
        public Builder setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
            this.sessionIdGenerator = sessionIdGenerator;
            return this;
        }

        /**
         * the login username
         * @param username
         * @return
         */
        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        /**
         * the login password
         * @param password
         * @return
         */
        public Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        /**
         * the mongo hosts to login to.  Can use commas to set multiple hosts
         * @param hosts
         * @return
         */
        public Builder setHosts(String hosts) {
            this.hosts = hosts;
            return this;
        }

        /**
         * set the tomcat context
         * @param context
         * @return
         */
        public Builder setContext(Context context) {
            this.context = context;
            return this;
        }

        /**
         * build it!
         * @return
         * @throws LifecycleException
         */
        public MongoSessionManager build() throws LifecycleException {
            MongoSessionManager mongoSessionManager = new MongoSessionManager();
            mongoSessionManager.setSessionIdGenerator(sessionIdGenerator);
            mongoSessionManager.setContext(context);
            MongoSessionStore mongoSessionStore = new MongoSessionStore();
            mongoSessionStore.setManager(mongoSessionManager);
            mongoSessionStore.setDatabaseName(databaseName);
            mongoSessionStore.setAdminDatabase(adminDatabase);
            mongoSessionStore.setHosts(hosts);
            mongoSessionStore.setUsername(username);
            mongoSessionStore.setPassword(password);
            mongoSessionManager.setStore(mongoSessionStore);
            mongoSessionStore.start();
            mongoSessionManager.start();
            return mongoSessionManager;
        }

    }

}
