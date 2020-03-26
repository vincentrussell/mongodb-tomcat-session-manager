Tomcat MongoDB Persistent Session Manager
=====================

# Overview

This is an Apache Tomcat Persistent Session Manager implementation backed by MongoDB.

## Quick Start

### Requirements

*  Tomcat 9.x (tested with Tomcat 9.0.31)
*  Java 1.7 or higher (tested with Java 1.7)
*  MongoDB Driver 3.6.3 or higher (tested with MongoDB Driver 3.6.3)

### Usage

* the following code builds the mongo session manager

```java
   MongoSessionManager mongoSessionManager = new MongoSessionManager.Builder()
                .setMongoClient(mongoClient)
                .setContext(mockContext)
                .setDatabaseName("local")
                .build();
```

#### MongoSessionManager Builder Properties.


 Attribute | Description |
 --------- | ----------- |
 **context** | Tomcat context
 **mongoClient** | Mongo client 
 **username** | Instead of providing  MongoClient you can provide a username, password, hosts, and adminDatabase.
 **password** | Instead of providing  MongoClient you can provide a username, password, hosts, and adminDatabase.
 **hosts** | Instead of providing  MongoClient you can provide a username, password, hosts, and adminDatabase.
 **adminDatabase** | Instead of providing  MongoClient you can provide a username, password, hosts, and adminDatabase.
 **databaseName** | MongoDB Database name to use
 collectionName | Name of the Collection to use.  Defaults to **tomcat_user_sessions** .
 saveOnRestart | Whether to save and reload sessions when the Manager **unload** and **load** methods are called.  Defaults to **true**.
 maxIdleBackup | How long a session must be idle before it should be backed up. **-1** means sessions won't be backed up.  Defaults to **5**. 
 minIdleSwap | The minimum time in seconds a session must be idle before it is eligible to be swapped to disk to keep the active session count below maxActiveSessions. Setting to **-1** means sessions will not be swapped out to keep the active session count down.  Defaults to **-1** 
 maxIdleSwap | The maximum time in seconds a session may be idle before it is eligible to be swapped to disk due to inactivity. Setting this to **-1** means sessions should not be swapped out just because of inactivity.  Defaults to **0**. 
 processExpiresFrequency | Frequency of the session expiration, and related manager operations. Manager operations will be done once for the specified amount of backgroundProcess calls (ie, the lower the amount, the most often the checks will occur).  Defaults to **6**.
License: [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
