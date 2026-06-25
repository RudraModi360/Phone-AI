#!/bin/bash

##############################################################################
## Gradle wrapper script for UNIX based systems.
##############################################################################

set -e

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Attempt to find JAVA_HOME, fallback to java in PATH
if [ -z "$JAVA_HOME" ] ; then
    JAVACMD=`which java`
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

# Verify JAVA is available
if [ ! -x "$JAVACMD" ] ; then
    echo "Error: JAVA_HOME not found."
    exit 1
fi

exec "$JAVACMD" $JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
