import sys
import os
import grpc

# Fix import path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

import opennlp_pb2
import opennlp_pb2_grpc


def get_active_model(stub):
    available = stub.GetAvailableModels(opennlp_pb2.Empty())
    models = list(available.models)

    if models:
        model = models[0]
        print(f"[Discovery] Using model: {model.name} ({model.hash})")
        return model.hash
    else:
        print("[Discovery] No models reported. Using 'unknown' fallback.")
        return "unknown"


def run():
    print("--- Connecting to Apache OpenNLP gRPC Server ---")

    # Create channel
    channel = grpc.insecure_channel("localhost:7071")
    stub = opennlp_pb2_grpc.SentenceDetectorServiceStub(channel)

    # Get model
    model_hash = get_active_model(stub)

    # Request
    text = "The Apache OpenNLP project is great. It is now connected to Python!"
    request = opennlp_pb2.SentDetectRequest(
        sentence=text,
        model_hash=model_hash
    )

    # Call API
    response = stub.sentDetect(request)

    # Output
    print("\n--- Detection Results ---")
    for i, sentence in enumerate(response.values, 1):
        print(f"Sentence {i}: {sentence}")


if __name__ == "__main__":
    run()