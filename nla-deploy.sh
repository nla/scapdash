#!/bin/bash
mkdir -p "$1/lib"
mvn package dependency:copy-dependencies -DoutputDirectory="$1/lib"
cp -v target/*.jar "$1/lib"
