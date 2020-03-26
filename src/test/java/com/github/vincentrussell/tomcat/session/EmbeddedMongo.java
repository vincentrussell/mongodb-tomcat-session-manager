package com.github.vincentrussell.tomcat.session;

import com.google.common.collect.Lists;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.bson.Document;

import java.io.Closeable;
import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class EmbeddedMongo implements Closeable {

    public static final String DEFAULT_DATABASE_NAME = "local";
    private final MongodExecutable mongodExe;
    private final MongodProcess process;
    private final int port;
    private final MongoClient mongoClient;

    public EmbeddedMongo(final int port, final String version) throws IOException {
        this(port, version, null, null);
    }

    public EmbeddedMongo(final int port, final String version, final String username,
                         final String password) throws IOException {
        this.port = port;
        MongodStarter runtime = MongodStarter.getDefaultInstance();
        mongodExe = runtime.prepare(
                new MongodConfigBuilder().version(Version.valueOf(normalizeVersion(version)))
                        .net(new Net(port, Network.localhostIsIPv6()))
                        .build());
        process = mongodExe.start();

        if (!isEmpty(username) && !isEmpty(password)) {
            MongoClient mongoClient = new MongoClient("localhost:" + port, new MongoClientOptions.Builder()
                    .build());
            mongoClient.getDatabase("admin").runCommand(
                    new Document(new BasicDBObject("createUser", username)
                    .append("pwd", password)
                    .append("roles",
                                    Lists.newArrayList("userAdminAnyDatabase")).toMap()));
            this.mongoClient = mongoClient;
        } else {
            mongoClient = new MongoClient("localhost:" + port, new MongoClientOptions.Builder()
                    .build());
        }

    }

    public int getPort() {
        return port;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    private String normalizeVersion(final String version) {
        final String normalizedVersion = version.replaceAll("\\.", "_");

        if (!"v".equalsIgnoreCase(version.substring(0, 1))) {
            return "V" + normalizedVersion;
        } else {
            return normalizedVersion;
        }
    }

    @Override
    public void close() throws IOException {
        mongoClient.close();
        process.stop();
        mongodExe.stop();
    }
}
