# Use Java 17 base image
FROM eclipse-temurin:17-jre

# Install Tesseract OCR and OpenCV
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    libopencv-dev \
    libopencv-java \
    python3-opencv \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the built JAR file
COPY target/doczbot-0.0.1-SNAPSHOT.jar app.jar

# Set environment variables
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata/
ENV LD_LIBRARY_PATH=/usr/lib/jni:/usr/lib/x86_64-linux-gnu:/usr/lib/aarch64-linux-gnu
ENV JAVA_LIBRARY_PATH=/usr/lib/jni:/usr/lib/x86_64-linux-gnu:/usr/lib/aarch64-linux-gnu

# Expose port
EXPOSE 8080

# Run the application with Java library path
ENTRYPOINT ["java", "-Djava.library.path=/usr/lib/jni:/usr/lib/x86_64-linux-gnu:/usr/lib/aarch64-linux-gnu", "-jar", "app.jar"]