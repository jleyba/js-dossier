// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: state.proto

package com.github.jsdossier.proto;

public final class State {
  private State() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_state_PageSnapshot_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_state_PageSnapshot_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\013state.proto\022\rdossier.state\"^\n\014PageSnap" +
      "shot\022\n\n\002id\030\001 \001(\t\022\020\n\010data_uri\030\002 \001(\t\022\r\n\005ti" +
      "tle\030\003 \001(\t\022\016\n\006scroll\030\004 \001(\005\022\021\n\topen_card\030\005" +
      " \003(\tB\036\n\032com.github.jsdossier.protoP\001b\006pr" +
      "oto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_dossier_state_PageSnapshot_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_dossier_state_PageSnapshot_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_state_PageSnapshot_descriptor,
        new java.lang.String[] { "Id", "DataUri", "Title", "Scroll", "OpenCard", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}