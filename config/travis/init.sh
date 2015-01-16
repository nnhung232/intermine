#!/bin/bash

set -e

if [ -z $(which wget) ]; then
    # use curl
    GET='curl'
else
    GET='wget -O -'
fi

GIT_GET="git clone --single-branch --depth 1"

BUILD_LOG=${HOME}/build.log

export PSQL_USER=postgres
export KEYSTORE=${PWD}/keystore.jks

echo "#---> Running $TEST_SUITE tests"

if [ "$TEST_SUITE" = "checkstyle" ]; then
    exit 0 # nothing to do
else
    # Create all databases we might need.
    psql -c 'create database notxmltest;' -U postgres
    psql -c 'create database truncunittest;' -U postgres
    psql -c 'create database flatmodetest;' -U postgres
    psql -c 'create database fulldatatest;' -U postgres
    psql -c 'create database userprofiletest;' -U postgres
    psql -c 'create database unittest;' -U postgres
    psql -c 'create database biotest;' -U postgres
    psql -c 'create database biofulldatatest;' -U postgres

    # Set up properties
    source config/create-ci-properties-files.sh

    echo '#---> Installing python requirements'
    # Install lib requirements
    pip install -r config/lib/requirements.txt

    # Build resources we might require
    if [ "$TEST_SUITE" = "webapp" ]; then
        # We will need python requirements for selenium tests
        pip install -r testmodel/webapp/selenium/requirements.txt
    fi

    if [ "$TEST_SUITE" = "webapp" -o "$TEST_SUITE" = "ws" ]; then
        # We will need a fully operational web-application
        echo '#---> Building and releasing web application to test against'
        source config/init-webapp.sh
        source config/issue-token.sh
    elif [ "$TEST_SUITE" = "bio" ]; then
        # Bio requires the bio model
        ant -f bio/test-all/dbmodel/build.xml build-db >> $BUILD_LOG
        echo '#---> building bio tools'
        ant -f bio/tools/main/build.xml clean default >> $BUILD_LOG
        echo '#---> Building bio sources'
        for biosrc in $(find bio/sources/ -path '*/main/build.xml' ! -path '*example*' ! -path '*retired*'); do
            echo "#------> building $biosrc"
            ant -f "$biosrc" clean default
        done
    elif [ "$TEST_SUITE" = "api" -o "$TEST_SUITE" = "web" -o "$TEST_SUITE" = "webtasks" -o "$TEST_SUITE" = "all" ]; then
        # api, webtasks, web and all need the testmodel to be built
        ant -f testmodel/dbmodel/build.xml build-db
    fi

    if [[ "$TEST_SUITE" = "bio-webapp" ]]; then
        echo '#---> Building and releasing the biotestmine'
        pip install -r 'biotestmine/test/api/requirements.txt'
        source config/download_and_configure_tomcat.sh
        ./biotestmine/setup.sh
    fi

    if [[ "$TEST_SUITE" = "ws" ]]; then

        # Warm up the keyword search by requesting results, but ignoring the results
        $GET "$TESTMODEL_URL/service/search" > /dev/null
        # Start any list upgrades
        $GET "$TESTMODEL_URL/service/lists?token=test-user-token" > /dev/null

        if [[ "$CLIENT" = "JS" ]]; then
            # We need the imjs code to exercise the webservices
            $GIT_GET https://github.com/intermine/imjs.git client
        elif [[ "$CLIENT" = "PY" ]]; then
            $GIT_GET https://github.com/intermine/intermine-ws-client.py client
        fi
    fi
fi
