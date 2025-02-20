#!/usr/bin/env bash
set -e
# update poms with the version extracted from sbt dynver
mvn --quiet --batch-mode --activate-profiles patch-version versions:set -DnewVersion=${SDK_VERSION}

( # also needs to change kalix-sdk.version in parent pom
    cd kalix-java-protobuf-parent
    sed -i.bak "s/<kalix-sdk.version>\(.*\)<\/kalix-sdk.version>/<kalix-sdk.version>$SDK_VERSION<\/kalix-sdk.version>/" pom.xml
    rm pom.xml.bak
)
