#!/bin/bash
set -e

JAR="api/build/libs/api-1.0-SNAPSHOT.jar"
JAVA_HOME_PATH="/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
JAVA="$JAVA_HOME_PATH/bin/java"

cd "$(dirname "$0")"

# Build if JAR is missing
if [ ! -f "$JAR" ]; then
  echo "JAR not found — building..."
  JAVA_HOME="$JAVA_HOME_PATH" ./gradlew clean build shadowJar -x test
fi

# Kill any existing process on port 9090
lsof -ti:9090 | xargs kill -9 2>/dev/null || true

# Start the Spring Boot app (Spark runs inside it)
echo "Starting application..."
"$JAVA" -Xmx4g -jar "$JAR" > /tmp/genre-classifier.log 2>&1 &
APP_PID=$!

echo "PID: $APP_PID (logs: /tmp/genre-classifier.log)"

# Wait for the server to be ready
echo "Waiting for server on port 9090..."
until curl -s http://localhost:9090 > /dev/null 2>&1; do
  if ! kill -0 $APP_PID 2>/dev/null; then
    echo "Application crashed. Check /tmp/genre-classifier.log"
    exit 1
  fi
  sleep 2
done

echo "Server ready — opening browser..."
open http://localhost:9090
