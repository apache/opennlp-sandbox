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

public final class OpenNLPService {
  private OpenNLPService() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  /**
   * Protobuf enum {@code opennlp.POSTagFormat}
   */
  public enum POSTagFormat
      implements com.google.protobuf.ProtocolMessageEnum {
    /**
     * <pre>
     * Universal Dependencies format (current opennlp-models)
     * </pre>
     *
     * <code>UD = 0;</code>
     */
    UD(0),
    /**
     * <pre>
     * Penn Treebank format (deprecated). Use UD instead. Converting from UD to PENN isn't a lossless operation.
     * </pre>
     *
     * <code>PENN = 1;</code>
     */
    PENN(1),
    /**
     * <pre>
     * Unknown tag format
     * </pre>
     *
     * <code>UNKNOWN = 2;</code>
     */
    UNKNOWN(2),
    /**
     * <pre>
     * Custom-defined tag format
     * </pre>
     *
     * <code>CUSTOM = 3;</code>
     */
    CUSTOM(3),
    UNRECOGNIZED(-1),
    ;

    /**
     * <pre>
     * Universal Dependencies format (current opennlp-models)
     * </pre>
     *
     * <code>UD = 0;</code>
     */
    public static final int UD_VALUE = 0;
    /**
     * <pre>
     * Penn Treebank format (deprecated). Use UD instead. Converting from UD to PENN isn't a lossless operation.
     * </pre>
     *
     * <code>PENN = 1;</code>
     */
    public static final int PENN_VALUE = 1;
    /**
     * <pre>
     * Unknown tag format
     * </pre>
     *
     * <code>UNKNOWN = 2;</code>
     */
    public static final int UNKNOWN_VALUE = 2;
    /**
     * <pre>
     * Custom-defined tag format
     * </pre>
     *
     * <code>CUSTOM = 3;</code>
     */
    public static final int CUSTOM_VALUE = 3;


    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new java.lang.IllegalArgumentException(
            "Can't get the number of an unknown enum value.");
      }
      return value;
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static POSTagFormat valueOf(int value) {
      return forNumber(value);
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     */
    public static POSTagFormat forNumber(int value) {
      switch (value) {
        case 0: return UD;
        case 1: return PENN;
        case 2: return UNKNOWN;
        case 3: return CUSTOM;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<POSTagFormat>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        POSTagFormat> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<POSTagFormat>() {
            public POSTagFormat findValueByNumber(int number) {
              return POSTagFormat.forNumber(number);
            }
          };

    public final com.google.protobuf.Descriptors.EnumValueDescriptor
        getValueDescriptor() {
      if (this == UNRECOGNIZED) {
        throw new java.lang.IllegalStateException(
            "Can't get the descriptor of an unrecognized enum value.");
      }
      return getDescriptor().getValues().get(ordinal());
    }
    public final com.google.protobuf.Descriptors.EnumDescriptor
        getDescriptorForType() {
      return getDescriptor();
    }
    public static final com.google.protobuf.Descriptors.EnumDescriptor
        getDescriptor() {
      return opennlp.OpenNLPService.getDescriptor().getEnumTypes().get(0);
    }

    private static final POSTagFormat[] VALUES = values();

    public static POSTagFormat valueOf(
        com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
      if (desc.getType() != getDescriptor()) {
        throw new java.lang.IllegalArgumentException(
          "EnumValueDescriptor is not for this type.");
      }
      if (desc.getIndex() == -1) {
        return UNRECOGNIZED;
      }
      return VALUES[desc.getIndex()];
    }

    private final int value;

    private POSTagFormat(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:opennlp.POSTagFormat)
  }

  public interface TagRequestOrBuilder extends
      // @@protoc_insertion_point(interface_extends:opennlp.TagRequest)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>repeated string sentence = 1;</code>
     * @return A list containing the sentence.
     */
    java.util.List<java.lang.String>
        getSentenceList();
    /**
     * <code>repeated string sentence = 1;</code>
     * @return The count of sentence.
     */
    int getSentenceCount();
    /**
     * <code>repeated string sentence = 1;</code>
     * @param index The index of the element to return.
     * @return The sentence at the given index.
     */
    java.lang.String getSentence(int index);
    /**
     * <code>repeated string sentence = 1;</code>
     * @param index The index of the value to return.
     * @return The bytes of the sentence at the given index.
     */
    com.google.protobuf.ByteString
        getSentenceBytes(int index);

    /**
     * <code>.opennlp.POSTagFormat format = 2;</code>
     * @return The enum numeric value on the wire for format.
     */
    int getFormatValue();
    /**
     * <code>.opennlp.POSTagFormat format = 2;</code>
     * @return The format.
     */
    opennlp.OpenNLPService.POSTagFormat getFormat();

    /**
     * <code>string model_hash = 3;</code>
     * @return The modelHash.
     */
    java.lang.String getModelHash();
    /**
     * <code>string model_hash = 3;</code>
     * @return The bytes for modelHash.
     */
    com.google.protobuf.ByteString
        getModelHashBytes();
  }
  /**
   * Protobuf type {@code opennlp.TagRequest}
   */
  public static final class TagRequest extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:opennlp.TagRequest)
      TagRequestOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use TagRequest.newBuilder() to construct.
    private TagRequest(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private TagRequest() {
      sentence_ = com.google.protobuf.LazyStringArrayList.EMPTY;
      format_ = 0;
      modelHash_ = "";
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new TagRequest();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private TagRequest(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {
              java.lang.String s = input.readStringRequireUtf8();
              if (!((mutable_bitField0_ & 0x00000001) != 0)) {
                sentence_ = new com.google.protobuf.LazyStringArrayList();
                mutable_bitField0_ |= 0x00000001;
              }
              sentence_.add(s);
              break;
            }
            case 16: {
              int rawValue = input.readEnum();

              format_ = rawValue;
              break;
            }
            case 26: {
              java.lang.String s = input.readStringRequireUtf8();

              modelHash_ = s;
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000001) != 0)) {
          sentence_ = sentence_.getUnmodifiableView();
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return opennlp.OpenNLPService.internal_static_opennlp_TagRequest_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return opennlp.OpenNLPService.internal_static_opennlp_TagRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              opennlp.OpenNLPService.TagRequest.class, opennlp.OpenNLPService.TagRequest.Builder.class);
    }

    public static final int SENTENCE_FIELD_NUMBER = 1;
    private com.google.protobuf.LazyStringList sentence_;
    /**
     * <code>repeated string sentence = 1;</code>
     * @return A list containing the sentence.
     */
    public com.google.protobuf.ProtocolStringList
        getSentenceList() {
      return sentence_;
    }
    /**
     * <code>repeated string sentence = 1;</code>
     * @return The count of sentence.
     */
    public int getSentenceCount() {
      return sentence_.size();
    }
    /**
     * <code>repeated string sentence = 1;</code>
     * @param index The index of the element to return.
     * @return The sentence at the given index.
     */
    public java.lang.String getSentence(int index) {
      return sentence_.get(index);
    }
    /**
     * <code>repeated string sentence = 1;</code>
     * @param index The index of the value to return.
     * @return The bytes of the sentence at the given index.
     */
    public com.google.protobuf.ByteString
        getSentenceBytes(int index) {
      return sentence_.getByteString(index);
    }

    public static final int FORMAT_FIELD_NUMBER = 2;
    private int format_;
    /**
     * <code>.opennlp.POSTagFormat format = 2;</code>
     * @return The enum numeric value on the wire for format.
     */
    @java.lang.Override public int getFormatValue() {
      return format_;
    }
    /**
     * <code>.opennlp.POSTagFormat format = 2;</code>
     * @return The format.
     */
    @java.lang.Override public opennlp.OpenNLPService.POSTagFormat getFormat() {
      @SuppressWarnings("deprecation")
      opennlp.OpenNLPService.POSTagFormat result = opennlp.OpenNLPService.POSTagFormat.valueOf(format_);
      return result == null ? opennlp.OpenNLPService.POSTagFormat.UNRECOGNIZED : result;
    }

    public static final int MODEL_HASH_FIELD_NUMBER = 3;
    private volatile java.lang.Object modelHash_;
    /**
     * <code>string model_hash = 3;</code>
     * @return The modelHash.
     */
    @java.lang.Override
    public java.lang.String getModelHash() {
      java.lang.Object ref = modelHash_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        modelHash_ = s;
        return s;
      }
    }
    /**
     * <code>string model_hash = 3;</code>
     * @return The bytes for modelHash.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getModelHashBytes() {
      java.lang.Object ref = modelHash_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        modelHash_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      for (int i = 0; i < sentence_.size(); i++) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 1, sentence_.getRaw(i));
      }
      if (format_ != opennlp.OpenNLPService.POSTagFormat.UD.getNumber()) {
        output.writeEnum(2, format_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(modelHash_)) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 3, modelHash_);
      }
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      {
        int dataSize = 0;
        for (int i = 0; i < sentence_.size(); i++) {
          dataSize += computeStringSizeNoTag(sentence_.getRaw(i));
        }
        size += dataSize;
        size += 1 * getSentenceList().size();
      }
      if (format_ != opennlp.OpenNLPService.POSTagFormat.UD.getNumber()) {
        size += com.google.protobuf.CodedOutputStream
          .computeEnumSize(2, format_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(modelHash_)) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(3, modelHash_);
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof opennlp.OpenNLPService.TagRequest)) {
        return super.equals(obj);
      }
      opennlp.OpenNLPService.TagRequest other = (opennlp.OpenNLPService.TagRequest) obj;

      if (!getSentenceList()
          .equals(other.getSentenceList())) return false;
      if (format_ != other.format_) return false;
      if (!getModelHash()
          .equals(other.getModelHash())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      if (getSentenceCount() > 0) {
        hash = (37 * hash) + SENTENCE_FIELD_NUMBER;
        hash = (53 * hash) + getSentenceList().hashCode();
      }
      hash = (37 * hash) + FORMAT_FIELD_NUMBER;
      hash = (53 * hash) + format_;
      hash = (37 * hash) + MODEL_HASH_FIELD_NUMBER;
      hash = (53 * hash) + getModelHash().hashCode();
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static opennlp.OpenNLPService.TagRequest parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagRequest parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.TagRequest parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.TagRequest parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(opennlp.OpenNLPService.TagRequest prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code opennlp.TagRequest}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:opennlp.TagRequest)
        opennlp.OpenNLPService.TagRequestOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return opennlp.OpenNLPService.internal_static_opennlp_TagRequest_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return opennlp.OpenNLPService.internal_static_opennlp_TagRequest_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                opennlp.OpenNLPService.TagRequest.class, opennlp.OpenNLPService.TagRequest.Builder.class);
      }

      // Construct using opennlp.OpenNLPService.TagRequest.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        sentence_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        format_ = 0;

        modelHash_ = "";

        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return opennlp.OpenNLPService.internal_static_opennlp_TagRequest_descriptor;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.TagRequest getDefaultInstanceForType() {
        return opennlp.OpenNLPService.TagRequest.getDefaultInstance();
      }

      @java.lang.Override
      public opennlp.OpenNLPService.TagRequest build() {
        opennlp.OpenNLPService.TagRequest result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.TagRequest buildPartial() {
        opennlp.OpenNLPService.TagRequest result = new opennlp.OpenNLPService.TagRequest(this);
        int from_bitField0_ = bitField0_;
        if (((bitField0_ & 0x00000001) != 0)) {
          sentence_ = sentence_.getUnmodifiableView();
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.sentence_ = sentence_;
        result.format_ = format_;
        result.modelHash_ = modelHash_;
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof opennlp.OpenNLPService.TagRequest) {
          return mergeFrom((opennlp.OpenNLPService.TagRequest)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(opennlp.OpenNLPService.TagRequest other) {
        if (other == opennlp.OpenNLPService.TagRequest.getDefaultInstance()) return this;
        if (!other.sentence_.isEmpty()) {
          if (sentence_.isEmpty()) {
            sentence_ = other.sentence_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureSentenceIsMutable();
            sentence_.addAll(other.sentence_);
          }
          onChanged();
        }
        if (other.format_ != 0) {
          setFormatValue(other.getFormatValue());
        }
        if (!other.getModelHash().isEmpty()) {
          modelHash_ = other.modelHash_;
          onChanged();
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        opennlp.OpenNLPService.TagRequest parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (opennlp.OpenNLPService.TagRequest) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private com.google.protobuf.LazyStringList sentence_ = com.google.protobuf.LazyStringArrayList.EMPTY;
      private void ensureSentenceIsMutable() {
        if (!((bitField0_ & 0x00000001) != 0)) {
          sentence_ = new com.google.protobuf.LazyStringArrayList(sentence_);
          bitField0_ |= 0x00000001;
         }
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @return A list containing the sentence.
       */
      public com.google.protobuf.ProtocolStringList
          getSentenceList() {
        return sentence_.getUnmodifiableView();
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @return The count of sentence.
       */
      public int getSentenceCount() {
        return sentence_.size();
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param index The index of the element to return.
       * @return The sentence at the given index.
       */
      public java.lang.String getSentence(int index) {
        return sentence_.get(index);
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param index The index of the value to return.
       * @return The bytes of the sentence at the given index.
       */
      public com.google.protobuf.ByteString
          getSentenceBytes(int index) {
        return sentence_.getByteString(index);
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param index The index to set the value at.
       * @param value The sentence to set.
       * @return This builder for chaining.
       */
      public Builder setSentence(
          int index, java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureSentenceIsMutable();
        sentence_.set(index, value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param value The sentence to add.
       * @return This builder for chaining.
       */
      public Builder addSentence(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureSentenceIsMutable();
        sentence_.add(value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param values The sentence to add.
       * @return This builder for chaining.
       */
      public Builder addAllSentence(
          java.lang.Iterable<java.lang.String> values) {
        ensureSentenceIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, sentence_);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearSentence() {
        sentence_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param value The bytes of the sentence to add.
       * @return This builder for chaining.
       */
      public Builder addSentenceBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        ensureSentenceIsMutable();
        sentence_.add(value);
        onChanged();
        return this;
      }

      private int format_ = 0;
      /**
       * <code>.opennlp.POSTagFormat format = 2;</code>
       * @return The enum numeric value on the wire for format.
       */
      @java.lang.Override public int getFormatValue() {
        return format_;
      }
      /**
       * <code>.opennlp.POSTagFormat format = 2;</code>
       * @param value The enum numeric value on the wire for format to set.
       * @return This builder for chaining.
       */
      public Builder setFormatValue(int value) {
        
        format_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>.opennlp.POSTagFormat format = 2;</code>
       * @return The format.
       */
      @java.lang.Override
      public opennlp.OpenNLPService.POSTagFormat getFormat() {
        @SuppressWarnings("deprecation")
        opennlp.OpenNLPService.POSTagFormat result = opennlp.OpenNLPService.POSTagFormat.valueOf(format_);
        return result == null ? opennlp.OpenNLPService.POSTagFormat.UNRECOGNIZED : result;
      }
      /**
       * <code>.opennlp.POSTagFormat format = 2;</code>
       * @param value The format to set.
       * @return This builder for chaining.
       */
      public Builder setFormat(opennlp.OpenNLPService.POSTagFormat value) {
        if (value == null) {
          throw new NullPointerException();
        }
        
        format_ = value.getNumber();
        onChanged();
        return this;
      }
      /**
       * <code>.opennlp.POSTagFormat format = 2;</code>
       * @return This builder for chaining.
       */
      public Builder clearFormat() {
        
        format_ = 0;
        onChanged();
        return this;
      }

      private java.lang.Object modelHash_ = "";
      /**
       * <code>string model_hash = 3;</code>
       * @return The modelHash.
       */
      public java.lang.String getModelHash() {
        java.lang.Object ref = modelHash_;
        if (!(ref instanceof java.lang.String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          java.lang.String s = bs.toStringUtf8();
          modelHash_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>string model_hash = 3;</code>
       * @return The bytes for modelHash.
       */
      public com.google.protobuf.ByteString
          getModelHashBytes() {
        java.lang.Object ref = modelHash_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          modelHash_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>string model_hash = 3;</code>
       * @param value The modelHash to set.
       * @return This builder for chaining.
       */
      public Builder setModelHash(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        modelHash_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>string model_hash = 3;</code>
       * @return This builder for chaining.
       */
      public Builder clearModelHash() {
        
        modelHash_ = getDefaultInstance().getModelHash();
        onChanged();
        return this;
      }
      /**
       * <code>string model_hash = 3;</code>
       * @param value The bytes for modelHash to set.
       * @return This builder for chaining.
       */
      public Builder setModelHashBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        modelHash_ = value;
        onChanged();
        return this;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:opennlp.TagRequest)
    }

    // @@protoc_insertion_point(class_scope:opennlp.TagRequest)
    private static final opennlp.OpenNLPService.TagRequest DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new opennlp.OpenNLPService.TagRequest();
    }

    public static opennlp.OpenNLPService.TagRequest getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<TagRequest>
        PARSER = new com.google.protobuf.AbstractParser<TagRequest>() {
      @java.lang.Override
      public TagRequest parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new TagRequest(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<TagRequest> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<TagRequest> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public opennlp.OpenNLPService.TagRequest getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  public interface TagWithContextRequestOrBuilder extends
      // @@protoc_insertion_point(interface_extends:opennlp.TagWithContextRequest)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>repeated string sentence = 1;</code>
     * @return A list containing the sentence.
     */
    java.util.List<java.lang.String>
        getSentenceList();
    /**
     * <code>repeated string sentence = 1;</code>
     * @return The count of sentence.
     */
    int getSentenceCount();
    /**
     * <code>repeated string sentence = 1;</code>
     * @param index The index of the element to return.
     * @return The sentence at the given index.
     */
    java.lang.String getSentence(int index);
    /**
     * <code>repeated string sentence = 1;</code>
     * @param index The index of the value to return.
     * @return The bytes of the sentence at the given index.
     */
    com.google.protobuf.ByteString
        getSentenceBytes(int index);

    /**
     * <code>repeated string additional_context = 2;</code>
     * @return A list containing the additionalContext.
     */
    java.util.List<java.lang.String>
        getAdditionalContextList();
    /**
     * <code>repeated string additional_context = 2;</code>
     * @return The count of additionalContext.
     */
    int getAdditionalContextCount();
    /**
     * <code>repeated string additional_context = 2;</code>
     * @param index The index of the element to return.
     * @return The additionalContext at the given index.
     */
    java.lang.String getAdditionalContext(int index);
    /**
     * <code>repeated string additional_context = 2;</code>
     * @param index The index of the value to return.
     * @return The bytes of the additionalContext at the given index.
     */
    com.google.protobuf.ByteString
        getAdditionalContextBytes(int index);

    /**
     * <code>.opennlp.POSTagFormat format = 3;</code>
     * @return The enum numeric value on the wire for format.
     */
    int getFormatValue();
    /**
     * <code>.opennlp.POSTagFormat format = 3;</code>
     * @return The format.
     */
    opennlp.OpenNLPService.POSTagFormat getFormat();

    /**
     * <code>string model_hash = 4;</code>
     * @return The modelHash.
     */
    java.lang.String getModelHash();
    /**
     * <code>string model_hash = 4;</code>
     * @return The bytes for modelHash.
     */
    com.google.protobuf.ByteString
        getModelHashBytes();
  }
  /**
   * Protobuf type {@code opennlp.TagWithContextRequest}
   */
  public static final class TagWithContextRequest extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:opennlp.TagWithContextRequest)
      TagWithContextRequestOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use TagWithContextRequest.newBuilder() to construct.
    private TagWithContextRequest(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private TagWithContextRequest() {
      sentence_ = com.google.protobuf.LazyStringArrayList.EMPTY;
      additionalContext_ = com.google.protobuf.LazyStringArrayList.EMPTY;
      format_ = 0;
      modelHash_ = "";
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new TagWithContextRequest();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private TagWithContextRequest(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {
              java.lang.String s = input.readStringRequireUtf8();
              if (!((mutable_bitField0_ & 0x00000001) != 0)) {
                sentence_ = new com.google.protobuf.LazyStringArrayList();
                mutable_bitField0_ |= 0x00000001;
              }
              sentence_.add(s);
              break;
            }
            case 18: {
              java.lang.String s = input.readStringRequireUtf8();
              if (!((mutable_bitField0_ & 0x00000002) != 0)) {
                additionalContext_ = new com.google.protobuf.LazyStringArrayList();
                mutable_bitField0_ |= 0x00000002;
              }
              additionalContext_.add(s);
              break;
            }
            case 24: {
              int rawValue = input.readEnum();

              format_ = rawValue;
              break;
            }
            case 34: {
              java.lang.String s = input.readStringRequireUtf8();

              modelHash_ = s;
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000001) != 0)) {
          sentence_ = sentence_.getUnmodifiableView();
        }
        if (((mutable_bitField0_ & 0x00000002) != 0)) {
          additionalContext_ = additionalContext_.getUnmodifiableView();
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return opennlp.OpenNLPService.internal_static_opennlp_TagWithContextRequest_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return opennlp.OpenNLPService.internal_static_opennlp_TagWithContextRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              opennlp.OpenNLPService.TagWithContextRequest.class, opennlp.OpenNLPService.TagWithContextRequest.Builder.class);
    }

    public static final int SENTENCE_FIELD_NUMBER = 1;
    private com.google.protobuf.LazyStringList sentence_;
    /**
     * <code>repeated string sentence = 1;</code>
     * @return A list containing the sentence.
     */
    public com.google.protobuf.ProtocolStringList
        getSentenceList() {
      return sentence_;
    }
    /**
     * <code>repeated string sentence = 1;</code>
     * @return The count of sentence.
     */
    public int getSentenceCount() {
      return sentence_.size();
    }
    /**
     * <code>repeated string sentence = 1;</code>
     * @param index The index of the element to return.
     * @return The sentence at the given index.
     */
    public java.lang.String getSentence(int index) {
      return sentence_.get(index);
    }
    /**
     * <code>repeated string sentence = 1;</code>
     * @param index The index of the value to return.
     * @return The bytes of the sentence at the given index.
     */
    public com.google.protobuf.ByteString
        getSentenceBytes(int index) {
      return sentence_.getByteString(index);
    }

    public static final int ADDITIONAL_CONTEXT_FIELD_NUMBER = 2;
    private com.google.protobuf.LazyStringList additionalContext_;
    /**
     * <code>repeated string additional_context = 2;</code>
     * @return A list containing the additionalContext.
     */
    public com.google.protobuf.ProtocolStringList
        getAdditionalContextList() {
      return additionalContext_;
    }
    /**
     * <code>repeated string additional_context = 2;</code>
     * @return The count of additionalContext.
     */
    public int getAdditionalContextCount() {
      return additionalContext_.size();
    }
    /**
     * <code>repeated string additional_context = 2;</code>
     * @param index The index of the element to return.
     * @return The additionalContext at the given index.
     */
    public java.lang.String getAdditionalContext(int index) {
      return additionalContext_.get(index);
    }
    /**
     * <code>repeated string additional_context = 2;</code>
     * @param index The index of the value to return.
     * @return The bytes of the additionalContext at the given index.
     */
    public com.google.protobuf.ByteString
        getAdditionalContextBytes(int index) {
      return additionalContext_.getByteString(index);
    }

    public static final int FORMAT_FIELD_NUMBER = 3;
    private int format_;
    /**
     * <code>.opennlp.POSTagFormat format = 3;</code>
     * @return The enum numeric value on the wire for format.
     */
    @java.lang.Override public int getFormatValue() {
      return format_;
    }
    /**
     * <code>.opennlp.POSTagFormat format = 3;</code>
     * @return The format.
     */
    @java.lang.Override public opennlp.OpenNLPService.POSTagFormat getFormat() {
      @SuppressWarnings("deprecation")
      opennlp.OpenNLPService.POSTagFormat result = opennlp.OpenNLPService.POSTagFormat.valueOf(format_);
      return result == null ? opennlp.OpenNLPService.POSTagFormat.UNRECOGNIZED : result;
    }

    public static final int MODEL_HASH_FIELD_NUMBER = 4;
    private volatile java.lang.Object modelHash_;
    /**
     * <code>string model_hash = 4;</code>
     * @return The modelHash.
     */
    @java.lang.Override
    public java.lang.String getModelHash() {
      java.lang.Object ref = modelHash_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        modelHash_ = s;
        return s;
      }
    }
    /**
     * <code>string model_hash = 4;</code>
     * @return The bytes for modelHash.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getModelHashBytes() {
      java.lang.Object ref = modelHash_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        modelHash_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      for (int i = 0; i < sentence_.size(); i++) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 1, sentence_.getRaw(i));
      }
      for (int i = 0; i < additionalContext_.size(); i++) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 2, additionalContext_.getRaw(i));
      }
      if (format_ != opennlp.OpenNLPService.POSTagFormat.UD.getNumber()) {
        output.writeEnum(3, format_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(modelHash_)) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 4, modelHash_);
      }
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      {
        int dataSize = 0;
        for (int i = 0; i < sentence_.size(); i++) {
          dataSize += computeStringSizeNoTag(sentence_.getRaw(i));
        }
        size += dataSize;
        size += 1 * getSentenceList().size();
      }
      {
        int dataSize = 0;
        for (int i = 0; i < additionalContext_.size(); i++) {
          dataSize += computeStringSizeNoTag(additionalContext_.getRaw(i));
        }
        size += dataSize;
        size += 1 * getAdditionalContextList().size();
      }
      if (format_ != opennlp.OpenNLPService.POSTagFormat.UD.getNumber()) {
        size += com.google.protobuf.CodedOutputStream
          .computeEnumSize(3, format_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(modelHash_)) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(4, modelHash_);
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof opennlp.OpenNLPService.TagWithContextRequest)) {
        return super.equals(obj);
      }
      opennlp.OpenNLPService.TagWithContextRequest other = (opennlp.OpenNLPService.TagWithContextRequest) obj;

      if (!getSentenceList()
          .equals(other.getSentenceList())) return false;
      if (!getAdditionalContextList()
          .equals(other.getAdditionalContextList())) return false;
      if (format_ != other.format_) return false;
      if (!getModelHash()
          .equals(other.getModelHash())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      if (getSentenceCount() > 0) {
        hash = (37 * hash) + SENTENCE_FIELD_NUMBER;
        hash = (53 * hash) + getSentenceList().hashCode();
      }
      if (getAdditionalContextCount() > 0) {
        hash = (37 * hash) + ADDITIONAL_CONTEXT_FIELD_NUMBER;
        hash = (53 * hash) + getAdditionalContextList().hashCode();
      }
      hash = (37 * hash) + FORMAT_FIELD_NUMBER;
      hash = (53 * hash) + format_;
      hash = (37 * hash) + MODEL_HASH_FIELD_NUMBER;
      hash = (53 * hash) + getModelHash().hashCode();
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.TagWithContextRequest parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(opennlp.OpenNLPService.TagWithContextRequest prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code opennlp.TagWithContextRequest}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:opennlp.TagWithContextRequest)
        opennlp.OpenNLPService.TagWithContextRequestOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return opennlp.OpenNLPService.internal_static_opennlp_TagWithContextRequest_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return opennlp.OpenNLPService.internal_static_opennlp_TagWithContextRequest_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                opennlp.OpenNLPService.TagWithContextRequest.class, opennlp.OpenNLPService.TagWithContextRequest.Builder.class);
      }

      // Construct using opennlp.OpenNLPService.TagWithContextRequest.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        sentence_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        additionalContext_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000002);
        format_ = 0;

        modelHash_ = "";

        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return opennlp.OpenNLPService.internal_static_opennlp_TagWithContextRequest_descriptor;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.TagWithContextRequest getDefaultInstanceForType() {
        return opennlp.OpenNLPService.TagWithContextRequest.getDefaultInstance();
      }

      @java.lang.Override
      public opennlp.OpenNLPService.TagWithContextRequest build() {
        opennlp.OpenNLPService.TagWithContextRequest result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.TagWithContextRequest buildPartial() {
        opennlp.OpenNLPService.TagWithContextRequest result = new opennlp.OpenNLPService.TagWithContextRequest(this);
        int from_bitField0_ = bitField0_;
        if (((bitField0_ & 0x00000001) != 0)) {
          sentence_ = sentence_.getUnmodifiableView();
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.sentence_ = sentence_;
        if (((bitField0_ & 0x00000002) != 0)) {
          additionalContext_ = additionalContext_.getUnmodifiableView();
          bitField0_ = (bitField0_ & ~0x00000002);
        }
        result.additionalContext_ = additionalContext_;
        result.format_ = format_;
        result.modelHash_ = modelHash_;
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof opennlp.OpenNLPService.TagWithContextRequest) {
          return mergeFrom((opennlp.OpenNLPService.TagWithContextRequest)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(opennlp.OpenNLPService.TagWithContextRequest other) {
        if (other == opennlp.OpenNLPService.TagWithContextRequest.getDefaultInstance()) return this;
        if (!other.sentence_.isEmpty()) {
          if (sentence_.isEmpty()) {
            sentence_ = other.sentence_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureSentenceIsMutable();
            sentence_.addAll(other.sentence_);
          }
          onChanged();
        }
        if (!other.additionalContext_.isEmpty()) {
          if (additionalContext_.isEmpty()) {
            additionalContext_ = other.additionalContext_;
            bitField0_ = (bitField0_ & ~0x00000002);
          } else {
            ensureAdditionalContextIsMutable();
            additionalContext_.addAll(other.additionalContext_);
          }
          onChanged();
        }
        if (other.format_ != 0) {
          setFormatValue(other.getFormatValue());
        }
        if (!other.getModelHash().isEmpty()) {
          modelHash_ = other.modelHash_;
          onChanged();
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        opennlp.OpenNLPService.TagWithContextRequest parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (opennlp.OpenNLPService.TagWithContextRequest) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private com.google.protobuf.LazyStringList sentence_ = com.google.protobuf.LazyStringArrayList.EMPTY;
      private void ensureSentenceIsMutable() {
        if (!((bitField0_ & 0x00000001) != 0)) {
          sentence_ = new com.google.protobuf.LazyStringArrayList(sentence_);
          bitField0_ |= 0x00000001;
         }
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @return A list containing the sentence.
       */
      public com.google.protobuf.ProtocolStringList
          getSentenceList() {
        return sentence_.getUnmodifiableView();
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @return The count of sentence.
       */
      public int getSentenceCount() {
        return sentence_.size();
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param index The index of the element to return.
       * @return The sentence at the given index.
       */
      public java.lang.String getSentence(int index) {
        return sentence_.get(index);
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param index The index of the value to return.
       * @return The bytes of the sentence at the given index.
       */
      public com.google.protobuf.ByteString
          getSentenceBytes(int index) {
        return sentence_.getByteString(index);
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param index The index to set the value at.
       * @param value The sentence to set.
       * @return This builder for chaining.
       */
      public Builder setSentence(
          int index, java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureSentenceIsMutable();
        sentence_.set(index, value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param value The sentence to add.
       * @return This builder for chaining.
       */
      public Builder addSentence(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureSentenceIsMutable();
        sentence_.add(value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param values The sentence to add.
       * @return This builder for chaining.
       */
      public Builder addAllSentence(
          java.lang.Iterable<java.lang.String> values) {
        ensureSentenceIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, sentence_);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearSentence() {
        sentence_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string sentence = 1;</code>
       * @param value The bytes of the sentence to add.
       * @return This builder for chaining.
       */
      public Builder addSentenceBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        ensureSentenceIsMutable();
        sentence_.add(value);
        onChanged();
        return this;
      }

      private com.google.protobuf.LazyStringList additionalContext_ = com.google.protobuf.LazyStringArrayList.EMPTY;
      private void ensureAdditionalContextIsMutable() {
        if (!((bitField0_ & 0x00000002) != 0)) {
          additionalContext_ = new com.google.protobuf.LazyStringArrayList(additionalContext_);
          bitField0_ |= 0x00000002;
         }
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @return A list containing the additionalContext.
       */
      public com.google.protobuf.ProtocolStringList
          getAdditionalContextList() {
        return additionalContext_.getUnmodifiableView();
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @return The count of additionalContext.
       */
      public int getAdditionalContextCount() {
        return additionalContext_.size();
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @param index The index of the element to return.
       * @return The additionalContext at the given index.
       */
      public java.lang.String getAdditionalContext(int index) {
        return additionalContext_.get(index);
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @param index The index of the value to return.
       * @return The bytes of the additionalContext at the given index.
       */
      public com.google.protobuf.ByteString
          getAdditionalContextBytes(int index) {
        return additionalContext_.getByteString(index);
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @param index The index to set the value at.
       * @param value The additionalContext to set.
       * @return This builder for chaining.
       */
      public Builder setAdditionalContext(
          int index, java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureAdditionalContextIsMutable();
        additionalContext_.set(index, value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @param value The additionalContext to add.
       * @return This builder for chaining.
       */
      public Builder addAdditionalContext(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureAdditionalContextIsMutable();
        additionalContext_.add(value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @param values The additionalContext to add.
       * @return This builder for chaining.
       */
      public Builder addAllAdditionalContext(
          java.lang.Iterable<java.lang.String> values) {
        ensureAdditionalContextIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, additionalContext_);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @return This builder for chaining.
       */
      public Builder clearAdditionalContext() {
        additionalContext_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000002);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string additional_context = 2;</code>
       * @param value The bytes of the additionalContext to add.
       * @return This builder for chaining.
       */
      public Builder addAdditionalContextBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        ensureAdditionalContextIsMutable();
        additionalContext_.add(value);
        onChanged();
        return this;
      }

      private int format_ = 0;
      /**
       * <code>.opennlp.POSTagFormat format = 3;</code>
       * @return The enum numeric value on the wire for format.
       */
      @java.lang.Override public int getFormatValue() {
        return format_;
      }
      /**
       * <code>.opennlp.POSTagFormat format = 3;</code>
       * @param value The enum numeric value on the wire for format to set.
       * @return This builder for chaining.
       */
      public Builder setFormatValue(int value) {
        
        format_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>.opennlp.POSTagFormat format = 3;</code>
       * @return The format.
       */
      @java.lang.Override
      public opennlp.OpenNLPService.POSTagFormat getFormat() {
        @SuppressWarnings("deprecation")
        opennlp.OpenNLPService.POSTagFormat result = opennlp.OpenNLPService.POSTagFormat.valueOf(format_);
        return result == null ? opennlp.OpenNLPService.POSTagFormat.UNRECOGNIZED : result;
      }
      /**
       * <code>.opennlp.POSTagFormat format = 3;</code>
       * @param value The format to set.
       * @return This builder for chaining.
       */
      public Builder setFormat(opennlp.OpenNLPService.POSTagFormat value) {
        if (value == null) {
          throw new NullPointerException();
        }
        
        format_ = value.getNumber();
        onChanged();
        return this;
      }
      /**
       * <code>.opennlp.POSTagFormat format = 3;</code>
       * @return This builder for chaining.
       */
      public Builder clearFormat() {
        
        format_ = 0;
        onChanged();
        return this;
      }

      private java.lang.Object modelHash_ = "";
      /**
       * <code>string model_hash = 4;</code>
       * @return The modelHash.
       */
      public java.lang.String getModelHash() {
        java.lang.Object ref = modelHash_;
        if (!(ref instanceof java.lang.String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          java.lang.String s = bs.toStringUtf8();
          modelHash_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>string model_hash = 4;</code>
       * @return The bytes for modelHash.
       */
      public com.google.protobuf.ByteString
          getModelHashBytes() {
        java.lang.Object ref = modelHash_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          modelHash_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>string model_hash = 4;</code>
       * @param value The modelHash to set.
       * @return This builder for chaining.
       */
      public Builder setModelHash(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        modelHash_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>string model_hash = 4;</code>
       * @return This builder for chaining.
       */
      public Builder clearModelHash() {
        
        modelHash_ = getDefaultInstance().getModelHash();
        onChanged();
        return this;
      }
      /**
       * <code>string model_hash = 4;</code>
       * @param value The bytes for modelHash to set.
       * @return This builder for chaining.
       */
      public Builder setModelHashBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        modelHash_ = value;
        onChanged();
        return this;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:opennlp.TagWithContextRequest)
    }

    // @@protoc_insertion_point(class_scope:opennlp.TagWithContextRequest)
    private static final opennlp.OpenNLPService.TagWithContextRequest DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new opennlp.OpenNLPService.TagWithContextRequest();
    }

    public static opennlp.OpenNLPService.TagWithContextRequest getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<TagWithContextRequest>
        PARSER = new com.google.protobuf.AbstractParser<TagWithContextRequest>() {
      @java.lang.Override
      public TagWithContextRequest parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new TagWithContextRequest(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<TagWithContextRequest> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<TagWithContextRequest> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public opennlp.OpenNLPService.TagWithContextRequest getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  public interface StringListOrBuilder extends
      // @@protoc_insertion_point(interface_extends:opennlp.StringList)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>repeated string values = 1;</code>
     * @return A list containing the values.
     */
    java.util.List<java.lang.String>
        getValuesList();
    /**
     * <code>repeated string values = 1;</code>
     * @return The count of values.
     */
    int getValuesCount();
    /**
     * <code>repeated string values = 1;</code>
     * @param index The index of the element to return.
     * @return The values at the given index.
     */
    java.lang.String getValues(int index);
    /**
     * <code>repeated string values = 1;</code>
     * @param index The index of the value to return.
     * @return The bytes of the values at the given index.
     */
    com.google.protobuf.ByteString
        getValuesBytes(int index);
  }
  /**
   * Protobuf type {@code opennlp.StringList}
   */
  public static final class StringList extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:opennlp.StringList)
      StringListOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use StringList.newBuilder() to construct.
    private StringList(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private StringList() {
      values_ = com.google.protobuf.LazyStringArrayList.EMPTY;
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new StringList();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private StringList(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {
              java.lang.String s = input.readStringRequireUtf8();
              if (!((mutable_bitField0_ & 0x00000001) != 0)) {
                values_ = new com.google.protobuf.LazyStringArrayList();
                mutable_bitField0_ |= 0x00000001;
              }
              values_.add(s);
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000001) != 0)) {
          values_ = values_.getUnmodifiableView();
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return opennlp.OpenNLPService.internal_static_opennlp_StringList_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return opennlp.OpenNLPService.internal_static_opennlp_StringList_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              opennlp.OpenNLPService.StringList.class, opennlp.OpenNLPService.StringList.Builder.class);
    }

    public static final int VALUES_FIELD_NUMBER = 1;
    private com.google.protobuf.LazyStringList values_;
    /**
     * <code>repeated string values = 1;</code>
     * @return A list containing the values.
     */
    public com.google.protobuf.ProtocolStringList
        getValuesList() {
      return values_;
    }
    /**
     * <code>repeated string values = 1;</code>
     * @return The count of values.
     */
    public int getValuesCount() {
      return values_.size();
    }
    /**
     * <code>repeated string values = 1;</code>
     * @param index The index of the element to return.
     * @return The values at the given index.
     */
    public java.lang.String getValues(int index) {
      return values_.get(index);
    }
    /**
     * <code>repeated string values = 1;</code>
     * @param index The index of the value to return.
     * @return The bytes of the values at the given index.
     */
    public com.google.protobuf.ByteString
        getValuesBytes(int index) {
      return values_.getByteString(index);
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      for (int i = 0; i < values_.size(); i++) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 1, values_.getRaw(i));
      }
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      {
        int dataSize = 0;
        for (int i = 0; i < values_.size(); i++) {
          dataSize += computeStringSizeNoTag(values_.getRaw(i));
        }
        size += dataSize;
        size += 1 * getValuesList().size();
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof opennlp.OpenNLPService.StringList)) {
        return super.equals(obj);
      }
      opennlp.OpenNLPService.StringList other = (opennlp.OpenNLPService.StringList) obj;

      if (!getValuesList()
          .equals(other.getValuesList())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      if (getValuesCount() > 0) {
        hash = (37 * hash) + VALUES_FIELD_NUMBER;
        hash = (53 * hash) + getValuesList().hashCode();
      }
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static opennlp.OpenNLPService.StringList parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.StringList parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.StringList parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.StringList parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(opennlp.OpenNLPService.StringList prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code opennlp.StringList}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:opennlp.StringList)
        opennlp.OpenNLPService.StringListOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return opennlp.OpenNLPService.internal_static_opennlp_StringList_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return opennlp.OpenNLPService.internal_static_opennlp_StringList_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                opennlp.OpenNLPService.StringList.class, opennlp.OpenNLPService.StringList.Builder.class);
      }

      // Construct using opennlp.OpenNLPService.StringList.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        values_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return opennlp.OpenNLPService.internal_static_opennlp_StringList_descriptor;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.StringList getDefaultInstanceForType() {
        return opennlp.OpenNLPService.StringList.getDefaultInstance();
      }

      @java.lang.Override
      public opennlp.OpenNLPService.StringList build() {
        opennlp.OpenNLPService.StringList result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.StringList buildPartial() {
        opennlp.OpenNLPService.StringList result = new opennlp.OpenNLPService.StringList(this);
        int from_bitField0_ = bitField0_;
        if (((bitField0_ & 0x00000001) != 0)) {
          values_ = values_.getUnmodifiableView();
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.values_ = values_;
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof opennlp.OpenNLPService.StringList) {
          return mergeFrom((opennlp.OpenNLPService.StringList)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(opennlp.OpenNLPService.StringList other) {
        if (other == opennlp.OpenNLPService.StringList.getDefaultInstance()) return this;
        if (!other.values_.isEmpty()) {
          if (values_.isEmpty()) {
            values_ = other.values_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureValuesIsMutable();
            values_.addAll(other.values_);
          }
          onChanged();
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        opennlp.OpenNLPService.StringList parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (opennlp.OpenNLPService.StringList) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private com.google.protobuf.LazyStringList values_ = com.google.protobuf.LazyStringArrayList.EMPTY;
      private void ensureValuesIsMutable() {
        if (!((bitField0_ & 0x00000001) != 0)) {
          values_ = new com.google.protobuf.LazyStringArrayList(values_);
          bitField0_ |= 0x00000001;
         }
      }
      /**
       * <code>repeated string values = 1;</code>
       * @return A list containing the values.
       */
      public com.google.protobuf.ProtocolStringList
          getValuesList() {
        return values_.getUnmodifiableView();
      }
      /**
       * <code>repeated string values = 1;</code>
       * @return The count of values.
       */
      public int getValuesCount() {
        return values_.size();
      }
      /**
       * <code>repeated string values = 1;</code>
       * @param index The index of the element to return.
       * @return The values at the given index.
       */
      public java.lang.String getValues(int index) {
        return values_.get(index);
      }
      /**
       * <code>repeated string values = 1;</code>
       * @param index The index of the value to return.
       * @return The bytes of the values at the given index.
       */
      public com.google.protobuf.ByteString
          getValuesBytes(int index) {
        return values_.getByteString(index);
      }
      /**
       * <code>repeated string values = 1;</code>
       * @param index The index to set the value at.
       * @param value The values to set.
       * @return This builder for chaining.
       */
      public Builder setValues(
          int index, java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureValuesIsMutable();
        values_.set(index, value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string values = 1;</code>
       * @param value The values to add.
       * @return This builder for chaining.
       */
      public Builder addValues(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureValuesIsMutable();
        values_.add(value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string values = 1;</code>
       * @param values The values to add.
       * @return This builder for chaining.
       */
      public Builder addAllValues(
          java.lang.Iterable<java.lang.String> values) {
        ensureValuesIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, values_);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string values = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearValues() {
        values_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string values = 1;</code>
       * @param value The bytes of the values to add.
       * @return This builder for chaining.
       */
      public Builder addValuesBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        ensureValuesIsMutable();
        values_.add(value);
        onChanged();
        return this;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:opennlp.StringList)
    }

    // @@protoc_insertion_point(class_scope:opennlp.StringList)
    private static final opennlp.OpenNLPService.StringList DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new opennlp.OpenNLPService.StringList();
    }

    public static opennlp.OpenNLPService.StringList getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<StringList>
        PARSER = new com.google.protobuf.AbstractParser<StringList>() {
      @java.lang.Override
      public StringList parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new StringList(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<StringList> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<StringList> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public opennlp.OpenNLPService.StringList getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  public interface SpanListOrBuilder extends
      // @@protoc_insertion_point(interface_extends:opennlp.SpanList)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    java.util.List<opennlp.OpenNLPService.Span> 
        getValuesList();
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    opennlp.OpenNLPService.Span getValues(int index);
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    int getValuesCount();
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    java.util.List<? extends opennlp.OpenNLPService.SpanOrBuilder> 
        getValuesOrBuilderList();
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    opennlp.OpenNLPService.SpanOrBuilder getValuesOrBuilder(
        int index);
  }
  /**
   * Protobuf type {@code opennlp.SpanList}
   */
  public static final class SpanList extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:opennlp.SpanList)
      SpanListOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use SpanList.newBuilder() to construct.
    private SpanList(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private SpanList() {
      values_ = java.util.Collections.emptyList();
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new SpanList();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private SpanList(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {
              if (!((mutable_bitField0_ & 0x00000001) != 0)) {
                values_ = new java.util.ArrayList<opennlp.OpenNLPService.Span>();
                mutable_bitField0_ |= 0x00000001;
              }
              values_.add(
                  input.readMessage(opennlp.OpenNLPService.Span.parser(), extensionRegistry));
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000001) != 0)) {
          values_ = java.util.Collections.unmodifiableList(values_);
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return opennlp.OpenNLPService.internal_static_opennlp_SpanList_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return opennlp.OpenNLPService.internal_static_opennlp_SpanList_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              opennlp.OpenNLPService.SpanList.class, opennlp.OpenNLPService.SpanList.Builder.class);
    }

    public static final int VALUES_FIELD_NUMBER = 1;
    private java.util.List<opennlp.OpenNLPService.Span> values_;
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    @java.lang.Override
    public java.util.List<opennlp.OpenNLPService.Span> getValuesList() {
      return values_;
    }
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    @java.lang.Override
    public java.util.List<? extends opennlp.OpenNLPService.SpanOrBuilder> 
        getValuesOrBuilderList() {
      return values_;
    }
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    @java.lang.Override
    public int getValuesCount() {
      return values_.size();
    }
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    @java.lang.Override
    public opennlp.OpenNLPService.Span getValues(int index) {
      return values_.get(index);
    }
    /**
     * <code>repeated .opennlp.Span values = 1;</code>
     */
    @java.lang.Override
    public opennlp.OpenNLPService.SpanOrBuilder getValuesOrBuilder(
        int index) {
      return values_.get(index);
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      for (int i = 0; i < values_.size(); i++) {
        output.writeMessage(1, values_.get(i));
      }
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      for (int i = 0; i < values_.size(); i++) {
        size += com.google.protobuf.CodedOutputStream
          .computeMessageSize(1, values_.get(i));
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof opennlp.OpenNLPService.SpanList)) {
        return super.equals(obj);
      }
      opennlp.OpenNLPService.SpanList other = (opennlp.OpenNLPService.SpanList) obj;

      if (!getValuesList()
          .equals(other.getValuesList())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      if (getValuesCount() > 0) {
        hash = (37 * hash) + VALUES_FIELD_NUMBER;
        hash = (53 * hash) + getValuesList().hashCode();
      }
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static opennlp.OpenNLPService.SpanList parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.SpanList parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.SpanList parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.SpanList parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(opennlp.OpenNLPService.SpanList prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code opennlp.SpanList}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:opennlp.SpanList)
        opennlp.OpenNLPService.SpanListOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return opennlp.OpenNLPService.internal_static_opennlp_SpanList_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return opennlp.OpenNLPService.internal_static_opennlp_SpanList_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                opennlp.OpenNLPService.SpanList.class, opennlp.OpenNLPService.SpanList.Builder.class);
      }

      // Construct using opennlp.OpenNLPService.SpanList.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
          getValuesFieldBuilder();
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        if (valuesBuilder_ == null) {
          values_ = java.util.Collections.emptyList();
          bitField0_ = (bitField0_ & ~0x00000001);
        } else {
          valuesBuilder_.clear();
        }
        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return opennlp.OpenNLPService.internal_static_opennlp_SpanList_descriptor;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.SpanList getDefaultInstanceForType() {
        return opennlp.OpenNLPService.SpanList.getDefaultInstance();
      }

      @java.lang.Override
      public opennlp.OpenNLPService.SpanList build() {
        opennlp.OpenNLPService.SpanList result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.SpanList buildPartial() {
        opennlp.OpenNLPService.SpanList result = new opennlp.OpenNLPService.SpanList(this);
        int from_bitField0_ = bitField0_;
        if (valuesBuilder_ == null) {
          if (((bitField0_ & 0x00000001) != 0)) {
            values_ = java.util.Collections.unmodifiableList(values_);
            bitField0_ = (bitField0_ & ~0x00000001);
          }
          result.values_ = values_;
        } else {
          result.values_ = valuesBuilder_.build();
        }
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof opennlp.OpenNLPService.SpanList) {
          return mergeFrom((opennlp.OpenNLPService.SpanList)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(opennlp.OpenNLPService.SpanList other) {
        if (other == opennlp.OpenNLPService.SpanList.getDefaultInstance()) return this;
        if (valuesBuilder_ == null) {
          if (!other.values_.isEmpty()) {
            if (values_.isEmpty()) {
              values_ = other.values_;
              bitField0_ = (bitField0_ & ~0x00000001);
            } else {
              ensureValuesIsMutable();
              values_.addAll(other.values_);
            }
            onChanged();
          }
        } else {
          if (!other.values_.isEmpty()) {
            if (valuesBuilder_.isEmpty()) {
              valuesBuilder_.dispose();
              valuesBuilder_ = null;
              values_ = other.values_;
              bitField0_ = (bitField0_ & ~0x00000001);
              valuesBuilder_ = 
                com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders ?
                   getValuesFieldBuilder() : null;
            } else {
              valuesBuilder_.addAllMessages(other.values_);
            }
          }
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        opennlp.OpenNLPService.SpanList parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (opennlp.OpenNLPService.SpanList) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private java.util.List<opennlp.OpenNLPService.Span> values_ =
        java.util.Collections.emptyList();
      private void ensureValuesIsMutable() {
        if (!((bitField0_ & 0x00000001) != 0)) {
          values_ = new java.util.ArrayList<opennlp.OpenNLPService.Span>(values_);
          bitField0_ |= 0x00000001;
         }
      }

      private com.google.protobuf.RepeatedFieldBuilderV3<
          opennlp.OpenNLPService.Span, opennlp.OpenNLPService.Span.Builder, opennlp.OpenNLPService.SpanOrBuilder> valuesBuilder_;

      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public java.util.List<opennlp.OpenNLPService.Span> getValuesList() {
        if (valuesBuilder_ == null) {
          return java.util.Collections.unmodifiableList(values_);
        } else {
          return valuesBuilder_.getMessageList();
        }
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public int getValuesCount() {
        if (valuesBuilder_ == null) {
          return values_.size();
        } else {
          return valuesBuilder_.getCount();
        }
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public opennlp.OpenNLPService.Span getValues(int index) {
        if (valuesBuilder_ == null) {
          return values_.get(index);
        } else {
          return valuesBuilder_.getMessage(index);
        }
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder setValues(
          int index, opennlp.OpenNLPService.Span value) {
        if (valuesBuilder_ == null) {
          if (value == null) {
            throw new NullPointerException();
          }
          ensureValuesIsMutable();
          values_.set(index, value);
          onChanged();
        } else {
          valuesBuilder_.setMessage(index, value);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder setValues(
          int index, opennlp.OpenNLPService.Span.Builder builderForValue) {
        if (valuesBuilder_ == null) {
          ensureValuesIsMutable();
          values_.set(index, builderForValue.build());
          onChanged();
        } else {
          valuesBuilder_.setMessage(index, builderForValue.build());
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder addValues(opennlp.OpenNLPService.Span value) {
        if (valuesBuilder_ == null) {
          if (value == null) {
            throw new NullPointerException();
          }
          ensureValuesIsMutable();
          values_.add(value);
          onChanged();
        } else {
          valuesBuilder_.addMessage(value);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder addValues(
          int index, opennlp.OpenNLPService.Span value) {
        if (valuesBuilder_ == null) {
          if (value == null) {
            throw new NullPointerException();
          }
          ensureValuesIsMutable();
          values_.add(index, value);
          onChanged();
        } else {
          valuesBuilder_.addMessage(index, value);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder addValues(
          opennlp.OpenNLPService.Span.Builder builderForValue) {
        if (valuesBuilder_ == null) {
          ensureValuesIsMutable();
          values_.add(builderForValue.build());
          onChanged();
        } else {
          valuesBuilder_.addMessage(builderForValue.build());
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder addValues(
          int index, opennlp.OpenNLPService.Span.Builder builderForValue) {
        if (valuesBuilder_ == null) {
          ensureValuesIsMutable();
          values_.add(index, builderForValue.build());
          onChanged();
        } else {
          valuesBuilder_.addMessage(index, builderForValue.build());
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder addAllValues(
          java.lang.Iterable<? extends opennlp.OpenNLPService.Span> values) {
        if (valuesBuilder_ == null) {
          ensureValuesIsMutable();
          com.google.protobuf.AbstractMessageLite.Builder.addAll(
              values, values_);
          onChanged();
        } else {
          valuesBuilder_.addAllMessages(values);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder clearValues() {
        if (valuesBuilder_ == null) {
          values_ = java.util.Collections.emptyList();
          bitField0_ = (bitField0_ & ~0x00000001);
          onChanged();
        } else {
          valuesBuilder_.clear();
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public Builder removeValues(int index) {
        if (valuesBuilder_ == null) {
          ensureValuesIsMutable();
          values_.remove(index);
          onChanged();
        } else {
          valuesBuilder_.remove(index);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public opennlp.OpenNLPService.Span.Builder getValuesBuilder(
          int index) {
        return getValuesFieldBuilder().getBuilder(index);
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public opennlp.OpenNLPService.SpanOrBuilder getValuesOrBuilder(
          int index) {
        if (valuesBuilder_ == null) {
          return values_.get(index);  } else {
          return valuesBuilder_.getMessageOrBuilder(index);
        }
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public java.util.List<? extends opennlp.OpenNLPService.SpanOrBuilder> 
           getValuesOrBuilderList() {
        if (valuesBuilder_ != null) {
          return valuesBuilder_.getMessageOrBuilderList();
        } else {
          return java.util.Collections.unmodifiableList(values_);
        }
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public opennlp.OpenNLPService.Span.Builder addValuesBuilder() {
        return getValuesFieldBuilder().addBuilder(
            opennlp.OpenNLPService.Span.getDefaultInstance());
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public opennlp.OpenNLPService.Span.Builder addValuesBuilder(
          int index) {
        return getValuesFieldBuilder().addBuilder(
            index, opennlp.OpenNLPService.Span.getDefaultInstance());
      }
      /**
       * <code>repeated .opennlp.Span values = 1;</code>
       */
      public java.util.List<opennlp.OpenNLPService.Span.Builder> 
           getValuesBuilderList() {
        return getValuesFieldBuilder().getBuilderList();
      }
      private com.google.protobuf.RepeatedFieldBuilderV3<
          opennlp.OpenNLPService.Span, opennlp.OpenNLPService.Span.Builder, opennlp.OpenNLPService.SpanOrBuilder> 
          getValuesFieldBuilder() {
        if (valuesBuilder_ == null) {
          valuesBuilder_ = new com.google.protobuf.RepeatedFieldBuilderV3<
              opennlp.OpenNLPService.Span, opennlp.OpenNLPService.Span.Builder, opennlp.OpenNLPService.SpanOrBuilder>(
                  values_,
                  ((bitField0_ & 0x00000001) != 0),
                  getParentForChildren(),
                  isClean());
          values_ = null;
        }
        return valuesBuilder_;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:opennlp.SpanList)
    }

    // @@protoc_insertion_point(class_scope:opennlp.SpanList)
    private static final opennlp.OpenNLPService.SpanList DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new opennlp.OpenNLPService.SpanList();
    }

    public static opennlp.OpenNLPService.SpanList getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<SpanList>
        PARSER = new com.google.protobuf.AbstractParser<SpanList>() {
      @java.lang.Override
      public SpanList parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new SpanList(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<SpanList> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<SpanList> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public opennlp.OpenNLPService.SpanList getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  public interface SpanOrBuilder extends
      // @@protoc_insertion_point(interface_extends:opennlp.Span)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>int32 start = 1;</code>
     * @return The start.
     */
    int getStart();

    /**
     * <code>int32 end = 2;</code>
     * @return The end.
     */
    int getEnd();

    /**
     * <code>double prob = 3;</code>
     * @return The prob.
     */
    double getProb();

    /**
     * <code>string type = 4;</code>
     * @return The type.
     */
    java.lang.String getType();
    /**
     * <code>string type = 4;</code>
     * @return The bytes for type.
     */
    com.google.protobuf.ByteString
        getTypeBytes();
  }
  /**
   * Protobuf type {@code opennlp.Span}
   */
  public static final class Span extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:opennlp.Span)
      SpanOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use Span.newBuilder() to construct.
    private Span(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private Span() {
      type_ = "";
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new Span();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private Span(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 8: {

              start_ = input.readInt32();
              break;
            }
            case 16: {

              end_ = input.readInt32();
              break;
            }
            case 25: {

              prob_ = input.readDouble();
              break;
            }
            case 34: {
              java.lang.String s = input.readStringRequireUtf8();

              type_ = s;
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return opennlp.OpenNLPService.internal_static_opennlp_Span_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return opennlp.OpenNLPService.internal_static_opennlp_Span_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              opennlp.OpenNLPService.Span.class, opennlp.OpenNLPService.Span.Builder.class);
    }

    public static final int START_FIELD_NUMBER = 1;
    private int start_;
    /**
     * <code>int32 start = 1;</code>
     * @return The start.
     */
    @java.lang.Override
    public int getStart() {
      return start_;
    }

    public static final int END_FIELD_NUMBER = 2;
    private int end_;
    /**
     * <code>int32 end = 2;</code>
     * @return The end.
     */
    @java.lang.Override
    public int getEnd() {
      return end_;
    }

    public static final int PROB_FIELD_NUMBER = 3;
    private double prob_;
    /**
     * <code>double prob = 3;</code>
     * @return The prob.
     */
    @java.lang.Override
    public double getProb() {
      return prob_;
    }

    public static final int TYPE_FIELD_NUMBER = 4;
    private volatile java.lang.Object type_;
    /**
     * <code>string type = 4;</code>
     * @return The type.
     */
    @java.lang.Override
    public java.lang.String getType() {
      java.lang.Object ref = type_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        type_ = s;
        return s;
      }
    }
    /**
     * <code>string type = 4;</code>
     * @return The bytes for type.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getTypeBytes() {
      java.lang.Object ref = type_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        type_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      if (start_ != 0) {
        output.writeInt32(1, start_);
      }
      if (end_ != 0) {
        output.writeInt32(2, end_);
      }
      if (java.lang.Double.doubleToRawLongBits(prob_) != 0) {
        output.writeDouble(3, prob_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(type_)) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 4, type_);
      }
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      if (start_ != 0) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt32Size(1, start_);
      }
      if (end_ != 0) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt32Size(2, end_);
      }
      if (java.lang.Double.doubleToRawLongBits(prob_) != 0) {
        size += com.google.protobuf.CodedOutputStream
          .computeDoubleSize(3, prob_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(type_)) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(4, type_);
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof opennlp.OpenNLPService.Span)) {
        return super.equals(obj);
      }
      opennlp.OpenNLPService.Span other = (opennlp.OpenNLPService.Span) obj;

      if (getStart()
          != other.getStart()) return false;
      if (getEnd()
          != other.getEnd()) return false;
      if (java.lang.Double.doubleToLongBits(getProb())
          != java.lang.Double.doubleToLongBits(
              other.getProb())) return false;
      if (!getType()
          .equals(other.getType())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      hash = (37 * hash) + START_FIELD_NUMBER;
      hash = (53 * hash) + getStart();
      hash = (37 * hash) + END_FIELD_NUMBER;
      hash = (53 * hash) + getEnd();
      hash = (37 * hash) + PROB_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          java.lang.Double.doubleToLongBits(getProb()));
      hash = (37 * hash) + TYPE_FIELD_NUMBER;
      hash = (53 * hash) + getType().hashCode();
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static opennlp.OpenNLPService.Span parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Span parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Span parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Span parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Span parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Span parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Span parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Span parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Span parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Span parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Span parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Span parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(opennlp.OpenNLPService.Span prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code opennlp.Span}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:opennlp.Span)
        opennlp.OpenNLPService.SpanOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return opennlp.OpenNLPService.internal_static_opennlp_Span_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return opennlp.OpenNLPService.internal_static_opennlp_Span_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                opennlp.OpenNLPService.Span.class, opennlp.OpenNLPService.Span.Builder.class);
      }

      // Construct using opennlp.OpenNLPService.Span.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        start_ = 0;

        end_ = 0;

        prob_ = 0D;

        type_ = "";

        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return opennlp.OpenNLPService.internal_static_opennlp_Span_descriptor;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Span getDefaultInstanceForType() {
        return opennlp.OpenNLPService.Span.getDefaultInstance();
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Span build() {
        opennlp.OpenNLPService.Span result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Span buildPartial() {
        opennlp.OpenNLPService.Span result = new opennlp.OpenNLPService.Span(this);
        result.start_ = start_;
        result.end_ = end_;
        result.prob_ = prob_;
        result.type_ = type_;
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof opennlp.OpenNLPService.Span) {
          return mergeFrom((opennlp.OpenNLPService.Span)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(opennlp.OpenNLPService.Span other) {
        if (other == opennlp.OpenNLPService.Span.getDefaultInstance()) return this;
        if (other.getStart() != 0) {
          setStart(other.getStart());
        }
        if (other.getEnd() != 0) {
          setEnd(other.getEnd());
        }
        if (other.getProb() != 0D) {
          setProb(other.getProb());
        }
        if (!other.getType().isEmpty()) {
          type_ = other.type_;
          onChanged();
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        opennlp.OpenNLPService.Span parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (opennlp.OpenNLPService.Span) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }

      private int start_ ;
      /**
       * <code>int32 start = 1;</code>
       * @return The start.
       */
      @java.lang.Override
      public int getStart() {
        return start_;
      }
      /**
       * <code>int32 start = 1;</code>
       * @param value The start to set.
       * @return This builder for chaining.
       */
      public Builder setStart(int value) {
        
        start_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>int32 start = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearStart() {
        
        start_ = 0;
        onChanged();
        return this;
      }

      private int end_ ;
      /**
       * <code>int32 end = 2;</code>
       * @return The end.
       */
      @java.lang.Override
      public int getEnd() {
        return end_;
      }
      /**
       * <code>int32 end = 2;</code>
       * @param value The end to set.
       * @return This builder for chaining.
       */
      public Builder setEnd(int value) {
        
        end_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>int32 end = 2;</code>
       * @return This builder for chaining.
       */
      public Builder clearEnd() {
        
        end_ = 0;
        onChanged();
        return this;
      }

      private double prob_ ;
      /**
       * <code>double prob = 3;</code>
       * @return The prob.
       */
      @java.lang.Override
      public double getProb() {
        return prob_;
      }
      /**
       * <code>double prob = 3;</code>
       * @param value The prob to set.
       * @return This builder for chaining.
       */
      public Builder setProb(double value) {
        
        prob_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>double prob = 3;</code>
       * @return This builder for chaining.
       */
      public Builder clearProb() {
        
        prob_ = 0D;
        onChanged();
        return this;
      }

      private java.lang.Object type_ = "";
      /**
       * <code>string type = 4;</code>
       * @return The type.
       */
      public java.lang.String getType() {
        java.lang.Object ref = type_;
        if (!(ref instanceof java.lang.String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          java.lang.String s = bs.toStringUtf8();
          type_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>string type = 4;</code>
       * @return The bytes for type.
       */
      public com.google.protobuf.ByteString
          getTypeBytes() {
        java.lang.Object ref = type_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          type_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>string type = 4;</code>
       * @param value The type to set.
       * @return This builder for chaining.
       */
      public Builder setType(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        type_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>string type = 4;</code>
       * @return This builder for chaining.
       */
      public Builder clearType() {
        
        type_ = getDefaultInstance().getType();
        onChanged();
        return this;
      }
      /**
       * <code>string type = 4;</code>
       * @param value The bytes for type to set.
       * @return This builder for chaining.
       */
      public Builder setTypeBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        type_ = value;
        onChanged();
        return this;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:opennlp.Span)
    }

    // @@protoc_insertion_point(class_scope:opennlp.Span)
    private static final opennlp.OpenNLPService.Span DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new opennlp.OpenNLPService.Span();
    }

    public static opennlp.OpenNLPService.Span getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<Span>
        PARSER = new com.google.protobuf.AbstractParser<Span>() {
      @java.lang.Override
      public Span parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new Span(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<Span> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<Span> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public opennlp.OpenNLPService.Span getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  public interface AvailableModelsOrBuilder extends
      // @@protoc_insertion_point(interface_extends:opennlp.AvailableModels)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    java.util.List<opennlp.OpenNLPService.Model> 
        getModelsList();
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    opennlp.OpenNLPService.Model getModels(int index);
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    int getModelsCount();
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    java.util.List<? extends opennlp.OpenNLPService.ModelOrBuilder> 
        getModelsOrBuilderList();
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    opennlp.OpenNLPService.ModelOrBuilder getModelsOrBuilder(
        int index);
  }
  /**
   * Protobuf type {@code opennlp.AvailableModels}
   */
  public static final class AvailableModels extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:opennlp.AvailableModels)
      AvailableModelsOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use AvailableModels.newBuilder() to construct.
    private AvailableModels(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private AvailableModels() {
      models_ = java.util.Collections.emptyList();
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new AvailableModels();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private AvailableModels(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {
              if (!((mutable_bitField0_ & 0x00000001) != 0)) {
                models_ = new java.util.ArrayList<opennlp.OpenNLPService.Model>();
                mutable_bitField0_ |= 0x00000001;
              }
              models_.add(
                  input.readMessage(opennlp.OpenNLPService.Model.parser(), extensionRegistry));
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000001) != 0)) {
          models_ = java.util.Collections.unmodifiableList(models_);
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return opennlp.OpenNLPService.internal_static_opennlp_AvailableModels_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return opennlp.OpenNLPService.internal_static_opennlp_AvailableModels_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              opennlp.OpenNLPService.AvailableModels.class, opennlp.OpenNLPService.AvailableModels.Builder.class);
    }

    public static final int MODELS_FIELD_NUMBER = 1;
    private java.util.List<opennlp.OpenNLPService.Model> models_;
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    @java.lang.Override
    public java.util.List<opennlp.OpenNLPService.Model> getModelsList() {
      return models_;
    }
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    @java.lang.Override
    public java.util.List<? extends opennlp.OpenNLPService.ModelOrBuilder> 
        getModelsOrBuilderList() {
      return models_;
    }
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    @java.lang.Override
    public int getModelsCount() {
      return models_.size();
    }
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    @java.lang.Override
    public opennlp.OpenNLPService.Model getModels(int index) {
      return models_.get(index);
    }
    /**
     * <code>repeated .opennlp.Model models = 1;</code>
     */
    @java.lang.Override
    public opennlp.OpenNLPService.ModelOrBuilder getModelsOrBuilder(
        int index) {
      return models_.get(index);
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      for (int i = 0; i < models_.size(); i++) {
        output.writeMessage(1, models_.get(i));
      }
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      for (int i = 0; i < models_.size(); i++) {
        size += com.google.protobuf.CodedOutputStream
          .computeMessageSize(1, models_.get(i));
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof opennlp.OpenNLPService.AvailableModels)) {
        return super.equals(obj);
      }
      opennlp.OpenNLPService.AvailableModels other = (opennlp.OpenNLPService.AvailableModels) obj;

      if (!getModelsList()
          .equals(other.getModelsList())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      if (getModelsCount() > 0) {
        hash = (37 * hash) + MODELS_FIELD_NUMBER;
        hash = (53 * hash) + getModelsList().hashCode();
      }
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static opennlp.OpenNLPService.AvailableModels parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.AvailableModels parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.AvailableModels parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.AvailableModels parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(opennlp.OpenNLPService.AvailableModels prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code opennlp.AvailableModels}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:opennlp.AvailableModels)
        opennlp.OpenNLPService.AvailableModelsOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return opennlp.OpenNLPService.internal_static_opennlp_AvailableModels_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return opennlp.OpenNLPService.internal_static_opennlp_AvailableModels_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                opennlp.OpenNLPService.AvailableModels.class, opennlp.OpenNLPService.AvailableModels.Builder.class);
      }

      // Construct using opennlp.OpenNLPService.AvailableModels.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
          getModelsFieldBuilder();
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        if (modelsBuilder_ == null) {
          models_ = java.util.Collections.emptyList();
          bitField0_ = (bitField0_ & ~0x00000001);
        } else {
          modelsBuilder_.clear();
        }
        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return opennlp.OpenNLPService.internal_static_opennlp_AvailableModels_descriptor;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.AvailableModels getDefaultInstanceForType() {
        return opennlp.OpenNLPService.AvailableModels.getDefaultInstance();
      }

      @java.lang.Override
      public opennlp.OpenNLPService.AvailableModels build() {
        opennlp.OpenNLPService.AvailableModels result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.AvailableModels buildPartial() {
        opennlp.OpenNLPService.AvailableModels result = new opennlp.OpenNLPService.AvailableModels(this);
        int from_bitField0_ = bitField0_;
        if (modelsBuilder_ == null) {
          if (((bitField0_ & 0x00000001) != 0)) {
            models_ = java.util.Collections.unmodifiableList(models_);
            bitField0_ = (bitField0_ & ~0x00000001);
          }
          result.models_ = models_;
        } else {
          result.models_ = modelsBuilder_.build();
        }
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof opennlp.OpenNLPService.AvailableModels) {
          return mergeFrom((opennlp.OpenNLPService.AvailableModels)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(opennlp.OpenNLPService.AvailableModels other) {
        if (other == opennlp.OpenNLPService.AvailableModels.getDefaultInstance()) return this;
        if (modelsBuilder_ == null) {
          if (!other.models_.isEmpty()) {
            if (models_.isEmpty()) {
              models_ = other.models_;
              bitField0_ = (bitField0_ & ~0x00000001);
            } else {
              ensureModelsIsMutable();
              models_.addAll(other.models_);
            }
            onChanged();
          }
        } else {
          if (!other.models_.isEmpty()) {
            if (modelsBuilder_.isEmpty()) {
              modelsBuilder_.dispose();
              modelsBuilder_ = null;
              models_ = other.models_;
              bitField0_ = (bitField0_ & ~0x00000001);
              modelsBuilder_ = 
                com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders ?
                   getModelsFieldBuilder() : null;
            } else {
              modelsBuilder_.addAllMessages(other.models_);
            }
          }
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        opennlp.OpenNLPService.AvailableModels parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (opennlp.OpenNLPService.AvailableModels) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private java.util.List<opennlp.OpenNLPService.Model> models_ =
        java.util.Collections.emptyList();
      private void ensureModelsIsMutable() {
        if (!((bitField0_ & 0x00000001) != 0)) {
          models_ = new java.util.ArrayList<opennlp.OpenNLPService.Model>(models_);
          bitField0_ |= 0x00000001;
         }
      }

      private com.google.protobuf.RepeatedFieldBuilderV3<
          opennlp.OpenNLPService.Model, opennlp.OpenNLPService.Model.Builder, opennlp.OpenNLPService.ModelOrBuilder> modelsBuilder_;

      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public java.util.List<opennlp.OpenNLPService.Model> getModelsList() {
        if (modelsBuilder_ == null) {
          return java.util.Collections.unmodifiableList(models_);
        } else {
          return modelsBuilder_.getMessageList();
        }
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public int getModelsCount() {
        if (modelsBuilder_ == null) {
          return models_.size();
        } else {
          return modelsBuilder_.getCount();
        }
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public opennlp.OpenNLPService.Model getModels(int index) {
        if (modelsBuilder_ == null) {
          return models_.get(index);
        } else {
          return modelsBuilder_.getMessage(index);
        }
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder setModels(
          int index, opennlp.OpenNLPService.Model value) {
        if (modelsBuilder_ == null) {
          if (value == null) {
            throw new NullPointerException();
          }
          ensureModelsIsMutable();
          models_.set(index, value);
          onChanged();
        } else {
          modelsBuilder_.setMessage(index, value);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder setModels(
          int index, opennlp.OpenNLPService.Model.Builder builderForValue) {
        if (modelsBuilder_ == null) {
          ensureModelsIsMutable();
          models_.set(index, builderForValue.build());
          onChanged();
        } else {
          modelsBuilder_.setMessage(index, builderForValue.build());
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder addModels(opennlp.OpenNLPService.Model value) {
        if (modelsBuilder_ == null) {
          if (value == null) {
            throw new NullPointerException();
          }
          ensureModelsIsMutable();
          models_.add(value);
          onChanged();
        } else {
          modelsBuilder_.addMessage(value);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder addModels(
          int index, opennlp.OpenNLPService.Model value) {
        if (modelsBuilder_ == null) {
          if (value == null) {
            throw new NullPointerException();
          }
          ensureModelsIsMutable();
          models_.add(index, value);
          onChanged();
        } else {
          modelsBuilder_.addMessage(index, value);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder addModels(
          opennlp.OpenNLPService.Model.Builder builderForValue) {
        if (modelsBuilder_ == null) {
          ensureModelsIsMutable();
          models_.add(builderForValue.build());
          onChanged();
        } else {
          modelsBuilder_.addMessage(builderForValue.build());
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder addModels(
          int index, opennlp.OpenNLPService.Model.Builder builderForValue) {
        if (modelsBuilder_ == null) {
          ensureModelsIsMutable();
          models_.add(index, builderForValue.build());
          onChanged();
        } else {
          modelsBuilder_.addMessage(index, builderForValue.build());
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder addAllModels(
          java.lang.Iterable<? extends opennlp.OpenNLPService.Model> values) {
        if (modelsBuilder_ == null) {
          ensureModelsIsMutable();
          com.google.protobuf.AbstractMessageLite.Builder.addAll(
              values, models_);
          onChanged();
        } else {
          modelsBuilder_.addAllMessages(values);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder clearModels() {
        if (modelsBuilder_ == null) {
          models_ = java.util.Collections.emptyList();
          bitField0_ = (bitField0_ & ~0x00000001);
          onChanged();
        } else {
          modelsBuilder_.clear();
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public Builder removeModels(int index) {
        if (modelsBuilder_ == null) {
          ensureModelsIsMutable();
          models_.remove(index);
          onChanged();
        } else {
          modelsBuilder_.remove(index);
        }
        return this;
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public opennlp.OpenNLPService.Model.Builder getModelsBuilder(
          int index) {
        return getModelsFieldBuilder().getBuilder(index);
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public opennlp.OpenNLPService.ModelOrBuilder getModelsOrBuilder(
          int index) {
        if (modelsBuilder_ == null) {
          return models_.get(index);  } else {
          return modelsBuilder_.getMessageOrBuilder(index);
        }
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public java.util.List<? extends opennlp.OpenNLPService.ModelOrBuilder> 
           getModelsOrBuilderList() {
        if (modelsBuilder_ != null) {
          return modelsBuilder_.getMessageOrBuilderList();
        } else {
          return java.util.Collections.unmodifiableList(models_);
        }
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public opennlp.OpenNLPService.Model.Builder addModelsBuilder() {
        return getModelsFieldBuilder().addBuilder(
            opennlp.OpenNLPService.Model.getDefaultInstance());
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public opennlp.OpenNLPService.Model.Builder addModelsBuilder(
          int index) {
        return getModelsFieldBuilder().addBuilder(
            index, opennlp.OpenNLPService.Model.getDefaultInstance());
      }
      /**
       * <code>repeated .opennlp.Model models = 1;</code>
       */
      public java.util.List<opennlp.OpenNLPService.Model.Builder> 
           getModelsBuilderList() {
        return getModelsFieldBuilder().getBuilderList();
      }
      private com.google.protobuf.RepeatedFieldBuilderV3<
          opennlp.OpenNLPService.Model, opennlp.OpenNLPService.Model.Builder, opennlp.OpenNLPService.ModelOrBuilder> 
          getModelsFieldBuilder() {
        if (modelsBuilder_ == null) {
          modelsBuilder_ = new com.google.protobuf.RepeatedFieldBuilderV3<
              opennlp.OpenNLPService.Model, opennlp.OpenNLPService.Model.Builder, opennlp.OpenNLPService.ModelOrBuilder>(
                  models_,
                  ((bitField0_ & 0x00000001) != 0),
                  getParentForChildren(),
                  isClean());
          models_ = null;
        }
        return modelsBuilder_;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:opennlp.AvailableModels)
    }

    // @@protoc_insertion_point(class_scope:opennlp.AvailableModels)
    private static final opennlp.OpenNLPService.AvailableModels DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new opennlp.OpenNLPService.AvailableModels();
    }

    public static opennlp.OpenNLPService.AvailableModels getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<AvailableModels>
        PARSER = new com.google.protobuf.AbstractParser<AvailableModels>() {
      @java.lang.Override
      public AvailableModels parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new AvailableModels(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<AvailableModels> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<AvailableModels> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public opennlp.OpenNLPService.AvailableModels getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  public interface ModelOrBuilder extends
      // @@protoc_insertion_point(interface_extends:opennlp.Model)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>string hash = 1;</code>
     * @return The hash.
     */
    java.lang.String getHash();
    /**
     * <code>string hash = 1;</code>
     * @return The bytes for hash.
     */
    com.google.protobuf.ByteString
        getHashBytes();

    /**
     * <code>string name = 2;</code>
     * @return The name.
     */
    java.lang.String getName();
    /**
     * <code>string name = 2;</code>
     * @return The bytes for name.
     */
    com.google.protobuf.ByteString
        getNameBytes();

    /**
     * <code>string locale = 3;</code>
     * @return The locale.
     */
    java.lang.String getLocale();
    /**
     * <code>string locale = 3;</code>
     * @return The bytes for locale.
     */
    com.google.protobuf.ByteString
        getLocaleBytes();
  }
  /**
   * Protobuf type {@code opennlp.Model}
   */
  public static final class Model extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:opennlp.Model)
      ModelOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use Model.newBuilder() to construct.
    private Model(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private Model() {
      hash_ = "";
      name_ = "";
      locale_ = "";
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new Model();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private Model(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {
              java.lang.String s = input.readStringRequireUtf8();

              hash_ = s;
              break;
            }
            case 18: {
              java.lang.String s = input.readStringRequireUtf8();

              name_ = s;
              break;
            }
            case 26: {
              java.lang.String s = input.readStringRequireUtf8();

              locale_ = s;
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return opennlp.OpenNLPService.internal_static_opennlp_Model_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return opennlp.OpenNLPService.internal_static_opennlp_Model_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              opennlp.OpenNLPService.Model.class, opennlp.OpenNLPService.Model.Builder.class);
    }

    public static final int HASH_FIELD_NUMBER = 1;
    private volatile java.lang.Object hash_;
    /**
     * <code>string hash = 1;</code>
     * @return The hash.
     */
    @java.lang.Override
    public java.lang.String getHash() {
      java.lang.Object ref = hash_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        hash_ = s;
        return s;
      }
    }
    /**
     * <code>string hash = 1;</code>
     * @return The bytes for hash.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getHashBytes() {
      java.lang.Object ref = hash_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        hash_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    public static final int NAME_FIELD_NUMBER = 2;
    private volatile java.lang.Object name_;
    /**
     * <code>string name = 2;</code>
     * @return The name.
     */
    @java.lang.Override
    public java.lang.String getName() {
      java.lang.Object ref = name_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        name_ = s;
        return s;
      }
    }
    /**
     * <code>string name = 2;</code>
     * @return The bytes for name.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getNameBytes() {
      java.lang.Object ref = name_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        name_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    public static final int LOCALE_FIELD_NUMBER = 3;
    private volatile java.lang.Object locale_;
    /**
     * <code>string locale = 3;</code>
     * @return The locale.
     */
    @java.lang.Override
    public java.lang.String getLocale() {
      java.lang.Object ref = locale_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        locale_ = s;
        return s;
      }
    }
    /**
     * <code>string locale = 3;</code>
     * @return The bytes for locale.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getLocaleBytes() {
      java.lang.Object ref = locale_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        locale_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(hash_)) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 1, hash_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(name_)) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 2, name_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(locale_)) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 3, locale_);
      }
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(hash_)) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, hash_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(name_)) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, name_);
      }
      if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(locale_)) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(3, locale_);
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof opennlp.OpenNLPService.Model)) {
        return super.equals(obj);
      }
      opennlp.OpenNLPService.Model other = (opennlp.OpenNLPService.Model) obj;

      if (!getHash()
          .equals(other.getHash())) return false;
      if (!getName()
          .equals(other.getName())) return false;
      if (!getLocale()
          .equals(other.getLocale())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      hash = (37 * hash) + HASH_FIELD_NUMBER;
      hash = (53 * hash) + getHash().hashCode();
      hash = (37 * hash) + NAME_FIELD_NUMBER;
      hash = (53 * hash) + getName().hashCode();
      hash = (37 * hash) + LOCALE_FIELD_NUMBER;
      hash = (53 * hash) + getLocale().hashCode();
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static opennlp.OpenNLPService.Model parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Model parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Model parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Model parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Model parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Model parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Model parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Model parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Model parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Model parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Model parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Model parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(opennlp.OpenNLPService.Model prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code opennlp.Model}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:opennlp.Model)
        opennlp.OpenNLPService.ModelOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return opennlp.OpenNLPService.internal_static_opennlp_Model_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return opennlp.OpenNLPService.internal_static_opennlp_Model_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                opennlp.OpenNLPService.Model.class, opennlp.OpenNLPService.Model.Builder.class);
      }

      // Construct using opennlp.OpenNLPService.Model.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        hash_ = "";

        name_ = "";

        locale_ = "";

        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return opennlp.OpenNLPService.internal_static_opennlp_Model_descriptor;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Model getDefaultInstanceForType() {
        return opennlp.OpenNLPService.Model.getDefaultInstance();
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Model build() {
        opennlp.OpenNLPService.Model result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Model buildPartial() {
        opennlp.OpenNLPService.Model result = new opennlp.OpenNLPService.Model(this);
        result.hash_ = hash_;
        result.name_ = name_;
        result.locale_ = locale_;
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof opennlp.OpenNLPService.Model) {
          return mergeFrom((opennlp.OpenNLPService.Model)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(opennlp.OpenNLPService.Model other) {
        if (other == opennlp.OpenNLPService.Model.getDefaultInstance()) return this;
        if (!other.getHash().isEmpty()) {
          hash_ = other.hash_;
          onChanged();
        }
        if (!other.getName().isEmpty()) {
          name_ = other.name_;
          onChanged();
        }
        if (!other.getLocale().isEmpty()) {
          locale_ = other.locale_;
          onChanged();
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        opennlp.OpenNLPService.Model parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (opennlp.OpenNLPService.Model) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }

      private java.lang.Object hash_ = "";
      /**
       * <code>string hash = 1;</code>
       * @return The hash.
       */
      public java.lang.String getHash() {
        java.lang.Object ref = hash_;
        if (!(ref instanceof java.lang.String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          java.lang.String s = bs.toStringUtf8();
          hash_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>string hash = 1;</code>
       * @return The bytes for hash.
       */
      public com.google.protobuf.ByteString
          getHashBytes() {
        java.lang.Object ref = hash_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          hash_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>string hash = 1;</code>
       * @param value The hash to set.
       * @return This builder for chaining.
       */
      public Builder setHash(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        hash_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>string hash = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearHash() {
        
        hash_ = getDefaultInstance().getHash();
        onChanged();
        return this;
      }
      /**
       * <code>string hash = 1;</code>
       * @param value The bytes for hash to set.
       * @return This builder for chaining.
       */
      public Builder setHashBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        hash_ = value;
        onChanged();
        return this;
      }

      private java.lang.Object name_ = "";
      /**
       * <code>string name = 2;</code>
       * @return The name.
       */
      public java.lang.String getName() {
        java.lang.Object ref = name_;
        if (!(ref instanceof java.lang.String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          java.lang.String s = bs.toStringUtf8();
          name_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>string name = 2;</code>
       * @return The bytes for name.
       */
      public com.google.protobuf.ByteString
          getNameBytes() {
        java.lang.Object ref = name_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          name_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>string name = 2;</code>
       * @param value The name to set.
       * @return This builder for chaining.
       */
      public Builder setName(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        name_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>string name = 2;</code>
       * @return This builder for chaining.
       */
      public Builder clearName() {
        
        name_ = getDefaultInstance().getName();
        onChanged();
        return this;
      }
      /**
       * <code>string name = 2;</code>
       * @param value The bytes for name to set.
       * @return This builder for chaining.
       */
      public Builder setNameBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        name_ = value;
        onChanged();
        return this;
      }

      private java.lang.Object locale_ = "";
      /**
       * <code>string locale = 3;</code>
       * @return The locale.
       */
      public java.lang.String getLocale() {
        java.lang.Object ref = locale_;
        if (!(ref instanceof java.lang.String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          java.lang.String s = bs.toStringUtf8();
          locale_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>string locale = 3;</code>
       * @return The bytes for locale.
       */
      public com.google.protobuf.ByteString
          getLocaleBytes() {
        java.lang.Object ref = locale_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          locale_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>string locale = 3;</code>
       * @param value The locale to set.
       * @return This builder for chaining.
       */
      public Builder setLocale(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        locale_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>string locale = 3;</code>
       * @return This builder for chaining.
       */
      public Builder clearLocale() {
        
        locale_ = getDefaultInstance().getLocale();
        onChanged();
        return this;
      }
      /**
       * <code>string locale = 3;</code>
       * @param value The bytes for locale to set.
       * @return This builder for chaining.
       */
      public Builder setLocaleBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        locale_ = value;
        onChanged();
        return this;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:opennlp.Model)
    }

    // @@protoc_insertion_point(class_scope:opennlp.Model)
    private static final opennlp.OpenNLPService.Model DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new opennlp.OpenNLPService.Model();
    }

    public static opennlp.OpenNLPService.Model getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<Model>
        PARSER = new com.google.protobuf.AbstractParser<Model>() {
      @java.lang.Override
      public Model parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new Model(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<Model> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<Model> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public opennlp.OpenNLPService.Model getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  public interface EmptyOrBuilder extends
      // @@protoc_insertion_point(interface_extends:opennlp.Empty)
      com.google.protobuf.MessageOrBuilder {
  }
  /**
   * Protobuf type {@code opennlp.Empty}
   */
  public static final class Empty extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:opennlp.Empty)
      EmptyOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use Empty.newBuilder() to construct.
    private Empty(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private Empty() {
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new Empty();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private Empty(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return opennlp.OpenNLPService.internal_static_opennlp_Empty_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return opennlp.OpenNLPService.internal_static_opennlp_Empty_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              opennlp.OpenNLPService.Empty.class, opennlp.OpenNLPService.Empty.Builder.class);
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof opennlp.OpenNLPService.Empty)) {
        return super.equals(obj);
      }
      opennlp.OpenNLPService.Empty other = (opennlp.OpenNLPService.Empty) obj;

      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static opennlp.OpenNLPService.Empty parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Empty parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Empty parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static opennlp.OpenNLPService.Empty parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(opennlp.OpenNLPService.Empty prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code opennlp.Empty}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:opennlp.Empty)
        opennlp.OpenNLPService.EmptyOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return opennlp.OpenNLPService.internal_static_opennlp_Empty_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return opennlp.OpenNLPService.internal_static_opennlp_Empty_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                opennlp.OpenNLPService.Empty.class, opennlp.OpenNLPService.Empty.Builder.class);
      }

      // Construct using opennlp.OpenNLPService.Empty.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return opennlp.OpenNLPService.internal_static_opennlp_Empty_descriptor;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Empty getDefaultInstanceForType() {
        return opennlp.OpenNLPService.Empty.getDefaultInstance();
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Empty build() {
        opennlp.OpenNLPService.Empty result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public opennlp.OpenNLPService.Empty buildPartial() {
        opennlp.OpenNLPService.Empty result = new opennlp.OpenNLPService.Empty(this);
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof opennlp.OpenNLPService.Empty) {
          return mergeFrom((opennlp.OpenNLPService.Empty)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(opennlp.OpenNLPService.Empty other) {
        if (other == opennlp.OpenNLPService.Empty.getDefaultInstance()) return this;
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        opennlp.OpenNLPService.Empty parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (opennlp.OpenNLPService.Empty) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:opennlp.Empty)
    }

    // @@protoc_insertion_point(class_scope:opennlp.Empty)
    private static final opennlp.OpenNLPService.Empty DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new opennlp.OpenNLPService.Empty();
    }

    public static opennlp.OpenNLPService.Empty getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<Empty>
        PARSER = new com.google.protobuf.AbstractParser<Empty>() {
      @java.lang.Override
      public Empty parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new Empty(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<Empty> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<Empty> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public opennlp.OpenNLPService.Empty getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_opennlp_TagRequest_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_opennlp_TagRequest_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_opennlp_TagWithContextRequest_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_opennlp_TagWithContextRequest_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_opennlp_StringList_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_opennlp_StringList_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_opennlp_SpanList_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_opennlp_SpanList_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_opennlp_Span_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_opennlp_Span_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_opennlp_AvailableModels_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_opennlp_AvailableModels_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_opennlp_Model_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_opennlp_Model_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_opennlp_Empty_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_opennlp_Empty_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\ropennlp.proto\022\007opennlp\"Y\n\nTagRequest\022\020" +
      "\n\010sentence\030\001 \003(\t\022%\n\006format\030\002 \001(\0162\025.openn" +
      "lp.POSTagFormat\022\022\n\nmodel_hash\030\003 \001(\t\"\200\001\n\025" +
      "TagWithContextRequest\022\020\n\010sentence\030\001 \003(\t\022" +
      "\032\n\022additional_context\030\002 \003(\t\022%\n\006format\030\003 " +
      "\001(\0162\025.opennlp.POSTagFormat\022\022\n\nmodel_hash" +
      "\030\004 \001(\t\"\034\n\nStringList\022\016\n\006values\030\001 \003(\t\")\n\010" +
      "SpanList\022\035\n\006values\030\001 \003(\0132\r.opennlp.Span\"" +
      ">\n\004Span\022\r\n\005start\030\001 \001(\005\022\013\n\003end\030\002 \001(\005\022\014\n\004p" +
      "rob\030\003 \001(\001\022\014\n\004type\030\004 \001(\t\"1\n\017AvailableMode" +
      "ls\022\036\n\006models\030\001 \003(\0132\016.opennlp.Model\"3\n\005Mo" +
      "del\022\014\n\004hash\030\001 \001(\t\022\014\n\004name\030\002 \001(\t\022\016\n\006local" +
      "e\030\003 \001(\t\"\007\n\005Empty*9\n\014POSTagFormat\022\006\n\002UD\020\000" +
      "\022\010\n\004PENN\020\001\022\013\n\007UNKNOWN\020\002\022\n\n\006CUSTOM\020\0032\312\001\n\020" +
      "PosTaggerService\022/\n\003Tag\022\023.opennlp.TagReq" +
      "uest\032\023.opennlp.StringList\022E\n\016TagWithCont" +
      "ext\022\036.opennlp.TagWithContextRequest\032\023.op" +
      "ennlp.StringList\022>\n\022GetAvailableModels\022\016" +
      ".opennlp.Empty\032\030.opennlp.AvailableModels" +
      "B\031\n\007opennlpB\016OpenNLPServiceb\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_opennlp_TagRequest_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_opennlp_TagRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_opennlp_TagRequest_descriptor,
        new java.lang.String[] { "Sentence", "Format", "ModelHash", });
    internal_static_opennlp_TagWithContextRequest_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_opennlp_TagWithContextRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_opennlp_TagWithContextRequest_descriptor,
        new java.lang.String[] { "Sentence", "AdditionalContext", "Format", "ModelHash", });
    internal_static_opennlp_StringList_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_opennlp_StringList_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_opennlp_StringList_descriptor,
        new java.lang.String[] { "Values", });
    internal_static_opennlp_SpanList_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_opennlp_SpanList_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_opennlp_SpanList_descriptor,
        new java.lang.String[] { "Values", });
    internal_static_opennlp_Span_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_opennlp_Span_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_opennlp_Span_descriptor,
        new java.lang.String[] { "Start", "End", "Prob", "Type", });
    internal_static_opennlp_AvailableModels_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_opennlp_AvailableModels_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_opennlp_AvailableModels_descriptor,
        new java.lang.String[] { "Models", });
    internal_static_opennlp_Model_descriptor =
      getDescriptor().getMessageTypes().get(6);
    internal_static_opennlp_Model_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_opennlp_Model_descriptor,
        new java.lang.String[] { "Hash", "Name", "Locale", });
    internal_static_opennlp_Empty_descriptor =
      getDescriptor().getMessageTypes().get(7);
    internal_static_opennlp_Empty_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_opennlp_Empty_descriptor,
        new java.lang.String[] { });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
