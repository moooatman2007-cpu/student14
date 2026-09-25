#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
APP_BASE_NAME=${0##*/}

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE=java
fi

if ! command -v "$JAVA_EXE" >/dev/null 2>&1 && [ ! -x "$JAVA_EXE" ]; then
  echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
  exit 1
fi

exec "$JAVA_EXE" ${DEFAULT_JVM_OPTS:-} ${JAVA_OPTS:-} ${GRADLE_OPTS:-} "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
