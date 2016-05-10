scapdash
========

Simple dashboard for SCAP data.

Usage
-----

Build and run:

    mvn package dependency:copy-dependencies
    java -cp 'target/*:target/dependency/*' scapdash.Scapdash

Supply data:

    oscap xccdf eval --results results.xml example.xccdf.xml
    curl -v -XPOST --data-binary @results.xml http://localhost:4567/checkin

Configuration
-------------

Environment variables:

    DB_URL=jdbc:h2:file:/tmp/db
    DB_USERNAME=
    DB_PASSWORD=

    DEBUG_SQL=1
