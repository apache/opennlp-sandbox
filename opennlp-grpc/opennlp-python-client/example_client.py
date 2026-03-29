import grpc
import opennlp_pb2
import opennlp_pb2_grpc

def run_test():
    # 1. Connect to the Java gRPC server
    channel = grpc.insecure_channel('localhost:7071')
    
    # 2. Create the stub for Sentence Detection
    stub = opennlp_pb2_grpc.SentenceDetectorServiceStub(channel)

    print("--- Connecting to Apache OpenNLP gRPC Server ---")

    # 3. Discover available models first
    active_model_hash = "extlib/opennlp-models-sentdetect-en-1.1.0.jar" # Default fallback
    
    try:
        available = stub.GetAvailableModels(opennlp_pb2.Empty())
        print("\n[Discovery] Server reports the following models:")
        
        if not available.models:
            print(" - No models found. Using default fallback path.")
        else:
            for m in available.models:
                print(f" - Name: {m.name} | Hash/ID: {m.hash}")
                # Automatically pick the first available model hash
                active_model_hash = m.hash 
    except Exception as e:
        print(f"[Discovery Error] Could not fetch models: {e}")

    # 4. Perform the Sentence Detection
    try:
        test_text = "The Apache OpenNLP project is great. It is now connected to Python!"
        
        # Construct request with the discovered hash
        request = opennlp_pb2.SentDetectRequest(
            sentence=test_text, 
            model_hash=active_model_hash
        )

        print(f"\n[Request] Sending text using model: {active_model_hash}")
        response = stub.sentDetect(request)

        print("\n--- Success! ---")
        for i, sentence in enumerate(response.values, 1):
            print(f"Sentence {i}: {sentence}")

    except grpc.RpcError as e:
        print(f"\n[Error] gRPC call failed!")
        print(f"Status: {e.code()}")
        print(f"Details: {e.details()}")
    except Exception as e:
        print(f"\n[Unexpected Error] {e}")

if __name__ == "__main__":
    run_test()