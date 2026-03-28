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
public final class SentenceDetectorServiceGrpc {

  private SentenceDetectorServiceGrpc() {}

  public static final String SERVICE_NAME = "opennlp.SentenceDetectorService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<opennlp.OpenNLPService.SentDetectRequest,
      opennlp.OpenNLPService.StringList> getSentDetectMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "sentDetect",
      requestType = opennlp.OpenNLPService.SentDetectRequest.class,
      responseType = opennlp.OpenNLPService.StringList.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<opennlp.OpenNLPService.SentDetectRequest,
      opennlp.OpenNLPService.StringList> getSentDetectMethod() {
    io.grpc.MethodDescriptor<opennlp.OpenNLPService.SentDetectRequest, opennlp.OpenNLPService.StringList> getSentDetectMethod;
    if ((getSentDetectMethod = SentenceDetectorServiceGrpc.getSentDetectMethod) == null) {
      synchronized (SentenceDetectorServiceGrpc.class) {
        if ((getSentDetectMethod = SentenceDetectorServiceGrpc.getSentDetectMethod) == null) {
          SentenceDetectorServiceGrpc.getSentDetectMethod = getSentDetectMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.SentDetectRequest, opennlp.OpenNLPService.StringList>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "sentDetect"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.SentDetectRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.StringList.getDefaultInstance()))
              .setSchemaDescriptor(new SentenceDetectorServiceMethodDescriptorSupplier("sentDetect"))
              .build();
        }
      }
    }
    return getSentDetectMethod;
  }

  private static volatile io.grpc.MethodDescriptor<opennlp.OpenNLPService.SentDetectPosRequest,
      opennlp.OpenNLPService.SpanList> getSentPosDetectMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "sentPosDetect",
      requestType = opennlp.OpenNLPService.SentDetectPosRequest.class,
      responseType = opennlp.OpenNLPService.SpanList.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<opennlp.OpenNLPService.SentDetectPosRequest,
      opennlp.OpenNLPService.SpanList> getSentPosDetectMethod() {
    io.grpc.MethodDescriptor<opennlp.OpenNLPService.SentDetectPosRequest, opennlp.OpenNLPService.SpanList> getSentPosDetectMethod;
    if ((getSentPosDetectMethod = SentenceDetectorServiceGrpc.getSentPosDetectMethod) == null) {
      synchronized (SentenceDetectorServiceGrpc.class) {
        if ((getSentPosDetectMethod = SentenceDetectorServiceGrpc.getSentPosDetectMethod) == null) {
          SentenceDetectorServiceGrpc.getSentPosDetectMethod = getSentPosDetectMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.SentDetectPosRequest, opennlp.OpenNLPService.SpanList>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "sentPosDetect"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.SentDetectPosRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.SpanList.getDefaultInstance()))
              .setSchemaDescriptor(new SentenceDetectorServiceMethodDescriptorSupplier("sentPosDetect"))
              .build();
        }
      }
    }
    return getSentPosDetectMethod;
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
    if ((getGetAvailableModelsMethod = SentenceDetectorServiceGrpc.getGetAvailableModelsMethod) == null) {
      synchronized (SentenceDetectorServiceGrpc.class) {
        if ((getGetAvailableModelsMethod = SentenceDetectorServiceGrpc.getGetAvailableModelsMethod) == null) {
          SentenceDetectorServiceGrpc.getGetAvailableModelsMethod = getGetAvailableModelsMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.Empty, opennlp.OpenNLPService.AvailableModels>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetAvailableModels"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.AvailableModels.getDefaultInstance()))
              .setSchemaDescriptor(new SentenceDetectorServiceMethodDescriptorSupplier("GetAvailableModels"))
              .build();
        }
      }
    }
    return getGetAvailableModelsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static SentenceDetectorServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SentenceDetectorServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SentenceDetectorServiceStub>() {
        @java.lang.Override
        public SentenceDetectorServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SentenceDetectorServiceStub(channel, callOptions);
        }
      };
    return SentenceDetectorServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static SentenceDetectorServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SentenceDetectorServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SentenceDetectorServiceBlockingStub>() {
        @java.lang.Override
        public SentenceDetectorServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SentenceDetectorServiceBlockingStub(channel, callOptions);
        }
      };
    return SentenceDetectorServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static SentenceDetectorServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SentenceDetectorServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SentenceDetectorServiceFutureStub>() {
        @java.lang.Override
        public SentenceDetectorServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SentenceDetectorServiceFutureStub(channel, callOptions);
        }
      };
    return SentenceDetectorServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class SentenceDetectorServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Detects sentences in a character sequence.
     * </pre>
     */
    public void sentDetect(opennlp.OpenNLPService.SentDetectRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSentDetectMethod(), responseObserver);
    }

    /**
     * <pre>
     * Detects sentences in a character sequence.
     * </pre>
     */
    public void sentPosDetect(opennlp.OpenNLPService.SentDetectPosRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.SpanList> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSentPosDetectMethod(), responseObserver);
    }

    /**
     * <pre>
     * Returns the available models which can be used for sentence detection.
     * </pre>
     */
    public void getAvailableModels(opennlp.OpenNLPService.Empty request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetAvailableModelsMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getSentDetectMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                opennlp.OpenNLPService.SentDetectRequest,
                opennlp.OpenNLPService.StringList>(
                  this, METHODID_SENT_DETECT)))
          .addMethod(
            getSentPosDetectMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                opennlp.OpenNLPService.SentDetectPosRequest,
                opennlp.OpenNLPService.SpanList>(
                  this, METHODID_SENT_POS_DETECT)))
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
  public static final class SentenceDetectorServiceStub extends io.grpc.stub.AbstractAsyncStub<SentenceDetectorServiceStub> {
    private SentenceDetectorServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SentenceDetectorServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SentenceDetectorServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Detects sentences in a character sequence.
     * </pre>
     */
    public void sentDetect(opennlp.OpenNLPService.SentDetectRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSentDetectMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Detects sentences in a character sequence.
     * </pre>
     */
    public void sentPosDetect(opennlp.OpenNLPService.SentDetectPosRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.SpanList> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSentPosDetectMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Returns the available models which can be used for sentence detection.
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
  public static final class SentenceDetectorServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<SentenceDetectorServiceBlockingStub> {
    private SentenceDetectorServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SentenceDetectorServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SentenceDetectorServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Detects sentences in a character sequence.
     * </pre>
     */
    public opennlp.OpenNLPService.StringList sentDetect(opennlp.OpenNLPService.SentDetectRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSentDetectMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Detects sentences in a character sequence.
     * </pre>
     */
    public opennlp.OpenNLPService.SpanList sentPosDetect(opennlp.OpenNLPService.SentDetectPosRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSentPosDetectMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Returns the available models which can be used for sentence detection.
     * </pre>
     */
    public opennlp.OpenNLPService.AvailableModels getAvailableModels(opennlp.OpenNLPService.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetAvailableModelsMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class SentenceDetectorServiceFutureStub extends io.grpc.stub.AbstractFutureStub<SentenceDetectorServiceFutureStub> {
    private SentenceDetectorServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SentenceDetectorServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SentenceDetectorServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Detects sentences in a character sequence.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.StringList> sentDetect(
        opennlp.OpenNLPService.SentDetectRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSentDetectMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Detects sentences in a character sequence.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.SpanList> sentPosDetect(
        opennlp.OpenNLPService.SentDetectPosRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSentPosDetectMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Returns the available models which can be used for sentence detection.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.AvailableModels> getAvailableModels(
        opennlp.OpenNLPService.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetAvailableModelsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SENT_DETECT = 0;
  private static final int METHODID_SENT_POS_DETECT = 1;
  private static final int METHODID_GET_AVAILABLE_MODELS = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final SentenceDetectorServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(SentenceDetectorServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SENT_DETECT:
          serviceImpl.sentDetect((opennlp.OpenNLPService.SentDetectRequest) request,
              (io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList>) responseObserver);
          break;
        case METHODID_SENT_POS_DETECT:
          serviceImpl.sentPosDetect((opennlp.OpenNLPService.SentDetectPosRequest) request,
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

  private static abstract class SentenceDetectorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    SentenceDetectorServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return opennlp.OpenNLPService.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("SentenceDetectorService");
    }
  }

  private static final class SentenceDetectorServiceFileDescriptorSupplier
      extends SentenceDetectorServiceBaseDescriptorSupplier {
    SentenceDetectorServiceFileDescriptorSupplier() {}
  }

  private static final class SentenceDetectorServiceMethodDescriptorSupplier
      extends SentenceDetectorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    SentenceDetectorServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (SentenceDetectorServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new SentenceDetectorServiceFileDescriptorSupplier())
              .addMethod(getSentDetectMethod())
              .addMethod(getSentPosDetectMethod())
              .addMethod(getGetAvailableModelsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
