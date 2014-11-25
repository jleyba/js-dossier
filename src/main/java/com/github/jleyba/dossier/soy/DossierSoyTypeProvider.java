package com.github.jleyba.dossier.soy;

import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;

import java.util.HashMap;
import java.util.Map;

class DossierSoyTypeProvider implements SoyTypeProvider {
  
  private final ImmutableMap<String, SoyType> types;

  DossierSoyTypeProvider() {
    Map<String, SoyType> map = new HashMap<>();

    Descriptors.FileDescriptor fileDescriptor = Dossier.getDescriptor();

    for (Descriptors.EnumDescriptor enumDescriptor : fileDescriptor.getEnumTypes()) {
      ProtoEnumSoyType type = ProtoEnumSoyType.get(enumDescriptor);
      map.put(type.getName(), type);
    }

    for (Descriptors.Descriptor descriptor : fileDescriptor.getMessageTypes()) {
      registerTypes(map, descriptor);
    }

    types = ImmutableMap.copyOf(map);
  }

  private static void registerTypes(Map<String, SoyType> map, Descriptors.Descriptor descriptor) {
    ProtoMessageSoyType type = ProtoMessageSoyType.get(descriptor);
    map.put(type.getName(), type);

    for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
      if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
        registerTypes(map, field.getMessageType());
      }
    }
  }

  @Override
  public SoyType getType(String name, SoyTypeRegistry soyTypeRegistry) {
    return types.get(name);
  }
}
