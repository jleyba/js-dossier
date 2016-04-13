// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: dossier.proto

package com.github.jsdossier.proto;

/**
 * Protobuf type {@code dossier.JsTypeRenderSpec}
 *
 * <pre>
 * Describes how to render documentation for a JavaScript type.
 * </pre>
 */
public  final class JsTypeRenderSpec extends
    com.google.protobuf.GeneratedMessage implements
    // @@protoc_insertion_point(message_implements:dossier.JsTypeRenderSpec)
    JsTypeRenderSpecOrBuilder {
  // Use JsTypeRenderSpec.newBuilder() to construct.
  private JsTypeRenderSpec(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    super(builder);
  }
  private JsTypeRenderSpec() {
    type_ = java.util.Collections.emptyList();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private JsTypeRenderSpec(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry) {
    this();
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
          default: {
            if (!parseUnknownField(input, unknownFields,
                                   extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
          case 10: {
            if (!((mutable_bitField0_ & 0x00000001) == 0x00000001)) {
              type_ = new java.util.ArrayList<com.github.jsdossier.proto.JsType>();
              mutable_bitField0_ |= 0x00000001;
            }
            type_.add(input.readMessage(com.github.jsdossier.proto.JsType.parser(), extensionRegistry));
            break;
          }
          case 18: {
            com.github.jsdossier.proto.Resources.Builder subBuilder = null;
            if (((bitField0_ & 0x00000001) == 0x00000001)) {
              subBuilder = resources_.toBuilder();
            }
            resources_ = input.readMessage(com.github.jsdossier.proto.Resources.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(resources_);
              resources_ = subBuilder.buildPartial();
            }
            bitField0_ |= 0x00000001;
            break;
          }
          case 26: {
            com.github.jsdossier.proto.Index.Builder subBuilder = null;
            if (((bitField0_ & 0x00000002) == 0x00000002)) {
              subBuilder = index_.toBuilder();
            }
            index_ = input.readMessage(com.github.jsdossier.proto.Index.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(index_);
              index_ = subBuilder.buildPartial();
            }
            bitField0_ |= 0x00000002;
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw new RuntimeException(e.setUnfinishedMessage(this));
    } catch (java.io.IOException e) {
      throw new RuntimeException(
          new com.google.protobuf.InvalidProtocolBufferException(
              e.getMessage()).setUnfinishedMessage(this));
    } finally {
      if (((mutable_bitField0_ & 0x00000001) == 0x00000001)) {
        type_ = java.util.Collections.unmodifiableList(type_);
      }
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.github.jsdossier.proto.Dossier.internal_static_dossier_JsTypeRenderSpec_descriptor;
  }

  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.github.jsdossier.proto.Dossier.internal_static_dossier_JsTypeRenderSpec_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.github.jsdossier.proto.JsTypeRenderSpec.class, com.github.jsdossier.proto.JsTypeRenderSpec.Builder.class);
  }

  private int bitField0_;
  public static final int TYPE_FIELD_NUMBER = 1;
  private java.util.List<com.github.jsdossier.proto.JsType> type_;
  /**
   * <code>repeated .dossier.JsType type = 1;</code>
   *
   * <pre>
   * The types to generate documentation for.
   * </pre>
   */
  public java.util.List<com.github.jsdossier.proto.JsType> getTypeList() {
    return type_;
  }
  /**
   * <code>repeated .dossier.JsType type = 1;</code>
   *
   * <pre>
   * The types to generate documentation for.
   * </pre>
   */
  public java.util.List<? extends com.github.jsdossier.proto.JsTypeOrBuilder> 
      getTypeOrBuilderList() {
    return type_;
  }
  /**
   * <code>repeated .dossier.JsType type = 1;</code>
   *
   * <pre>
   * The types to generate documentation for.
   * </pre>
   */
  public int getTypeCount() {
    return type_.size();
  }
  /**
   * <code>repeated .dossier.JsType type = 1;</code>
   *
   * <pre>
   * The types to generate documentation for.
   * </pre>
   */
  public com.github.jsdossier.proto.JsType getType(int index) {
    return type_.get(index);
  }
  /**
   * <code>repeated .dossier.JsType type = 1;</code>
   *
   * <pre>
   * The types to generate documentation for.
   * </pre>
   */
  public com.github.jsdossier.proto.JsTypeOrBuilder getTypeOrBuilder(
      int index) {
    return type_.get(index);
  }

  public static final int RESOURCES_FIELD_NUMBER = 2;
  private com.github.jsdossier.proto.Resources resources_;
  /**
   * <code>required .dossier.Resources resources = 2;</code>
   *
   * <pre>
   * The resources to include.
   * </pre>
   */
  public boolean hasResources() {
    return ((bitField0_ & 0x00000001) == 0x00000001);
  }
  /**
   * <code>required .dossier.Resources resources = 2;</code>
   *
   * <pre>
   * The resources to include.
   * </pre>
   */
  public com.github.jsdossier.proto.Resources getResources() {
    return resources_ == null ? com.github.jsdossier.proto.Resources.getDefaultInstance() : resources_;
  }
  /**
   * <code>required .dossier.Resources resources = 2;</code>
   *
   * <pre>
   * The resources to include.
   * </pre>
   */
  public com.github.jsdossier.proto.ResourcesOrBuilder getResourcesOrBuilder() {
    return resources_ == null ? com.github.jsdossier.proto.Resources.getDefaultInstance() : resources_;
  }

  public static final int INDEX_FIELD_NUMBER = 3;
  private com.github.jsdossier.proto.Index index_;
  /**
   * <code>required .dossier.Index index = 3;</code>
   *
   * <pre>
   * Navigation index.
   * </pre>
   */
  public boolean hasIndex() {
    return ((bitField0_ & 0x00000002) == 0x00000002);
  }
  /**
   * <code>required .dossier.Index index = 3;</code>
   *
   * <pre>
   * Navigation index.
   * </pre>
   */
  public com.github.jsdossier.proto.Index getIndex() {
    return index_ == null ? com.github.jsdossier.proto.Index.getDefaultInstance() : index_;
  }
  /**
   * <code>required .dossier.Index index = 3;</code>
   *
   * <pre>
   * Navigation index.
   * </pre>
   */
  public com.github.jsdossier.proto.IndexOrBuilder getIndexOrBuilder() {
    return index_ == null ? com.github.jsdossier.proto.Index.getDefaultInstance() : index_;
  }

  private byte memoizedIsInitialized = -1;
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    if (!hasResources()) {
      memoizedIsInitialized = 0;
      return false;
    }
    if (!hasIndex()) {
      memoizedIsInitialized = 0;
      return false;
    }
    for (int i = 0; i < getTypeCount(); i++) {
      if (!getType(i).isInitialized()) {
        memoizedIsInitialized = 0;
        return false;
      }
    }
    if (!getIndex().isInitialized()) {
      memoizedIsInitialized = 0;
      return false;
    }
    memoizedIsInitialized = 1;
    return true;
  }

  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    for (int i = 0; i < type_.size(); i++) {
      output.writeMessage(1, type_.get(i));
    }
    if (((bitField0_ & 0x00000001) == 0x00000001)) {
      output.writeMessage(2, getResources());
    }
    if (((bitField0_ & 0x00000002) == 0x00000002)) {
      output.writeMessage(3, getIndex());
    }
    unknownFields.writeTo(output);
  }

  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    for (int i = 0; i < type_.size(); i++) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(1, type_.get(i));
    }
    if (((bitField0_ & 0x00000001) == 0x00000001)) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(2, getResources());
    }
    if (((bitField0_ & 0x00000002) == 0x00000002)) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(3, getIndex());
    }
    size += unknownFields.getSerializedSize();
    memoizedSize = size;
    return size;
  }

  private static final long serialVersionUID = 0L;
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input, extensionRegistry);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static com.github.jsdossier.proto.JsTypeRenderSpec parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }

  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(com.github.jsdossier.proto.JsTypeRenderSpec prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessage.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code dossier.JsTypeRenderSpec}
   *
   * <pre>
   * Describes how to render documentation for a JavaScript type.
   * </pre>
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessage.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:dossier.JsTypeRenderSpec)
      com.github.jsdossier.proto.JsTypeRenderSpecOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.github.jsdossier.proto.Dossier.internal_static_dossier_JsTypeRenderSpec_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.github.jsdossier.proto.Dossier.internal_static_dossier_JsTypeRenderSpec_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.github.jsdossier.proto.JsTypeRenderSpec.class, com.github.jsdossier.proto.JsTypeRenderSpec.Builder.class);
    }

    // Construct using com.github.jsdossier.proto.JsTypeRenderSpec.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessage.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
        getTypeFieldBuilder();
        getResourcesFieldBuilder();
        getIndexFieldBuilder();
      }
    }
    public Builder clear() {
      super.clear();
      if (typeBuilder_ == null) {
        type_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000001);
      } else {
        typeBuilder_.clear();
      }
      if (resourcesBuilder_ == null) {
        resources_ = null;
      } else {
        resourcesBuilder_.clear();
      }
      bitField0_ = (bitField0_ & ~0x00000002);
      if (indexBuilder_ == null) {
        index_ = null;
      } else {
        indexBuilder_.clear();
      }
      bitField0_ = (bitField0_ & ~0x00000004);
      return this;
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.github.jsdossier.proto.Dossier.internal_static_dossier_JsTypeRenderSpec_descriptor;
    }

    public com.github.jsdossier.proto.JsTypeRenderSpec getDefaultInstanceForType() {
      return com.github.jsdossier.proto.JsTypeRenderSpec.getDefaultInstance();
    }

    public com.github.jsdossier.proto.JsTypeRenderSpec build() {
      com.github.jsdossier.proto.JsTypeRenderSpec result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public com.github.jsdossier.proto.JsTypeRenderSpec buildPartial() {
      com.github.jsdossier.proto.JsTypeRenderSpec result = new com.github.jsdossier.proto.JsTypeRenderSpec(this);
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (typeBuilder_ == null) {
        if (((bitField0_ & 0x00000001) == 0x00000001)) {
          type_ = java.util.Collections.unmodifiableList(type_);
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.type_ = type_;
      } else {
        result.type_ = typeBuilder_.build();
      }
      if (((from_bitField0_ & 0x00000002) == 0x00000002)) {
        to_bitField0_ |= 0x00000001;
      }
      if (resourcesBuilder_ == null) {
        result.resources_ = resources_;
      } else {
        result.resources_ = resourcesBuilder_.build();
      }
      if (((from_bitField0_ & 0x00000004) == 0x00000004)) {
        to_bitField0_ |= 0x00000002;
      }
      if (indexBuilder_ == null) {
        result.index_ = index_;
      } else {
        result.index_ = indexBuilder_.build();
      }
      result.bitField0_ = to_bitField0_;
      onBuilt();
      return result;
    }

    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.github.jsdossier.proto.JsTypeRenderSpec) {
        return mergeFrom((com.github.jsdossier.proto.JsTypeRenderSpec)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.github.jsdossier.proto.JsTypeRenderSpec other) {
      if (other == com.github.jsdossier.proto.JsTypeRenderSpec.getDefaultInstance()) return this;
      if (typeBuilder_ == null) {
        if (!other.type_.isEmpty()) {
          if (type_.isEmpty()) {
            type_ = other.type_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureTypeIsMutable();
            type_.addAll(other.type_);
          }
          onChanged();
        }
      } else {
        if (!other.type_.isEmpty()) {
          if (typeBuilder_.isEmpty()) {
            typeBuilder_.dispose();
            typeBuilder_ = null;
            type_ = other.type_;
            bitField0_ = (bitField0_ & ~0x00000001);
            typeBuilder_ = 
              com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders ?
                 getTypeFieldBuilder() : null;
          } else {
            typeBuilder_.addAllMessages(other.type_);
          }
        }
      }
      if (other.hasResources()) {
        mergeResources(other.getResources());
      }
      if (other.hasIndex()) {
        mergeIndex(other.getIndex());
      }
      this.mergeUnknownFields(other.unknownFields);
      onChanged();
      return this;
    }

    public final boolean isInitialized() {
      if (!hasResources()) {
        return false;
      }
      if (!hasIndex()) {
        return false;
      }
      for (int i = 0; i < getTypeCount(); i++) {
        if (!getType(i).isInitialized()) {
          return false;
        }
      }
      if (!getIndex().isInitialized()) {
        return false;
      }
      return true;
    }

    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      com.github.jsdossier.proto.JsTypeRenderSpec parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.github.jsdossier.proto.JsTypeRenderSpec) e.getUnfinishedMessage();
        throw e;
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private java.util.List<com.github.jsdossier.proto.JsType> type_ =
      java.util.Collections.emptyList();
    private void ensureTypeIsMutable() {
      if (!((bitField0_ & 0x00000001) == 0x00000001)) {
        type_ = new java.util.ArrayList<com.github.jsdossier.proto.JsType>(type_);
        bitField0_ |= 0x00000001;
       }
    }

    private com.google.protobuf.RepeatedFieldBuilder<
        com.github.jsdossier.proto.JsType, com.github.jsdossier.proto.JsType.Builder, com.github.jsdossier.proto.JsTypeOrBuilder> typeBuilder_;

    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public java.util.List<com.github.jsdossier.proto.JsType> getTypeList() {
      if (typeBuilder_ == null) {
        return java.util.Collections.unmodifiableList(type_);
      } else {
        return typeBuilder_.getMessageList();
      }
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public int getTypeCount() {
      if (typeBuilder_ == null) {
        return type_.size();
      } else {
        return typeBuilder_.getCount();
      }
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public com.github.jsdossier.proto.JsType getType(int index) {
      if (typeBuilder_ == null) {
        return type_.get(index);
      } else {
        return typeBuilder_.getMessage(index);
      }
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder setType(
        int index, com.github.jsdossier.proto.JsType value) {
      if (typeBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureTypeIsMutable();
        type_.set(index, value);
        onChanged();
      } else {
        typeBuilder_.setMessage(index, value);
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder setType(
        int index, com.github.jsdossier.proto.JsType.Builder builderForValue) {
      if (typeBuilder_ == null) {
        ensureTypeIsMutable();
        type_.set(index, builderForValue.build());
        onChanged();
      } else {
        typeBuilder_.setMessage(index, builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder addType(com.github.jsdossier.proto.JsType value) {
      if (typeBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureTypeIsMutable();
        type_.add(value);
        onChanged();
      } else {
        typeBuilder_.addMessage(value);
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder addType(
        int index, com.github.jsdossier.proto.JsType value) {
      if (typeBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureTypeIsMutable();
        type_.add(index, value);
        onChanged();
      } else {
        typeBuilder_.addMessage(index, value);
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder addType(
        com.github.jsdossier.proto.JsType.Builder builderForValue) {
      if (typeBuilder_ == null) {
        ensureTypeIsMutable();
        type_.add(builderForValue.build());
        onChanged();
      } else {
        typeBuilder_.addMessage(builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder addType(
        int index, com.github.jsdossier.proto.JsType.Builder builderForValue) {
      if (typeBuilder_ == null) {
        ensureTypeIsMutable();
        type_.add(index, builderForValue.build());
        onChanged();
      } else {
        typeBuilder_.addMessage(index, builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder addAllType(
        java.lang.Iterable<? extends com.github.jsdossier.proto.JsType> values) {
      if (typeBuilder_ == null) {
        ensureTypeIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, type_);
        onChanged();
      } else {
        typeBuilder_.addAllMessages(values);
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder clearType() {
      if (typeBuilder_ == null) {
        type_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
      } else {
        typeBuilder_.clear();
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public Builder removeType(int index) {
      if (typeBuilder_ == null) {
        ensureTypeIsMutable();
        type_.remove(index);
        onChanged();
      } else {
        typeBuilder_.remove(index);
      }
      return this;
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public com.github.jsdossier.proto.JsType.Builder getTypeBuilder(
        int index) {
      return getTypeFieldBuilder().getBuilder(index);
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public com.github.jsdossier.proto.JsTypeOrBuilder getTypeOrBuilder(
        int index) {
      if (typeBuilder_ == null) {
        return type_.get(index);  } else {
        return typeBuilder_.getMessageOrBuilder(index);
      }
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public java.util.List<? extends com.github.jsdossier.proto.JsTypeOrBuilder> 
         getTypeOrBuilderList() {
      if (typeBuilder_ != null) {
        return typeBuilder_.getMessageOrBuilderList();
      } else {
        return java.util.Collections.unmodifiableList(type_);
      }
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public com.github.jsdossier.proto.JsType.Builder addTypeBuilder() {
      return getTypeFieldBuilder().addBuilder(
          com.github.jsdossier.proto.JsType.getDefaultInstance());
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public com.github.jsdossier.proto.JsType.Builder addTypeBuilder(
        int index) {
      return getTypeFieldBuilder().addBuilder(
          index, com.github.jsdossier.proto.JsType.getDefaultInstance());
    }
    /**
     * <code>repeated .dossier.JsType type = 1;</code>
     *
     * <pre>
     * The types to generate documentation for.
     * </pre>
     */
    public java.util.List<com.github.jsdossier.proto.JsType.Builder> 
         getTypeBuilderList() {
      return getTypeFieldBuilder().getBuilderList();
    }
    private com.google.protobuf.RepeatedFieldBuilder<
        com.github.jsdossier.proto.JsType, com.github.jsdossier.proto.JsType.Builder, com.github.jsdossier.proto.JsTypeOrBuilder> 
        getTypeFieldBuilder() {
      if (typeBuilder_ == null) {
        typeBuilder_ = new com.google.protobuf.RepeatedFieldBuilder<
            com.github.jsdossier.proto.JsType, com.github.jsdossier.proto.JsType.Builder, com.github.jsdossier.proto.JsTypeOrBuilder>(
                type_,
                ((bitField0_ & 0x00000001) == 0x00000001),
                getParentForChildren(),
                isClean());
        type_ = null;
      }
      return typeBuilder_;
    }

    private com.github.jsdossier.proto.Resources resources_ = null;
    private com.google.protobuf.SingleFieldBuilder<
        com.github.jsdossier.proto.Resources, com.github.jsdossier.proto.Resources.Builder, com.github.jsdossier.proto.ResourcesOrBuilder> resourcesBuilder_;
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    public boolean hasResources() {
      return ((bitField0_ & 0x00000002) == 0x00000002);
    }
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    public com.github.jsdossier.proto.Resources getResources() {
      if (resourcesBuilder_ == null) {
        return resources_ == null ? com.github.jsdossier.proto.Resources.getDefaultInstance() : resources_;
      } else {
        return resourcesBuilder_.getMessage();
      }
    }
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    public Builder setResources(com.github.jsdossier.proto.Resources value) {
      if (resourcesBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        resources_ = value;
        onChanged();
      } else {
        resourcesBuilder_.setMessage(value);
      }
      bitField0_ |= 0x00000002;
      return this;
    }
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    public Builder setResources(
        com.github.jsdossier.proto.Resources.Builder builderForValue) {
      if (resourcesBuilder_ == null) {
        resources_ = builderForValue.build();
        onChanged();
      } else {
        resourcesBuilder_.setMessage(builderForValue.build());
      }
      bitField0_ |= 0x00000002;
      return this;
    }
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    public Builder mergeResources(com.github.jsdossier.proto.Resources value) {
      if (resourcesBuilder_ == null) {
        if (((bitField0_ & 0x00000002) == 0x00000002) &&
            resources_ != null &&
            resources_ != com.github.jsdossier.proto.Resources.getDefaultInstance()) {
          resources_ =
            com.github.jsdossier.proto.Resources.newBuilder(resources_).mergeFrom(value).buildPartial();
        } else {
          resources_ = value;
        }
        onChanged();
      } else {
        resourcesBuilder_.mergeFrom(value);
      }
      bitField0_ |= 0x00000002;
      return this;
    }
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    public Builder clearResources() {
      if (resourcesBuilder_ == null) {
        resources_ = null;
        onChanged();
      } else {
        resourcesBuilder_.clear();
      }
      bitField0_ = (bitField0_ & ~0x00000002);
      return this;
    }
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    public com.github.jsdossier.proto.Resources.Builder getResourcesBuilder() {
      bitField0_ |= 0x00000002;
      onChanged();
      return getResourcesFieldBuilder().getBuilder();
    }
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    public com.github.jsdossier.proto.ResourcesOrBuilder getResourcesOrBuilder() {
      if (resourcesBuilder_ != null) {
        return resourcesBuilder_.getMessageOrBuilder();
      } else {
        return resources_ == null ?
            com.github.jsdossier.proto.Resources.getDefaultInstance() : resources_;
      }
    }
    /**
     * <code>required .dossier.Resources resources = 2;</code>
     *
     * <pre>
     * The resources to include.
     * </pre>
     */
    private com.google.protobuf.SingleFieldBuilder<
        com.github.jsdossier.proto.Resources, com.github.jsdossier.proto.Resources.Builder, com.github.jsdossier.proto.ResourcesOrBuilder> 
        getResourcesFieldBuilder() {
      if (resourcesBuilder_ == null) {
        resourcesBuilder_ = new com.google.protobuf.SingleFieldBuilder<
            com.github.jsdossier.proto.Resources, com.github.jsdossier.proto.Resources.Builder, com.github.jsdossier.proto.ResourcesOrBuilder>(
                getResources(),
                getParentForChildren(),
                isClean());
        resources_ = null;
      }
      return resourcesBuilder_;
    }

    private com.github.jsdossier.proto.Index index_ = null;
    private com.google.protobuf.SingleFieldBuilder<
        com.github.jsdossier.proto.Index, com.github.jsdossier.proto.Index.Builder, com.github.jsdossier.proto.IndexOrBuilder> indexBuilder_;
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    public boolean hasIndex() {
      return ((bitField0_ & 0x00000004) == 0x00000004);
    }
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    public com.github.jsdossier.proto.Index getIndex() {
      if (indexBuilder_ == null) {
        return index_ == null ? com.github.jsdossier.proto.Index.getDefaultInstance() : index_;
      } else {
        return indexBuilder_.getMessage();
      }
    }
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    public Builder setIndex(com.github.jsdossier.proto.Index value) {
      if (indexBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        index_ = value;
        onChanged();
      } else {
        indexBuilder_.setMessage(value);
      }
      bitField0_ |= 0x00000004;
      return this;
    }
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    public Builder setIndex(
        com.github.jsdossier.proto.Index.Builder builderForValue) {
      if (indexBuilder_ == null) {
        index_ = builderForValue.build();
        onChanged();
      } else {
        indexBuilder_.setMessage(builderForValue.build());
      }
      bitField0_ |= 0x00000004;
      return this;
    }
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    public Builder mergeIndex(com.github.jsdossier.proto.Index value) {
      if (indexBuilder_ == null) {
        if (((bitField0_ & 0x00000004) == 0x00000004) &&
            index_ != null &&
            index_ != com.github.jsdossier.proto.Index.getDefaultInstance()) {
          index_ =
            com.github.jsdossier.proto.Index.newBuilder(index_).mergeFrom(value).buildPartial();
        } else {
          index_ = value;
        }
        onChanged();
      } else {
        indexBuilder_.mergeFrom(value);
      }
      bitField0_ |= 0x00000004;
      return this;
    }
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    public Builder clearIndex() {
      if (indexBuilder_ == null) {
        index_ = null;
        onChanged();
      } else {
        indexBuilder_.clear();
      }
      bitField0_ = (bitField0_ & ~0x00000004);
      return this;
    }
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    public com.github.jsdossier.proto.Index.Builder getIndexBuilder() {
      bitField0_ |= 0x00000004;
      onChanged();
      return getIndexFieldBuilder().getBuilder();
    }
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    public com.github.jsdossier.proto.IndexOrBuilder getIndexOrBuilder() {
      if (indexBuilder_ != null) {
        return indexBuilder_.getMessageOrBuilder();
      } else {
        return index_ == null ?
            com.github.jsdossier.proto.Index.getDefaultInstance() : index_;
      }
    }
    /**
     * <code>required .dossier.Index index = 3;</code>
     *
     * <pre>
     * Navigation index.
     * </pre>
     */
    private com.google.protobuf.SingleFieldBuilder<
        com.github.jsdossier.proto.Index, com.github.jsdossier.proto.Index.Builder, com.github.jsdossier.proto.IndexOrBuilder> 
        getIndexFieldBuilder() {
      if (indexBuilder_ == null) {
        indexBuilder_ = new com.google.protobuf.SingleFieldBuilder<
            com.github.jsdossier.proto.Index, com.github.jsdossier.proto.Index.Builder, com.github.jsdossier.proto.IndexOrBuilder>(
                getIndex(),
                getParentForChildren(),
                isClean());
        index_ = null;
      }
      return indexBuilder_;
    }

    // @@protoc_insertion_point(builder_scope:dossier.JsTypeRenderSpec)
  }

  // @@protoc_insertion_point(class_scope:dossier.JsTypeRenderSpec)
  private static final com.github.jsdossier.proto.JsTypeRenderSpec DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.github.jsdossier.proto.JsTypeRenderSpec();
  }

  public static com.github.jsdossier.proto.JsTypeRenderSpec getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<JsTypeRenderSpec>
      PARSER = new com.google.protobuf.AbstractParser<JsTypeRenderSpec>() {
    public JsTypeRenderSpec parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      try {
        return new JsTypeRenderSpec(input, extensionRegistry);
      } catch (RuntimeException e) {
        if (e.getCause() instanceof
            com.google.protobuf.InvalidProtocolBufferException) {
          throw (com.google.protobuf.InvalidProtocolBufferException)
              e.getCause();
        }
        throw e;
      }
    }
  };

  public static com.google.protobuf.Parser<JsTypeRenderSpec> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<JsTypeRenderSpec> getParserForType() {
    return PARSER;
  }

  public com.github.jsdossier.proto.JsTypeRenderSpec getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

