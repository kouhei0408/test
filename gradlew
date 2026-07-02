#!/bin/sh

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_SHARED_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper-shared.jar"
WRAPPER_CLI_JAR="$APP_HOME/gradle/wrapper/gradle-cli.jar"

if [ ! -f "$WRAPPER_JAR" ] || [ ! -f "$WRAPPER_SHARED_JAR" ] || [ ! -f "$WRAPPER_CLI_JAR" ]; then
  echo "Gradle wrapper jars are missing. Please generate or download the Gradle wrapper binary first."
  exit 1
fi

exec java -classpath "$WRAPPER_JAR:$WRAPPER_SHARED_JAR:$WRAPPER_CLI_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
