/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.elasticsearch.restlastic.dsl

trait IndexDsl extends DslCommons {
  case class CreateIndex(settings: Option[IndexSetting] = None) extends RootObject {
    val _settings = "settings"

    override def toJson: Map[String, Any] = if (settings.nonEmpty) {
      Map(_settings -> settings.get.toJson)
    } else Map()
  }

  case class Document(id: String, data: Map[String, Any]) extends EsOperation with RootObject {
    override def toJson: Map[String, Any] = data
  }

  // Op types are lowercased to avoid a conflict with the Index case class
  sealed trait OperationType { val jsonStr: String }
  case object index extends  OperationType {
    override val jsonStr: String = "index"
  }
  case object create extends OperationType {
    override val jsonStr: String = "create"
  }
  case object update extends OperationType {
    override val jsonStr: String = "update"
  }

  case class Bulk(operations: Seq[BulkOperation]) extends EsOperation with RootObject {
    override def toJson: Map[String, Any] = throw new UnsupportedOperationException
    override lazy val toJsonStr = operations.map(_.toJsonStr).mkString("", "\n", "\n")
  }

  case class BulkOperation(operation: OperationType, location: Option[(Index, Type)], document: Document) extends EsOperation {
    import EsOperation.compactJson
    override def toJson: Map[String, Any] = throw new UnsupportedOperationException
    def toJsonStr: String = {
      val doc = operation match {
        case `update` =>
          Document(document.id, Map("doc"->document.data) ++ Map("detect_noop" -> true, "doc_as_upsert" -> true))
        case _ => document
      }
      val jsonObjects = Map(operation.jsonStr ->
        (Map("_id" -> doc.id) ++ location.map { case (index, tpe) => Map("_index" -> index.name, "_type" -> tpe.name)}.getOrElse(Map()))
      )
      s"${compactJson(jsonObjects)}\n${doc.toJsonStr}"
    }
  }

  case class IndexSetting(numberOfShards: Int, numberOfReplicas: Int,
                          analyzerMapping: Analysis,
                          refreshInterval: Int = 1)
    extends EsOperation {

    val _shards = "number_of_shards"
    val _replicas = "number_of_replicas"
    val _analysis = "analysis"
    val _interval = "refresh_interval"

    override def toJson: Map[String, Any] = Map(
      _shards -> numberOfShards,
      _replicas -> numberOfReplicas,
      _analysis -> analyzerMapping.toJson,
      _interval -> s"${refreshInterval}s")
  }

  sealed trait Analysis extends EsOperation

  case class Analyzers(analyzers: AnalyzerArray, filters: FilterArray)
    extends Analysis with EsOperation {

    override def toJson: Map[String, Any] = analyzers.toJson ++ filters.toJson
  }

  case class Analyzer(name: Name, tokenizer: FieldType, filter: FieldType*)
    extends Analysis with EsOperation {

    val _analyzer = "analyzer"
    val _tokenizer = "tokenizer"
    val _filter = "filter"

    override def toJson: Map[String, Any] = Map(
      _analyzer -> Map(
        name.name -> Map(
          _tokenizer -> tokenizer.rep,
          _filter -> filter.map(_.rep)
        )
      )
    )
  }

  sealed trait Filter extends EsOperation{
    val name: Name
  }

  case class EdgeNGramFilter(name: Name, minGram: Int, maxGram: Int) extends Filter with EsOperation {

    val _filter = "filter"
    val _type = "type"
    val _edgeNGram ="edge_ngram"
    val _minGram = "min_gram"
    val _maxGramp = "max_gram"

    override def toJson: Map[String, Any] = Map(
      _filter -> Map(
        name.name -> Map(
          _type -> _edgeNGram,
          _minGram -> minGram,
          _maxGramp -> maxGram
        )
      )
    )
  }

  case class AnalyzerArray(analyzers: Analyzer*) extends EsOperation {
    val _analyzer = "analyzer"

    override def toJson: Map[String, Any] = Map(
      _analyzer -> analyzers.map(_
        .toJson.getOrElse(_analyzer, Map())
        .asInstanceOf[Map[String, Any]]).reduce(_ ++ _)
    )
  }

  case class FilterArray(filters: Filter*) extends EsOperation {
    val _filter = "filter"

    override def toJson: Map[String, Any] = Map(
      _filter -> filters.map(_
        .toJson.getOrElse(_filter, Map())
        .asInstanceOf[Map[String, Any]]).reduce(_ ++ _)
    )
  }

  case object Keyword extends FieldType {
    val rep = "keyword"
  }

  case object Lowercase extends FieldType {
    val rep = "lowercase"
  }

  case object EdgeNGram extends FieldType {
    val rep = "edgengram"
  }
}


