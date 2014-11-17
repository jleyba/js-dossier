package com.github.jleyba.dossier.soy;

import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;

class DossierSoyTypeProvider implements SoyTypeProvider {
  
  private final ImmutableMap<String, SoyType> types;

  DossierSoyTypeProvider() {
    ImmutableMap.Builder<String, SoyType> builder = ImmutableMap.builder();

    Descriptors.FileDescriptor fileDescriptor = Dossier.getDescriptor();

    for (Descriptors.EnumDescriptor enumDescriptor : fileDescriptor.getEnumTypes()) {
      ProtoEnumSoyType type = ProtoEnumSoyType.get(enumDescriptor);
      builder.put(type.getName(), type);
    }

    for (Descriptors.Descriptor descriptor : fileDescriptor.getMessageTypes()) {
      ProtoMessageSoyType type = ProtoMessageSoyType.get(descriptor);
      builder.put(type.getName(), type);
    }

    types = builder.build();
  }

  @Override
  public SoyType getType(String name, SoyTypeRegistry soyTypeRegistry) {
    return types.get(name);
  }
}
