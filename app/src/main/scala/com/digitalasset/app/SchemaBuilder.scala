package com.digitalasset.app

import com.digitalasset.daml_lf_1_7.DamlLf1

import scala.collection.JavaConverters._

object SchemaBuilder {
  type Schema = Map[String, List[Field]]

  case class Field
  (
    name: String,
    cardinality: Cardinality.Cardinality,
    `type`: String,
    withMeta: Boolean,
    withReference: Boolean
  )

  object Cardinality extends Enumeration {
    type Cardinality = Value
    val OPTIONAL, ONEOF, LISTOF = Value
  }

  // Load simple schema for a list of modules (required to decode jsons)
  def build(ledgerClient: LedgerClient, moduleNames: List[String]): SchemaBuilder.Schema = {
    val packages = ledgerClient.getPackages().values.toList
    moduleNames.map(moduleName => {
      val schemas = packages.flatMap(p => new SchemaBuilder(p).build(moduleName))
      schemas.size match {
        case 0 => throw new IllegalArgumentException("No schema found for module " + moduleName)
        case 1 => schemas.head
        case _ => throw new NotImplementedError("Multiple schemas.")
      }
    }).flatMap(_.toList).toMap
  }
}

class SchemaBuilder(lfPackage: DamlLf1.Package) {

  // Map daml lf types to a simple schema (required to decode jsons)
  def build(moduleName: String): Option[SchemaBuilder.Schema] = {
    val module = lfPackage.getModulesList.asScala.toList.find(x => utils.LF.getModuleName(lfPackage, x) == moduleName)
    module.map { m =>
      m.getDataTypesList.asScala.toList.map{dataType =>
        val name = utils.LF.getDataTypeName(lfPackage, dataType)
        val fields = dataType.getRecord.getFieldsList.asScala.toList.map(f => mapType(utils.LF.getFieldName(lfPackage, f), f.getType))
        (name, fields)
      }.toMap
    }
  }

  private def mapType(name: String, damlLfType: DamlLf1.Type): SchemaBuilder.Field = {
    damlLfType.getPrim.getPrim.getValueDescriptor.getName match {
      case "INT64"        => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimInt64", false, false)
      case "DECIMAL"      => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimDecimal", false, false)
      case "NUMERIC"      => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimNumeric", false, false)
      case "TEXT"         => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimText", false, false)
      case "BOOL"         => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimBool", false, false)
      case "DATE"         => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimDate", false, false)
      case "TIMESTAMP"    => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimTimestamp", false, false)
      case "PARTY"        => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimParty", false, false)
      case "CONTRACT_ID"  => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimContractId", false, false)
      case "ARROW"        => SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, "PrimArrow", false, false)
      case "OPTIONAL"     =>
        val subType = mapType(name, damlLfType.getPrim.getArgsList.get(0))
        if (subType.cardinality == SchemaBuilder.Cardinality.ONEOF) subType.copy(cardinality = SchemaBuilder.Cardinality.OPTIONAL)
        else throw new NotImplementedError("Nested optionals or lists not supported.")
      case "LIST"         =>
        val subType = mapType(name, damlLfType.getPrim.getArgsList.get(0))
        if (subType.cardinality == SchemaBuilder.Cardinality.ONEOF) subType.copy(cardinality = SchemaBuilder.Cardinality.LISTOF)
        else throw new NotImplementedError("Nested optionals or lists not supported.")
      case "UNIT"         =>
        if (damlLfType.getCon.getArgsCount > 0) {
          val t = utils.LF.getTypeConName(lfPackage, damlLfType.getCon.getTycon)
          t match {
            case "ReferenceWithMeta" =>
              val subType = mapType(name, damlLfType.getCon.getArgs(0))
              if (subType.cardinality == SchemaBuilder.Cardinality.ONEOF) subType.copy(withReference = true)
              else throw new NotImplementedError("Nested FieldWithMeta types not supported.")
            case "BasicReferenceWithMeta" =>
              val subType = mapType(name, damlLfType.getCon.getArgs(0))
              if (subType.cardinality == SchemaBuilder.Cardinality.ONEOF) subType.copy(withReference = true)
              else throw new NotImplementedError("Nested FieldWithMeta types not supported.")
            case "FieldWithMeta" =>
              val subType = mapType(name, damlLfType.getCon.getArgs(0))
              if (subType.cardinality == SchemaBuilder.Cardinality.ONEOF) subType.copy(withMeta = true)
              else throw new NotImplementedError("Nested FieldWithMeta types not supported.")
            case _ => throw new NotImplementedError("Types with arguments not supported.")
          }
        }
        else SchemaBuilder.Field(name, SchemaBuilder.Cardinality.ONEOF, utils.LF.getTypeConName(lfPackage, damlLfType.getCon.getTycon), false, false)
      case other          => throw new Exception("PrimType " + other + " not supported.")
    }
  }
}
