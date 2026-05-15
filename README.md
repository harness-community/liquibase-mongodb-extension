# Liquibase MongoDB Extension

[![Build Status](https://github.com/liquibase/liquibase-mongodb/actions/workflows/build-nightly.yml/badge.svg)](https://github.com/liquibase/liquibase-mongodb/actions/workflows/build-nightly.yml)

*This is a Harness-enhanced fork of the [Liquibase MongoDB Extension](https://github.com/liquibase/liquibase-mongodb) with additional features and improvements.*

**Harness Maven Repository**: [Harness Maven Public Repository](https://console.cloud.google.com/artifacts/maven/gar-prod-setup/us/harness-maven-public/io.harness:liquibase-mongodb-dbops-extension?project=gar-prod-setup)

**Harness MongoDB Docs**: [Harness Database DevOps - MongoDB Commands](https://developer.harness.io/docs/database-devops/concepts/database-devops/concepts/mongodb-command)

## Table of contents

1. [Introduction](#introduction)
1. [Release Notes](#release-notes)
1. [Implemented Changes](#implemented-changes)
1. [Connection String Formats](#connection-string)
1. [Getting Started](#getting-started)
1. [Running tests](#running-tests)
1. [Integration](#integration)
1. [Harness Enhancements](#harness-enhancements)
1. [Contributing](#contributing)
1. [License](#license)

<a name="introduction"></a>
## Introduction

This is a **Harness-enhanced** Liquibase extension for MongoDB support, forked from the [Liquibase MongoDB Extension](https://github.com/liquibase/liquibase-mongodb).

The original extension resulted as an alternative to existing MongoDB evolution tools that were basically wrappers over the deprecated [`db.eval`](https://docs.mongodb.com/manual/reference/method/db.eval/#db.eval) shell method (deprecated starting from MongoDB 4.2).

**Harness has enhanced this extension** with additional features including:
- **Mongo Native Executor** - Execute native MongoDB operations with improved performance
- Enhanced database change management capabilities
- Additional operational improvements and optimizations

The extension allows calling specific `mongo-java-driver` methods through Liquibase's changeset framework, providing enterprise-grade database change management for MongoDB deployments.

<a name="release-notes"></a>
## [Release Notes](./changelog.txt)

### Harness Enhanced Releases

#### 1.0.0-4.33.0
* Upgrade the MongoDB extension to 4.33.0
* Based on Liquibase 4.33.0
* **JAR Download**: [Maven Repository](https://console.cloud.google.com/artifacts/maven/gar-prod-setup/us/harness-maven-public/io.harness:liquibase-mongodb-dbops-extension/1.0.0-4.33.0?project=gar-prod-setup)

#### 1.1.0-4.24.0
* **Selective Property Serialization** - Enhanced `mongo` and `mongoFile` changeTypes with selective property serialization
* Prevents unnecessary properties from being serialized in changeset YAML files
* Improves changeset file cleanliness and reduces file size
* Based on Liquibase 4.24.0
* **JAR Download**: [Maven Repository](https://console.cloud.google.com/artifacts/maven/gar-prod-setup/us/harness-maven-public/io.harness:liquibase-mongodb-dbops-extension/1.1.0-4.24.0?project=gar-prod-setup)

#### 1.0.0-4.24.0
* **Mongo Native Executor Support** - Initial release with mongo native executor capabilities
* Added new `mongo` changeType for inline JavaScript/MongoDB shell commands via mongosh
* Added new `mongoFile` changeType for executing MongoDB shell commands from external files
* Enhanced MongoDB operation execution with improved performance
* Based on Liquibase 4.24.0
* **JAR Download**: [Maven Repository](https://console.cloud.google.com/artifacts/maven/gar-prod-setup/us/harness-maven-public/io.harness:liquibase-mongodb-dbops-extension/1.0.0-4.24.0?project=gar-prod-setup)

<a name="implemented-changes"></a>
## Implemented Changes:

A couple of Changes were implemented until identified that majority of the operations can be achieved using `db.runCommand()` and `db.adminCommand()`

* [createCollection](https://docs.mongodb.com/manual/reference/method/db.createCollection/#db.createCollection) - 
Creates a collection with validator [create](https://docs.mongodb.com/manual/reference/command/create/)
* [dropCollection](https://docs.mongodb.com/manual/reference/method/db.collection.drop/#db-collection-drop) - 
Removes a collection or view from the database [drop](https://docs.mongodb.com/manual/reference/command/drop/)
* [createIndex](https://docs.mongodb.com/manual/reference/method/db.collection.createIndex/#db.collection.createIndex) - 
Creates an index for a collection [createIndexes](https://docs.mongodb.com/manual/reference/command/createIndexes/)
* [dropIndex](https://docs.mongodb.com/manual/reference/method/db.collection.dropIndex/#db.collection.dropIndex) - 
Drops index for a collection by keys [dropIndexes](https://docs.mongodb.com/manual/reference/command/dropIndexes/)
* [insertMany](https://docs.mongodb.com/manual/reference/method/db.collection.insertMany/#db.collection.insertMany) - 
Inserts multiple documents into a collection [insert](https://docs.mongodb.com/manual/reference/command/insert/)
* [insertOne](https://docs.mongodb.com/manual/tutorial/insert-documents/#insert-a-single-document) - 
Inserts a Single Document into a collection [insert](https://docs.mongodb.com/manual/reference/command/insert/)
* [__runCommand__](https://docs.mongodb.com/manual/reference/method/db.runCommand/#db-runcommand) - 
Provides a helper to run specified database commands. This is the preferred method to issue database commands, as it provides a consistent interface between the shell and drivers
* [__adminCommand__](https://docs.mongodb.com/manual/reference/method/db.adminCommand/#db.adminCommand) - 
Provides a helper to run specified database commands against the admin database
* [__mongo__](https://developer.harness.io/docs/database-devops/concepts/database-devops/concepts/mongodb-command) -
Executes inline JavaScript/MongoDB shell commands via mongosh (Harness Enhancement)
* [__mongoFile__](https://developer.harness.io/docs/database-devops/concepts/database-devops/concepts/mongodb-command) -
Executes JavaScript/MongoDB shell commands from external files via mongosh (Harness Enhancement)

<a name="connection-string"></a>
## Connection String Formats

### [Standard Connection String Format](https://docs.mongodb.com/manual/reference/connection-string/index.html#standard-connection-string-format)

`
mongodb://[username:password@]host1[:port1][,...hostN[:portN]][/[defaultauthdb][?options]]
mongodb://mongodb1.example.com:27317,mongodb2.example.com:27017/?replicaSet=mySet&authSource=authDB
`

### [DNS Seed List Connection Format](https://docs.mongodb.com/manual/reference/connection-string/index.html#dns-seed-list-connection-format)

`
mongodb+srv://[username:password@]host[/[database][?options]]
mongodb+srv://server.example.com/
mongodb+srv://:@cluster0.example.com/testdb?authSource=$external&authMechanism=MONGODB-AWS
`

<a name="getting-started"></a>
## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. 

### Prerequisites
 
* Dependencies that have to be available in classpath if run via Liquibase CLI

```
mongodb-driver-sync:4.2.0
snakeyaml:1.27
jackson-annotations:2.11.3
jackson-core:2.11.3
jackson-databind:2.11.3
```

### Installing

You can either download a released JAR from the Harness Maven repository or build the project from source.

#### 1. Download Prebuilt JAR (Recommended)

Browse available versions in the Harness Maven repository from [Registry](https://console.cloud.google.com/artifacts/maven/gar-prod-setup/us/harness-maven-public/io.harness:liquibase-mongodb-dbops-extension?project=gar-prod-setup). You can download a release JAR using `curl`, for example:

```bash
curl -L \
  "https://us-maven.pkg.dev/gar-prod-setup/harness-maven-public/io/harness/liquibase-mongodb-dbops-extension/1.0.0-4.33.0/liquibase-mongodb-dbops-extension-1.0.0-4.33.0.jar" \
  -o liquibase-mongodb-dbops-extension-1.0.0-4.33.0.jar
```

#### 2. Build from Source

a. Clone the repository
```shell
git clone https://github.com/liquibase/liquibase-mongodb
cd liquibase-mongodb
```
b. Build the project: `mvn clean install`
c. The generated JAR will be available under: `target/liquibase-mongodb-<version>.jar`

#### 3. Move the JAR into the Liquibase lib Directory

Once the JAR file is available, place it in your Liquibase lib directory. Based on the Liquibase installation method, the lib directory will generally be:

```sh
# if Liquibase is installed from the Liquibase website
<liquibase-home>/internals/lib

# if Liquibase is installed via brew
/opt/homebrew/opt/liquibase/libexec/lib
```

This makes the extension available to the **Liquibase CLI**.

---

* [Run tests](#running-tests)

<a name="running-tests"></a>
## Running tests

### Adjust connection string
 
Connection url can be adjusted here: [`url`](./src/test/resources/liquibase.properties)
[Connection String Format](https://docs.mongodb.com/manual/reference/connection-string/)
Run Integration tests by enabling `run-its` profile 

### Run integration tests

```shell
mvn clean install -Prun-its
```

#### Run integration test driver backward compatibility
1. Produce test containing JAR:
```shell
mvn clean install -Ptest-jar
```
2. Go to `test-project`:
```shell
cd test-project
```
3. Run backward compatibility test with the provided 3.x driver:
```shell
mvn clean install -Prun-its,mongo-3x
```

### Quick Start Examples

[Quick start Application for NoSql liquibase extensions](https://github.com/alexandru-slobodcicov/liquibase-nosql-quickstart)

<a name="harness-enhancements"></a>
## Harness Enhancements

This Harness fork includes several enhancements over the Liquibase MongoDB extension:

### **Mongo Native Executor**
- Enhanced native MongoDB operation execution
- Better integration with MongoDB-specific features
- Support for MongoDB-specific commands and operations

### **Additional Features**
- Enhanced error handling and logging
- Improved compatibility with various MongoDB deployment scenarios
- Additional operational improvements for enterprise use cases

*For detailed information about Harness-specific features, refer to the [Harness Database DevOps MongoDB Documentation](https://developer.harness.io/docs/database-devops/concepts/database-devops/concepts/mongodb-command).*

<a name="integration"></a>
## Integration

### Add dependency: 

**For Harness Enhanced Version:**
```xml
<dependency>
    <groupId>io.harness</groupId>
    <artifactId>liquibase-mongodb-dbops-extension</artifactId>
    <version>${harness-liquibase-mongodb.version}</version>
</dependency>
```

**Original Liquibase Version:**

```xml
<dependency>
    <groupId>org.liquibase.ext</groupId>
    <artifactId>liquibase-mongodb</artifactId>
    <version>${liquibase-mongodb.version}</version>
</dependency>
```
### Java call:
```java
public class Application {
    public static void main(String[] args) {
        MongoLiquibaseDatabase database = (MongoLiquibaseDatabase) DatabaseFactory.getInstance().openDatabase(url, null, null, null, null);
        Liquibase liquibase = new Liquibase("liquibase/ext/changelog.generic.test.xml", new ClassLoaderResourceAccessor(), database);
        liquibase.update("");
    }
}
```

<a name="contributing"></a>
## Contributing

Please read [CONTRIBUTING.md](./CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

<a name="license"></a>
## License

This project is licensed under the Apache License Version 2.0 - see the [LICENSE.md](LICENSE.md) file for details



