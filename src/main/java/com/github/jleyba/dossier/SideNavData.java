package com.github.jleyba.dossier;

import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;

import java.nio.file.Path;

class SideNavData {

  private final SoyListData types = new SoyListData();
  private final SoyListData files = new SoyListData();

  void addType(String name, Path href, boolean isInterface) {
    types.add(new SoyMapData(
        "name", name,
        "href", href.toString(),
        "isInterface", isInterface));
  }

  void addFile(String name, Path href) {
    files.add(new SoyMapData("name", name, "href", href.toString()));
  }

  SoyMapData toSoy() {
    return new SoyMapData("types", types, "files", files);
  }
}
