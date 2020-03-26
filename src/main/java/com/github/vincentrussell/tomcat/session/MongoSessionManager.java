package com.github.vincentrussell.tomcat.session;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.session.PersistentManagerBase;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.tomcat.util.ExceptionUtils;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.Validate.notNull;

public class MongoSessionManager extends PersistentManagerBase {

    public static final String USER_SESSIONS = "tomcat_user_sessions";
    private static final int DEFAULT_MONGO_PORT = 27017;
    private MongoClient mongoClient;
    private Builder builder;
    private String collectionName;
    private String databaseName;


    private MongoSessionManager(final Builder builder) {
        notNull(builder.context, "context is required");
        notNull(builder.databaseName, "databaseName is required");
        notNull(builder.collectionName, "collectionName is required");
        setContext(builder.context);
        this.mongoClient = getMongoClient(builder);
        this.databaseName = builder.databaseName;
        this.collectionName = builder.collectionName;
        this.builder = builder;
        notNull(this.mongoClient, "mongoClient is required");
        this.store = new MongoDbSessionStore(this, this.mongoClient,
                this.databaseName, this.collectionName);
        this.sessionIdGenerator = new StandardSessionIdGenerator();
        this.saveOnRestart = builder.saveOnRestart;
        this.maxIdleBackup = builder.maxIdleBackup;
        this.minIdleSwap = builder.minIdleSwap;
        this.maxIdleSwap = builder.maxIdleSwap;
        this.processExpiresFrequency = builder.processExpiresFrequency;
    }

    private MongoClient getMongoClient(Builder builder) {
        if (builder.mongoClient != null) {
            return builder.mongoClient;
        } else if (createdFromBuilder(builder)) {
            return new MongoClient(Lists.transform(builder.hosts, new Function<String, ServerAddress>() {
                @Override
                public ServerAddress apply(final String hostString) {
                    final String[] hosts = hostString.split(":");
                    if (hosts.length == 1) {
                        return new ServerAddress(hostString, DEFAULT_MONGO_PORT);
                    } else {
                        return new ServerAddress(hosts[0], Integer.valueOf(hosts[1]));
                    }

                }
            }), MongoCredential.createCredential(builder.username,
                    isEmpty(builder.adminDatabase) ? "admin" : builder.adminDatabase, builder.password.toCharArray()),
                    new MongoClientOptions.Builder().build());
        } else {
            throw new IllegalArgumentException("could not build mongoClient.  " +
                    "You must provide a username, password, and hosts or a mongoClient");
        }
    }

    private boolean createdFromBuilder(Builder builder) {
        return !isEmpty(builder.username) && !isEmpty(builder.password) && !builder.hosts.isEmpty();
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

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        try {
            if (createdFromBuilder(builder)) {
                mongoClient.close();
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            getContext().getLogger().error(sm.getString("standardManager.managerUnload"), t);
        }
    }

    public static class Builder {
        private String username;
        private String password;
        private String adminDatabase;
        private List<String> hosts = new ArrayList<>();
        private MongoClient mongoClient;
        private String databaseName;
        private String collectionName = USER_SESSIONS;
        private Context context;
        private boolean saveOnRestart = true;
        private int maxIdleBackup = 5;
        private int minIdleSwap = -1;
        private int maxIdleSwap = 0;
        private int processExpiresFrequency = 6;

        public Builder() {}

        public Builder setMongoClient(final MongoClient mongoClient) {
            this.mongoClient = mongoClient;
            return this;
        }

        public Builder setUsername(final String username) {
            this.username = username;
            return this;
        }

        public Builder setPassword(final String password) {
            this.password = password;
            return this;
        }

        public Builder setAdminDatabase(final String adminDatabase) {
            this.adminDatabase = adminDatabase;
            return this;
        }

        public Builder setHosts(final String... hosts) {
            this.hosts.clear();
            this.hosts.addAll(Lists.newArrayList(hosts));
            return this;
        }

        public Builder setHosts(final List<String> hosts) {
            this.hosts.clear();
            this.hosts.addAll(hosts);
            return this;
        }

        public Builder addHost(final String host) {
            this.hosts.add(host);
            return this;
        }

        public Builder setContext(final Context context) {
            this.context = context;
            return this;
        }

        public Builder setDatabaseName(final String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder setCollectionName(final String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder setSaveOnRestart(final boolean saveOnRestart) {
            this.saveOnRestart = saveOnRestart;
            return this;
        }

        public Builder setMaxIdleBackup(final int maxIdleBackup) {
            this.maxIdleBackup = maxIdleBackup;
            return this;
        }

        public Builder setMinIdleSwap(final int minIdleSwap) {
            this.minIdleSwap = minIdleSwap;
            return this;
        }

        public Builder setMaxIdleSwap(final int maxIdleSwap) {
            this.maxIdleSwap = maxIdleSwap;
            return this;
        }

        public Builder setProcessExpiresFrequency(final int processExpiresFrequency) {
            this.processExpiresFrequency = processExpiresFrequency;
            return this;
        }

        public MongoSessionManager build() {
            return new MongoSessionManager(this);
        }
    }
}
