package com.github.vincentrussell.tomcat.session;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.session.PersistentManagerBase;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.tomcat.util.ExceptionUtils;

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

    public static class Builder {

        private String databaseName;
        private String adminDatabase = MongoSessionStore.DEFAULT_ADMIN_DATABASE;
        private String collectionName = MongoSessionStore.USER_SESSIONS;
        private SessionIdGenerator sessionIdGenerator = new StandardSessionIdGenerator();
        private String username;
        private String password;
        private String hosts;
        private Context context;

        public Builder setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder setAdminDatabase(String adminDatabase) {
            this.adminDatabase = adminDatabase;
            return this;
        }

        public Builder setCollectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
            this.sessionIdGenerator = sessionIdGenerator;
            return this;
        }

        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder setHosts(String hosts) {
            this.hosts = hosts;
            return this;
        }

        public Builder setContext(Context context) {
            this.context = context;
            return this;
        }

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
