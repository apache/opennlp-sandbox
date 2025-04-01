# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import grpc

import opennlp_pb2
import opennlp_pb2_grpc

# Define the server address and port
server_address = 'localhost:7071'

# Create a channel and a stub (client)
with grpc.insecure_channel(server_address) as channel:
    stub = opennlp_pb2_grpc.PosTaggerServiceStub(channel)

    try:
        empty_request = opennlp_pb2.Empty()
        available_models_response = stub.GetAvailableModels(empty_request)
        print(f"Available POS Models: {available_models_response.models}")
    except grpc.RpcError as e:
        print(f"GetAvailableModels call failed: {e}")

    # Call the 'Tag' method
    try:
        # Construct the TagRequest object
        tag_request = opennlp_pb2.TagRequest(
            sentence=['The', 'driver', 'got', 'badly', 'injured', 'by', 'the', 'accident', '.'],
            format=opennlp_pb2.POSTagFormat.UD,  # Use the enum for UD format
            model_hash='cb219de4e5fc8c3c6d61531ac2dff0b186f6c3f457207359ec28e9336311ef8e'
        )

        # Make the RPC call
        tag_response = stub.Tag(tag_request)

        # Output the response
        print(f"Tag Response: {tag_response.values}")
    except grpc.RpcError as e:
        print(f"Tag call failed: {e}")
