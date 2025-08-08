FROM openjdk:17-jdk-slim

WORKDIR /app

# Install required tools for Gradle and Kotlin builds
RUN apt-get update && apt-get install -y \
    curl \
    unzip \
    git \
    bash \
    zip \
    && rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y openjdk-21-jdk
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

# Copy Gradle wrapper and settings
COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts .
COPY build.gradle.kts .

# Copy backend source
COPY backend backend

# Make Gradle wrapper executable
RUN chmod +x gradlew

# Build the backend fat jar
RUN ./gradlew :backend:shadowJar

# Expose Ktor default port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "backend/build/libs/melody-backend.jar"]