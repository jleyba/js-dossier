// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: dossier.proto

package com.github.jsdossier.proto;

public final class Dossier {
  private Dossier() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_SourceFile_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_SourceFile_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Resources_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Resources_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Link_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Link_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_SourceLink_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_SourceLink_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Comment_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Comment_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Comment_Token_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Comment_Token_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Tags_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Tags_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_BaseProperty_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_BaseProperty_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Property_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Property_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Function_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Function_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Function_Detail_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Function_Detail_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Enumeration_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Enumeration_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Enumeration_Value_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Enumeration_Value_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_Index_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_Index_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_JsType_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_JsType_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_JsType_TypeSummary_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_JsType_TypeSummary_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_JsType_NestedTypes_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_JsType_NestedTypes_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_JsType_ParentLink_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_JsType_ParentLink_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_HtmlRenderSpec_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_HtmlRenderSpec_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_SourceFileRenderSpec_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_SourceFileRenderSpec_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_dossier_JsTypeRenderSpec_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_dossier_JsTypeRenderSpec_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\rdossier.proto\022\007dossier\032\roptions.proto\032" +
      "\020expression.proto\"<\n\nSourceFile\022\021\n\tbase_" +
      "name\030\001 \001(\t\022\014\n\004path\030\002 \001(\t\022\r\n\005lines\030\003 \003(\t\"" +
      "Z\n\tResources\022\023\n\003css\030\001 \003(\tB\006\362\201\031\002\010\001\022\033\n\013hea" +
      "d_script\030\002 \003(\tB\006\362\201\031\002\010\001\022\033\n\013tail_script\030\003 " +
      "\003(\tB\006\362\201\031\002\010\001\"*\n\004Link\022\014\n\004text\030\001 \001(\t\022\024\n\004hre" +
      "f\030\002 \001(\tB\006\362\201\031\002\010\001\"=\n\nSourceLink\022\024\n\004path\030\001 " +
      "\001(\tB\006\362\201\031\002\010\001\022\014\n\004line\030\002 \001(\005\022\013\n\003uri\030\003 \001(\t\"\230" +
      "\001\n\007Comment\022%\n\005token\030\001 \003(\0132\026.dossier.Comm" +
      "ent.Token\032f\n\005Token\022\016\n\004text\030\001 \001(\tH\000\022\026\n\004ht",
      "ml\030\002 \001(\tB\006\362\201\031\002\020\001H\000\022*\n\004link\030\003 \001(\0132\034.dossi" +
      "er.expression.TypeLinkB\t\n\007content\"\265\001\n\004Ta" +
      "gs\022\020\n\010is_const\030\001 \001(\010\022\025\n\ris_deprecated\030\002 " +
      "\001(\010\022\017\n\007is_dict\030\003 \001(\010\022\020\n\010is_final\030\004 \001(\010\022\024" +
      "\n\014is_interface\030\005 \001(\010\022\021\n\tis_module\030\006 \001(\010\022" +
      "\021\n\tis_struct\030\007 \001(\010\022\022\n\nis_default\030\010 \001(\010\022\021" +
      "\n\tis_record\030\t \001(\010\"\223\003\n\014BaseProperty\022\014\n\004na" +
      "me\030\001 \001(\t\022#\n\006source\030\002 \001(\0132\023.dossier.Sourc" +
      "eLink\022%\n\013description\030\003 \001(\0132\020.dossier.Com" +
      "ment\022%\n\013deprecation\030\004 \001(\0132\020.dossier.Comm",
      "ent\022\'\n\nvisibility\030\005 \001(\0162\023.dossier.Visibi" +
      "lity\022\033\n\004tags\030\006 \001(\0132\r.dossier.Tags\0221\n\ndef" +
      "ined_by\030\007 \001(\0132\035.dossier.expression.Named" +
      "Type\0220\n\toverrides\030\010 \001(\0132\035.dossier.expres" +
      "sion.NamedType\0223\n\014specified_by\030\t \003(\0132\035.d" +
      "ossier.expression.NamedType\022\"\n\010see_also\030" +
      "\n \003(\0132\020.dossier.Comment\"a\n\010Property\022#\n\004b" +
      "ase\030\001 \001(\0132\025.dossier.BaseProperty\0220\n\004type" +
      "\030\002 \001(\0132\".dossier.expression.TypeExpressi" +
      "on\"\320\002\n\010Function\022#\n\004base\030\001 \001(\0132\025.dossier.",
      "BaseProperty\022\025\n\rtemplate_name\030\002 \003(\t\022\026\n\016i" +
      "s_constructor\030\003 \001(\010\022+\n\tparameter\030\004 \003(\0132\030" +
      ".dossier.Function.Detail\022(\n\006return\030\005 \001(\013" +
      "2\030.dossier.Function.Detail\022(\n\006thrown\030\006 \003" +
      "(\0132\030.dossier.Function.Detail\032o\n\006Detail\022\014" +
      "\n\004name\030\001 \001(\t\0220\n\004type\030\002 \001(\0132\".dossier.exp" +
      "ression.TypeExpression\022%\n\013description\030\003 " +
      "\001(\0132\020.dossier.Comment\"\370\001\n\013Enumeration\0220\n" +
      "\004type\030\001 \001(\0132\".dossier.expression.TypeExp" +
      "ression\022)\n\005value\030\002 \003(\0132\032.dossier.Enumera",
      "tion.Value\022\'\n\nvisibility\030\003 \001(\0162\023.dossier" +
      ".Visibility\032c\n\005Value\022\014\n\004name\030\001 \001(\t\022%\n\013de" +
      "scription\030\002 \001(\0132\020.dossier.Comment\022%\n\013dep" +
      "recation\030\003 \001(\0132\020.dossier.Comment\"}\n\005Inde" +
      "x\022\024\n\004home\030\001 \001(\tB\006\362\201\031\002\010\001\022\025\n\rinclude_types" +
      "\030\002 \001(\010\022\027\n\017include_modules\030\003 \001(\010\022\033\n\004link\030" +
      "\004 \003(\0132\r.dossier.Link\022\021\n\ttimestamp\030\005 \001(\003\"" +
      "\263\n\n\006JsType\022\014\n\004name\030\001 \001(\t\022#\n\006source\030\005 \001(\013" +
      "2\023.dossier.SourceLink\022+\n\006nested\030\006 \001(\0132\033." +
      "dossier.JsType.NestedTypes\022%\n\013descriptio",
      "n\030\007 \001(\0132\020.dossier.Comment\022\033\n\004tags\030\010 \001(\0132" +
      "\r.dossier.Tags\022%\n\013deprecation\030\t \001(\0132\020.do" +
      "ssier.Comment\022#\n\010type_def\030\n \003(\0132\021.dossie" +
      "r.Property\022)\n\013enumeration\030\013 \001(\0132\024.dossie" +
      "r.Enumeration\022*\n\017static_function\030\014 \003(\0132\021" +
      ".dossier.Function\022*\n\017static_property\030\r \003" +
      "(\0132\021.dossier.Property\022(\n\rmain_function\030\016" +
      " \001(\0132\021.dossier.Function\022!\n\006method\030\017 \003(\0132" +
      "\021.dossier.Function\022 \n\005field\030\020 \003(\0132\021.doss" +
      "ier.Property\0224\n\rextended_type\030\022 \003(\0132\035.do",
      "ssier.expression.NamedType\0227\n\020implemente" +
      "d_type\030\023 \003(\0132\035.dossier.expression.NamedT" +
      "ype\022.\n\007subtype\030\032 \003(\0132\035.dossier.expressio" +
      "n.NamedType\0225\n\016implementation\030\033 \003(\0132\035.do" +
      "ssier.expression.NamedType\022,\n\021compiler_c" +
      "onstant\030\024 \003(\0132\021.dossier.Property\022*\n\006pare" +
      "nt\030\025 \001(\0132\032.dossier.JsType.ParentLink\0223\n\014" +
      "aliased_type\030\026 \001(\0132\035.dossier.expression." +
      "NamedType\0222\n\013known_alias\030\034 \003(\0132\035.dossier" +
      ".expression.NamedType\022\020\n\010filename\030\027 \001(\t\022",
      "\026\n\016qualified_name\030\030 \001(\t\022,\n\021reexported_mo" +
      "dule\030\031 \003(\0132\021.dossier.Property\032q\n\013TypeSum" +
      "mary\022\014\n\004name\030\001 \001(\t\022\024\n\004href\030\002 \001(\tB\006\362\201\031\002\010\001" +
      "\022!\n\007summary\030\003 \001(\0132\020.dossier.Comment\022\033\n\004t" +
      "ags\030\004 \001(\0132\r.dossier.Tags\032\224\001\n\013NestedTypes" +
      "\022*\n\005class\030\001 \003(\0132\033.dossier.JsType.TypeSum" +
      "mary\022)\n\004enum\030\002 \003(\0132\033.dossier.JsType.Type" +
      "Summary\022.\n\tinterface\030\003 \003(\0132\033.dossier.JsT" +
      "ype.TypeSummary\032L\n\nParentLink\022+\n\004type\030\001 " +
      "\001(\0132\035.dossier.expression.NamedType\022\021\n\tis",
      "_module\030\002 \001(\010\"\210\001\n\016HtmlRenderSpec\022%\n\treso" +
      "urces\030\001 \001(\0132\022.dossier.Resources\022\r\n\005title" +
      "\030\002 \001(\t\022!\n\007content\030\003 \001(\0132\020.dossier.Commen" +
      "t\022\035\n\005index\030\004 \001(\0132\016.dossier.Index\"\177\n\024Sour" +
      "ceFileRenderSpec\022%\n\tresources\030\001 \001(\0132\022.do" +
      "ssier.Resources\022!\n\004file\030\002 \001(\0132\023.dossier." +
      "SourceFile\022\035\n\005index\030\003 \001(\0132\016.dossier.Inde" +
      "x\"w\n\020JsTypeRenderSpec\022\035\n\004type\030\001 \003(\0132\017.do" +
      "ssier.JsType\022%\n\tresources\030\002 \001(\0132\022.dossie" +
      "r.Resources\022\035\n\005index\030\003 \001(\0132\016.dossier.Ind",
      "ex*A\n\nVisibility\022\n\n\006PUBLIC\020\000\022\r\n\tPROTECTE" +
      "D\020\001\022\013\n\007PRIVATE\020\002\022\013\n\007PACKAGE\020\003B\036\n\032com.git" +
      "hub.jsdossier.protoP\001b\006proto3"
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
          com.github.jsdossier.proto.Options.getDescriptor(),
          com.github.jsdossier.proto.Expression.getDescriptor(),
        }, assigner);
    internal_static_dossier_SourceFile_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_dossier_SourceFile_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_SourceFile_descriptor,
        new java.lang.String[] { "BaseName", "Path", "Lines", });
    internal_static_dossier_Resources_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_dossier_Resources_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Resources_descriptor,
        new java.lang.String[] { "Css", "HeadScript", "TailScript", });
    internal_static_dossier_Link_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_dossier_Link_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Link_descriptor,
        new java.lang.String[] { "Text", "Href", });
    internal_static_dossier_SourceLink_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_dossier_SourceLink_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_SourceLink_descriptor,
        new java.lang.String[] { "Path", "Line", "Uri", });
    internal_static_dossier_Comment_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_dossier_Comment_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Comment_descriptor,
        new java.lang.String[] { "Token", });
    internal_static_dossier_Comment_Token_descriptor =
      internal_static_dossier_Comment_descriptor.getNestedTypes().get(0);
    internal_static_dossier_Comment_Token_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Comment_Token_descriptor,
        new java.lang.String[] { "Text", "Html", "Link", "Content", });
    internal_static_dossier_Tags_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_dossier_Tags_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Tags_descriptor,
        new java.lang.String[] { "IsConst", "IsDeprecated", "IsDict", "IsFinal", "IsInterface", "IsModule", "IsStruct", "IsDefault", "IsRecord", });
    internal_static_dossier_BaseProperty_descriptor =
      getDescriptor().getMessageTypes().get(6);
    internal_static_dossier_BaseProperty_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_BaseProperty_descriptor,
        new java.lang.String[] { "Name", "Source", "Description", "Deprecation", "Visibility", "Tags", "DefinedBy", "Overrides", "SpecifiedBy", "SeeAlso", });
    internal_static_dossier_Property_descriptor =
      getDescriptor().getMessageTypes().get(7);
    internal_static_dossier_Property_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Property_descriptor,
        new java.lang.String[] { "Base", "Type", });
    internal_static_dossier_Function_descriptor =
      getDescriptor().getMessageTypes().get(8);
    internal_static_dossier_Function_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Function_descriptor,
        new java.lang.String[] { "Base", "TemplateName", "IsConstructor", "Parameter", "Return", "Thrown", });
    internal_static_dossier_Function_Detail_descriptor =
      internal_static_dossier_Function_descriptor.getNestedTypes().get(0);
    internal_static_dossier_Function_Detail_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Function_Detail_descriptor,
        new java.lang.String[] { "Name", "Type", "Description", });
    internal_static_dossier_Enumeration_descriptor =
      getDescriptor().getMessageTypes().get(9);
    internal_static_dossier_Enumeration_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Enumeration_descriptor,
        new java.lang.String[] { "Type", "Value", "Visibility", });
    internal_static_dossier_Enumeration_Value_descriptor =
      internal_static_dossier_Enumeration_descriptor.getNestedTypes().get(0);
    internal_static_dossier_Enumeration_Value_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Enumeration_Value_descriptor,
        new java.lang.String[] { "Name", "Description", "Deprecation", });
    internal_static_dossier_Index_descriptor =
      getDescriptor().getMessageTypes().get(10);
    internal_static_dossier_Index_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_Index_descriptor,
        new java.lang.String[] { "Home", "IncludeTypes", "IncludeModules", "Link", "Timestamp", });
    internal_static_dossier_JsType_descriptor =
      getDescriptor().getMessageTypes().get(11);
    internal_static_dossier_JsType_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_JsType_descriptor,
        new java.lang.String[] { "Name", "Source", "Nested", "Description", "Tags", "Deprecation", "TypeDef", "Enumeration", "StaticFunction", "StaticProperty", "MainFunction", "Method", "Field", "ExtendedType", "ImplementedType", "Subtype", "Implementation", "CompilerConstant", "Parent", "AliasedType", "KnownAlias", "Filename", "QualifiedName", "ReexportedModule", });
    internal_static_dossier_JsType_TypeSummary_descriptor =
      internal_static_dossier_JsType_descriptor.getNestedTypes().get(0);
    internal_static_dossier_JsType_TypeSummary_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_JsType_TypeSummary_descriptor,
        new java.lang.String[] { "Name", "Href", "Summary", "Tags", });
    internal_static_dossier_JsType_NestedTypes_descriptor =
      internal_static_dossier_JsType_descriptor.getNestedTypes().get(1);
    internal_static_dossier_JsType_NestedTypes_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_JsType_NestedTypes_descriptor,
        new java.lang.String[] { "Class_", "Enum", "Interface", });
    internal_static_dossier_JsType_ParentLink_descriptor =
      internal_static_dossier_JsType_descriptor.getNestedTypes().get(2);
    internal_static_dossier_JsType_ParentLink_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_JsType_ParentLink_descriptor,
        new java.lang.String[] { "Type", "IsModule", });
    internal_static_dossier_HtmlRenderSpec_descriptor =
      getDescriptor().getMessageTypes().get(12);
    internal_static_dossier_HtmlRenderSpec_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_HtmlRenderSpec_descriptor,
        new java.lang.String[] { "Resources", "Title", "Content", "Index", });
    internal_static_dossier_SourceFileRenderSpec_descriptor =
      getDescriptor().getMessageTypes().get(13);
    internal_static_dossier_SourceFileRenderSpec_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_SourceFileRenderSpec_descriptor,
        new java.lang.String[] { "Resources", "File", "Index", });
    internal_static_dossier_JsTypeRenderSpec_descriptor =
      getDescriptor().getMessageTypes().get(14);
    internal_static_dossier_JsTypeRenderSpec_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_dossier_JsTypeRenderSpec_descriptor,
        new java.lang.String[] { "Type", "Resources", "Index", });
    com.google.protobuf.ExtensionRegistry registry =
        com.google.protobuf.ExtensionRegistry.newInstance();
    registry.add(com.github.jsdossier.proto.Options.sanitized);
    com.google.protobuf.Descriptors.FileDescriptor
        .internalUpdateFileDescriptor(descriptor, registry);
    com.github.jsdossier.proto.Options.getDescriptor();
    com.github.jsdossier.proto.Expression.getDescriptor();
  }

  // @@protoc_insertion_point(outer_class_scope)
}
