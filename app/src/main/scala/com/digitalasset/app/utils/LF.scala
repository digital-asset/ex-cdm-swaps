package com.digitalasset.app.utils

import com.digitalasset.daml_lf_1_7.DamlLf1

import scala.collection.JavaConverters._

object LF {
  def getModuleName(lfPackage: DamlLf1.Package, module: DamlLf1.Module) : String = {
    if (module.hasNameDname)
      module.getNameDname.getSegmentsList.asScala.reduce((l, r) => l + "." + r)
    else {
      val segmentsInterned = lfPackage.getInternedDottedNames(module.getNameInternedDname).getSegmentsInternedStrList.asScala
      segmentsInterned.map(i => lfPackage.getInternedStrings(i)).reduce((l, r) => l + "." + r)
    }
  }

  def getTemplateName(lfPackage: DamlLf1.Package, template: DamlLf1.DefTemplate): String = {
    if (template.hasTyconDname)
      template.getTyconDname.getSegmentsList.asScala.reduce((l, r) => l + "." + r)
    else {
      val segmentsInterned = lfPackage.getInternedDottedNames(template.getTyconInternedDname).getSegmentsInternedStrList.asScala
      segmentsInterned.map(i => lfPackage.getInternedStrings(i)).reduce((l, r) => l + "." + r)
    }
  }

  def getDataTypeName(lfPackage: DamlLf1.Package, dataType: DamlLf1.DefDataType): String = {
    if (dataType.hasNameDname)
      dataType.getNameDname.getSegmentsList.asScala.reduce((l, r) => l + "." + r)
    else {
      val segmentsInterned = lfPackage.getInternedDottedNames(dataType.getNameInternedDname).getSegmentsInternedStrList.asScala
      segmentsInterned.map(i => lfPackage.getInternedStrings(i)).reduce((l, r) => l + "." + r)
    }
  }

  def getFieldName(lfPackage: DamlLf1.Package, field: DamlLf1.FieldWithType): String = {
    if (field.getFieldStr == null)
      field.getFieldStr
    else {
      lfPackage.getInternedStrings(field.getFieldInternedStr)
    }
  }

  def getTypeConName(lfPackage: DamlLf1.Package, typeCon: DamlLf1.TypeConName): String = {
    if (typeCon.hasNameDname)
      typeCon.getNameDname.getSegmentsList.asScala.reduce((l, r) => l + "." + r)
    else {
      val segmentsInterned = lfPackage.getInternedDottedNames(typeCon.getNameInternedDname).getSegmentsInternedStrList.asScala
      segmentsInterned.map(i => lfPackage.getInternedStrings(i)).reduce((l, r) => l + "." + r)
    }
  }
}
