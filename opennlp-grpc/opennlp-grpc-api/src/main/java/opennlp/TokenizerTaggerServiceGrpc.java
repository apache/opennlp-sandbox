/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package opennlp;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler",
    comments = "Source: opennlp.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class TokenizerTaggerServiceGrpc {

  private TokenizerTaggerServiceGrpc() {}

  public static final String SERVICE_NAME = "opennlp.TokenizerTaggerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<opennlp.OpenNLPService.TokenizeRequest,
      opennlp.OpenNLPService.StringList> getTokenizeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Tokenize",
      requestType = opennlp.OpenNLPService.TokenizeRequest.class,
      responseType = opennlp.OpenNLPService.StringList.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<opennlp.OpenNLPService.TokenizeRequest,
      opennlp.OpenNLPService.StringList> getTokenizeMethod() {
    io.grpc.MethodDescriptor<opennlp.OpenNLPService.TokenizeRequest, opennlp.OpenNLPService.StringList> getTokenizeMethod;
    if ((getTokenizeMethod = TokenizerTaggerServiceGrpc.getTokenizeMethod) == null) {
      synchronized (TokenizerTaggerServiceGrpc.class) {
        if ((getTokenizeMethod = TokenizerTaggerServiceGrpc.getTokenizeMethod) == null) {
          TokenizerTaggerServiceGrpc.getTokenizeMethod = getTokenizeMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.TokenizeRequest, opennlp.OpenNLPService.StringList>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Tokenize"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.TokenizeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.StringList.getDefaultInstance()))
              .setSchemaDescriptor(new TokenizerTaggerServiceMethodDescriptorSupplier("Tokenize"))
              .build();
        }
      }
    }
    return getTokenizeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<opennlp.OpenNLPService.TokenizePosRequest,
      opennlp.OpenNLPService.SpanList> getTokenizePosMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "TokenizePos",
      requestType = opennlp.OpenNLPService.TokenizePosRequest.class,
      responseType = opennlp.OpenNLPService.SpanList.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<opennlp.OpenNLPService.TokenizePosRequest,
      opennlp.OpenNLPService.SpanList> getTokenizePosMethod() {
    io.grpc.MethodDescriptor<opennlp.OpenNLPService.TokenizePosRequest, opennlp.OpenNLPService.SpanList> getTokenizePosMethod;
    if ((getTokenizePosMethod = TokenizerTaggerServiceGrpc.getTokenizePosMethod) == null) {
      synchronized (TokenizerTaggerServiceGrpc.class) {
        if ((getTokenizePosMethod = TokenizerTaggerServiceGrpc.getTokenizePosMethod) == null) {
          TokenizerTaggerServiceGrpc.getTokenizePosMethod = getTokenizePosMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.TokenizePosRequest, opennlp.OpenNLPService.SpanList>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "TokenizePos"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.TokenizePosRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.SpanList.getDefaultInstance()))
              .setSchemaDescriptor(new TokenizerTaggerServiceMethodDescriptorSupplier("TokenizePos"))
              .build();
        }
      }
    }
    return getTokenizePosMethod;
  }

  private static volatile io.grpc.MethodDescriptor<opennlp.OpenNLPService.Empty,
      opennlp.OpenNLPService.AvailableModels> getGetAvailableModelsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetAvailableModels",
      requestType = opennlp.OpenNLPService.Empty.class,
      responseType = opennlp.OpenNLPService.AvailableModels.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<opennlp.OpenNLPService.Empty,
      opennlp.OpenNLPService.AvailableModels> getGetAvailableModelsMethod() {
    io.grpc.MethodDescriptor<opennlp.OpenNLPService.Empty, opennlp.OpenNLPService.AvailableModels> getGetAvailableModelsMethod;
    if ((getGetAvailableModelsMethod = TokenizerTaggerServiceGrpc.getGetAvailableModelsMethod) == null) {
      synchronized (TokenizerTaggerServiceGrpc.class) {
        if ((getGetAvailableModelsMethod = TokenizerTaggerServiceGrpc.getGetAvailableModelsMethod) == null) {
          TokenizerTaggerServiceGrpc.getGetAvailableModelsMethod = getGetAvailableModelsMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.Empty, opennlp.OpenNLPService.AvailableModels>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetAvailableModels"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.AvailableModels.getDefaultInstance()))
              .setSchemaDescriptor(new TokenizerTaggerServiceMethodDescriptorSupplier("GetAvailableModels"))
              .build();
        }
      }
    }
    return getGetAvailableModelsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static TokenizerTaggerServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TokenizerTaggerServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TokenizerTaggerServiceStub>() {
        @java.lang.Override
        public TokenizerTaggerServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TokenizerTaggerServiceStub(channel, callOptions);
        }
      };
    return TokenizerTaggerServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static TokenizerTaggerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TokenizerTaggerServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TokenizerTaggerServiceBlockingStub>() {
        @java.lang.Override
        public TokenizerTaggerServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TokenizerTaggerServiceBlockingStub(channel, callOptions);
        }
      };
    return TokenizerTaggerServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static TokenizerTaggerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TokenizerTaggerServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TokenizerTaggerServiceFutureStub>() {
        @java.lang.Override
        public TokenizerTaggerServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TokenizerTaggerServiceFutureStub(channel, callOptions);
        }
      };
    return TokenizerTaggerServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class TokenizerTaggerServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Splits a sentence into its atomic parts.
     * </pre>
     */
    public void tokenize(opennlp.OpenNLPService.TokenizeRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTokenizeMethod(), responseObserver);
    }

    /**
     * <pre>
     * Finds the boundaries of atomic parts in a string.
     * </pre>
     */
    public void tokenizePos(opennlp.OpenNLPService.TokenizePosRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.SpanList> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTokenizePosMethod(), responseObserver);
    }

    /**
     * <pre>
     * Returns the available models which can be used for tokenization tagging.
     * </pre>
     */
    public void getAvailableModels(opennlp.OpenNLPService.Empty request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetAvailableModelsMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getTokenizeMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                opennlp.OpenNLPService.TokenizeRequest,
                opennlp.OpenNLPService.StringList>(
                  this, METHODID_TOKENIZE)))
          .addMethod(
            getTokenizePosMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                opennlp.OpenNLPService.TokenizePosRequest,
                opennlp.OpenNLPService.SpanList>(
                  this, METHODID_TOKENIZE_POS)))
          .addMethod(
            getGetAvailableModelsMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                opennlp.OpenNLPService.Empty,
                opennlp.OpenNLPService.AvailableModels>(
                  this, METHODID_GET_AVAILABLE_MODELS)))
          .build();
    }
  }

  /**
   */
  public static final class TokenizerTaggerServiceStub extends io.grpc.stub.AbstractAsyncStub<TokenizerTaggerServiceStub> {
    private TokenizerTaggerServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TokenizerTaggerServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TokenizerTaggerServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Splits a sentence into its atomic parts.
     * </pre>
     */
    public void tokenize(opennlp.OpenNLPService.TokenizeRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTokenizeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Finds the boundaries of atomic parts in a string.
     * </pre>
     */
    public void tokenizePos(opennlp.OpenNLPService.TokenizePosRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.SpanList> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTokenizePosMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Returns the available models which can be used for tokenization tagging.
     * </pre>
     */
    public void getAvailableModels(opennlp.OpenNLPService.Empty request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetAvailableModelsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class TokenizerTaggerServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<TokenizerTaggerServiceBlockingStub> {
    private TokenizerTaggerServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TokenizerTaggerServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TokenizerTaggerServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Splits a sentence into its atomic parts.
     * </pre>
     */
    public opennlp.OpenNLPService.StringList tokenize(opennlp.OpenNLPService.TokenizeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTokenizeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Finds the boundaries of atomic parts in a string.
     * </pre>
     */
    public opennlp.OpenNLPService.SpanList tokenizePos(opennlp.OpenNLPService.TokenizePosRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTokenizePosMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Returns the available models which can be used for tokenization tagging.
     * </pre>
     */
    public opennlp.OpenNLPService.AvailableModels getAvailableModels(opennlp.OpenNLPService.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetAvailableModelsMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class TokenizerTaggerServiceFutureStub extends io.grpc.stub.AbstractFutureStub<TokenizerTaggerServiceFutureStub> {
    private TokenizerTaggerServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TokenizerTaggerServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TokenizerTaggerServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Splits a sentence into its atomic parts.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.StringList> tokenize(
        opennlp.OpenNLPService.TokenizeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTokenizeMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Finds the boundaries of atomic parts in a string.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.SpanList> tokenizePos(
        opennlp.OpenNLPService.TokenizePosRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTokenizePosMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Returns the available models which can be used for tokenization tagging.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.AvailableModels> getAvailableModels(
        opennlp.OpenNLPService.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetAvailableModelsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_TOKENIZE = 0;
  private static final int METHODID_TOKENIZE_POS = 1;
  private static final int METHODID_GET_AVAILABLE_MODELS = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final TokenizerTaggerServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(TokenizerTaggerServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_TOKENIZE:
          serviceImpl.tokenize((opennlp.OpenNLPService.TokenizeRequest) request,
              (io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList>) responseObserver);
          break;
        case METHODID_TOKENIZE_POS:
          serviceImpl.tokenizePos((opennlp.OpenNLPService.TokenizePosRequest) request,
              (io.grpc.stub.StreamObserver<opennlp.OpenNLPService.SpanList>) responseObserver);
          break;
        case METHODID_GET_AVAILABLE_MODELS:
          serviceImpl.getAvailableModels((opennlp.OpenNLPService.Empty) request,
              (io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class TokenizerTaggerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    TokenizerTaggerServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return opennlp.OpenNLPService.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("TokenizerTaggerService");
    }
  }

  private static final class TokenizerTaggerServiceFileDescriptorSupplier
      extends TokenizerTaggerServiceBaseDescriptorSupplier {
    TokenizerTaggerServiceFileDescriptorSupplier() {}
  }

  private static final class TokenizerTaggerServiceMethodDescriptorSupplier
      extends TokenizerTaggerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    TokenizerTaggerServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (TokenizerTaggerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new TokenizerTaggerServiceFileDescriptorSupplier())
              .addMethod(getTokenizeMethod())
              .addMethod(getTokenizePosMethod())
              .addMethod(getGetAvailableModelsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
