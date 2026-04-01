# OpenNLP gRPC Python Client

This directory contains a minimal Python client for interacting with the OpenNLP gRPC service. It demonstrates how to connect to the server and perform sentence detection using a simple example.

---

## Prerequisites

Make sure you have:

- Python 3.8+
- Java (JDK 11 or later recommended)
- Maven

Install Python dependencies:

pip install grpcio grpcio-tools

---

## Project Structure

opennlp-grpc/
├── python-client/
│   ├── example.py
│   ├── opennlp_pb2.py
│   ├── opennlp_pb2_grpc.py

---

## Step 1: Start the OpenNLP gRPC Server

Open a terminal and navigate to:

cd opennlp-sandbox/opennlp-grpc

Build the server:

mvn clean install

Run the server:

java -Dopennlp.models.dir=./models \
     -jar opennlp-grpc-service/target/opennlp-grpc-server-3.0.0-SNAPSHOT.jar \
     -c config.properties \
     -p 7071

Expected output:

Started OpenNLPServer on port 7071

---

## Step 2: Run the Python Client

Open a new terminal:

cd opennlp-sandbox/opennlp-grpc/python-client

Run the client:

python example.py

---

## Expected Output

--- Connecting to Apache OpenNLP gRPC Server ---
[Discovery] Using model: unknown (unknown)

--- Detection Results ---
Sentence 1: The Apache OpenNLP project is great.
Sentence 2: It is now connected to Python!

---

## Notes

- If the model shows as "unknown", check:
  - The models/ directory contains valid models (e.g., en-sent.bin)
  - config.properties is correctly configured

Example:

sentenceModel=en-sent.bin

---

## Troubleshooting

Server not starting:
- Ensure Maven build completed successfully
- Check Java version compatibility

Python cannot connect:
- Ensure server is running on port 7071

Import errors:
- Run the script from inside the python-client/ directory

---

## Purpose

This client serves as a minimal reference to:

- Demonstrate Python ↔ gRPC integration
- Provide a simple starting point
- Help validate OpenNLP gRPC services

---

## Future Improvements

- Add POS tagging support
- Improve model discovery
- Package as a Python SDK (pip)