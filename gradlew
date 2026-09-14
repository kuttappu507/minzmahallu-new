#!/bin/sh
# Gradle start-up script for POSIX
DIR=$(cd "$(dirname "$0")" && pwd)
APP_HOME=$DIR
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
# Download wrapper jar if missing
if [ ! -f "$CLASSPATH" ]; then
  mkdir -p "$APP_HOME/gradle/wrapper"
  echo "Downloading gradle-wrapper.jar..."
  curl -fsSL -o "$CLASSPATH" \
    "https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar" \
    || curl -fsSL -o "$CLASSPATH" \
    "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
fi
# Find java
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi
exec "$JAVACMD" -Xmx2048m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
