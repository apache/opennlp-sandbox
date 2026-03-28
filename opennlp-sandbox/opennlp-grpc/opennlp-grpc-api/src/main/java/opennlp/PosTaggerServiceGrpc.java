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
public final class PosTaggerServiceGrpc {

  private PosTaggerServiceGrpc() {}

  public static final String SERVICE_NAME = "opennlp.PosTaggerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<opennlp.OpenNLPService.TagRequest,
      opennlp.OpenNLPService.StringList> getTagMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Tag",
      requestType = opennlp.OpenNLPService.TagRequest.class,
      responseType = opennlp.OpenNLPService.StringList.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<opennlp.OpenNLPService.TagRequest,
      opennlp.OpenNLPService.StringList> getTagMethod() {
    io.grpc.MethodDescriptor<opennlp.OpenNLPService.TagRequest, opennlp.OpenNLPService.StringList> getTagMethod;
    if ((getTagMethod = PosTaggerServiceGrpc.getTagMethod) == null) {
      synchronized (PosTaggerServiceGrpc.class) {
        if ((getTagMethod = PosTaggerServiceGrpc.getTagMethod) == null) {
          PosTaggerServiceGrpc.getTagMethod = getTagMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.TagRequest, opennlp.OpenNLPService.StringList>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Tag"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.TagRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.StringList.getDefaultInstance()))
              .setSchemaDescriptor(new PosTaggerServiceMethodDescriptorSupplier("Tag"))
              .build();
        }
      }
    }
    return getTagMethod;
  }

  private static volatile io.grpc.MethodDescriptor<opennlp.OpenNLPService.TagWithContextRequest,
      opennlp.OpenNLPService.StringList> getTagWithContextMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "TagWithContext",
      requestType = opennlp.OpenNLPService.TagWithContextRequest.class,
      responseType = opennlp.OpenNLPService.StringList.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<opennlp.OpenNLPService.TagWithContextRequest,
      opennlp.OpenNLPService.StringList> getTagWithContextMethod() {
    io.grpc.MethodDescriptor<opennlp.OpenNLPService.TagWithContextRequest, opennlp.OpenNLPService.StringList> getTagWithContextMethod;
    if ((getTagWithContextMethod = PosTaggerServiceGrpc.getTagWithContextMethod) == null) {
      synchronized (PosTaggerServiceGrpc.class) {
        if ((getTagWithContextMethod = PosTaggerServiceGrpc.getTagWithContextMethod) == null) {
          PosTaggerServiceGrpc.getTagWithContextMethod = getTagWithContextMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.TagWithContextRequest, opennlp.OpenNLPService.StringList>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "TagWithContext"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.TagWithContextRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.StringList.getDefaultInstance()))
              .setSchemaDescriptor(new PosTaggerServiceMethodDescriptorSupplier("TagWithContext"))
              .build();
        }
      }
    }
    return getTagWithContextMethod;
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
    if ((getGetAvailableModelsMethod = PosTaggerServiceGrpc.getGetAvailableModelsMethod) == null) {
      synchronized (PosTaggerServiceGrpc.class) {
        if ((getGetAvailableModelsMethod = PosTaggerServiceGrpc.getGetAvailableModelsMethod) == null) {
          PosTaggerServiceGrpc.getGetAvailableModelsMethod = getGetAvailableModelsMethod =
              io.grpc.MethodDescriptor.<opennlp.OpenNLPService.Empty, opennlp.OpenNLPService.AvailableModels>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetAvailableModels"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  opennlp.OpenNLPService.AvailableModels.getDefaultInstance()))
              .setSchemaDescriptor(new PosTaggerServiceMethodDescriptorSupplier("GetAvailableModels"))
              .build();
        }
      }
    }
    return getGetAvailableModelsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PosTaggerServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PosTaggerServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PosTaggerServiceStub>() {
        @java.lang.Override
        public PosTaggerServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PosTaggerServiceStub(channel, callOptions);
        }
      };
    return PosTaggerServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PosTaggerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PosTaggerServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PosTaggerServiceBlockingStub>() {
        @java.lang.Override
        public PosTaggerServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PosTaggerServiceBlockingStub(channel, callOptions);
        }
      };
    return PosTaggerServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PosTaggerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PosTaggerServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PosTaggerServiceFutureStub>() {
        @java.lang.Override
        public PosTaggerServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PosTaggerServiceFutureStub(channel, callOptions);
        }
      };
    return PosTaggerServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class PosTaggerServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Assigns the sentence of tokens POS tags.
     * </pre>
     */
    public void tag(opennlp.OpenNLPService.TagRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTagMethod(), responseObserver);
    }

    /**
     * <pre>
     * Assigns the sentence of tokens POS tags with additional (string-based) context.
     * </pre>
     */
    public void tagWithContext(opennlp.OpenNLPService.TagWithContextRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTagWithContextMethod(), responseObserver);
    }

    /**
     * <pre>
     * Returns the available models which can be used for POS tagging.
     * </pre>
     */
    public void getAvailableModels(opennlp.OpenNLPService.Empty request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetAvailableModelsMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getTagMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                opennlp.OpenNLPService.TagRequest,
                opennlp.OpenNLPService.StringList>(
                  this, METHODID_TAG)))
          .addMethod(
            getTagWithContextMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                opennlp.OpenNLPService.TagWithContextRequest,
                opennlp.OpenNLPService.StringList>(
                  this, METHODID_TAG_WITH_CONTEXT)))
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
  public static final class PosTaggerServiceStub extends io.grpc.stub.AbstractAsyncStub<PosTaggerServiceStub> {
    private PosTaggerServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PosTaggerServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PosTaggerServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Assigns the sentence of tokens POS tags.
     * </pre>
     */
    public void tag(opennlp.OpenNLPService.TagRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTagMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Assigns the sentence of tokens POS tags with additional (string-based) context.
     * </pre>
     */
    public void tagWithContext(opennlp.OpenNLPService.TagWithContextRequest request,
        io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTagWithContextMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Returns the available models which can be used for POS tagging.
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
  public static final class PosTaggerServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<PosTaggerServiceBlockingStub> {
    private PosTaggerServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PosTaggerServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PosTaggerServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Assigns the sentence of tokens POS tags.
     * </pre>
     */
    public opennlp.OpenNLPService.StringList tag(opennlp.OpenNLPService.TagRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTagMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Assigns the sentence of tokens POS tags with additional (string-based) context.
     * </pre>
     */
    public opennlp.OpenNLPService.StringList tagWithContext(opennlp.OpenNLPService.TagWithContextRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTagWithContextMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Returns the available models which can be used for POS tagging.
     * </pre>
     */
    public opennlp.OpenNLPService.AvailableModels getAvailableModels(opennlp.OpenNLPService.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetAvailableModelsMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class PosTaggerServiceFutureStub extends io.grpc.stub.AbstractFutureStub<PosTaggerServiceFutureStub> {
    private PosTaggerServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PosTaggerServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PosTaggerServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Assigns the sentence of tokens POS tags.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.StringList> tag(
        opennlp.OpenNLPService.TagRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTagMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Assigns the sentence of tokens POS tags with additional (string-based) context.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.StringList> tagWithContext(
        opennlp.OpenNLPService.TagWithContextRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTagWithContextMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Returns the available models which can be used for POS tagging.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<opennlp.OpenNLPService.AvailableModels> getAvailableModels(
        opennlp.OpenNLPService.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetAvailableModelsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_TAG = 0;
  private static final int METHODID_TAG_WITH_CONTEXT = 1;
  private static final int METHODID_GET_AVAILABLE_MODELS = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final PosTaggerServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(PosTaggerServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_TAG:
          serviceImpl.tag((opennlp.OpenNLPService.TagRequest) request,
              (io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList>) responseObserver);
          break;
        case METHODID_TAG_WITH_CONTEXT:
          serviceImpl.tagWithContext((opennlp.OpenNLPService.TagWithContextRequest) request,
              (io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList>) responseObserver);
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

  private static abstract class PosTaggerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PosTaggerServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return opennlp.OpenNLPService.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PosTaggerService");
    }
  }

  private static final class PosTaggerServiceFileDescriptorSupplier
      extends PosTaggerServiceBaseDescriptorSupplier {
    PosTaggerServiceFileDescriptorSupplier() {}
  }

  private static final class PosTaggerServiceMethodDescriptorSupplier
      extends PosTaggerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    PosTaggerServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (PosTaggerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PosTaggerServiceFileDescriptorSupplier())
              .addMethod(getTagMethod())
              .addMethod(getTagWithContextMethod())
              .addMethod(getGetAvailableModelsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
