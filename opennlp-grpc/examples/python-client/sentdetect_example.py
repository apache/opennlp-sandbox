# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import grpc
import opennlp_pb2
import opennlp_pb2_grpc


def run():
    # Create gRPC channel
    channel = grpc.insecure_channel("localhost:7071")
    stub = opennlp_pb2_grpc.SentenceDetectorServiceStub(channel)

    print("Connecting to OpenNLP gRPC server...")

    # Discover available models
    response = stub.GetAvailableModels(opennlp_pb2.Empty())
    models = list(response.models)

    if not models:
        print("No models available on server.")
        return

    model = models[0]
    print(f"Using model: {model.name} ({model.hash})")

    # Input text
    text = "The Apache OpenNLP project is great. It is now connected to Python!"

    # Create request
    request = opennlp_pb2.SentDetectRequest(
        sentence=text,
        model_hash=model.hash
    )

    # Call service
    result = stub.sentDetect(request)

    print("\nSentence Detection Result:")
    for i, sentence in enumerate(result.values, 1):
        print(f"{i}. {sentence}")


if __name__ == "__main__":
    run()