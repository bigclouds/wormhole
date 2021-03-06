/*-
 * <<
 * wormhole
 * ==
 * Copyright (C) 2016 - 2017 EDP
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */


package edp.rider.rest.persistence.dal

import edp.rider.common.RiderLogger
import edp.rider.module.DbModule._
import edp.rider.rest.persistence.base.BaseDalImpl
import edp.rider.rest.persistence.entities._
import edp.rider.rest.util.CommonUtils._
import edp.rider.rest.util.NamespaceUtils._
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{CanBeQueryCondition, TableQuery}
import edp.rider.common.DbPermission._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

class RelProjectNsDal(namespaceTable: TableQuery[NamespaceTable],
                      databaseTable: TableQuery[NsDatabaseTable],
                      instanceTable: TableQuery[InstanceTable],
                      projectTable: TableQuery[ProjectTable],
                      relProjectNsTable: TableQuery[RelProjectNsTable],
                      streamTopicTable: TableQuery[StreamInTopicTable]) extends BaseDalImpl[RelProjectNsTable, RelProjectNs](relProjectNsTable) with RiderLogger {

  def getNsProjectName: Future[mutable.HashMap[Long, ArrayBuffer[String]]] = {
    val nsProjectSeq = db.run((projectTable join relProjectNsTable on (_.id === _.projectId))
      .map {
        case (project, rel) => (rel.nsId, project.name) <> (NamespaceProjectName.tupled, NamespaceProjectName.unapply)
      }.result).mapTo[Seq[NamespaceProjectName]]
    nsProjectSeq.map[mutable.HashMap[Long, ArrayBuffer[String]]] {
      val nsProjectMap = mutable.HashMap.empty[Long, ArrayBuffer[String]]
      nsProjectSeq =>
        nsProjectSeq.foreach(nsProject => {
          if (nsProjectMap.contains(nsProject.nsId))
            nsProjectMap(nsProject.nsId) = nsProjectMap(nsProject.nsId) += nsProject.name
          else
            nsProjectMap(nsProject.nsId) = ArrayBuffer(nsProject.name)
        })
        nsProjectMap
    }
  }

  def getNsIdsByProjectId(id: Long): Future[String] = super.findByFilter(_.projectId === id)
    .map[String] {
    relProjectNsSeq =>
      relProjectNsSeq.map(_.nsId).mkString(",")
  }

  def getNsByProjectId(projectIdOpt: Option[Long] = None, nsIdOpt: Option[Long] = None): Future[Seq[NamespaceTopic]] = {
    val relProjectNsQuery = projectIdOpt match {
      case Some(id) => relProjectNsTable.filter(_.projectId === id)
      case None => relProjectNsTable
    }
    val nsQuery = nsIdOpt match {
      case Some(id) => namespaceTable.filter(_.id === id)
      case None => namespaceTable
    }
    db.run(((nsQuery.filter(_.active === true) join relProjectNsQuery on (_.id === _.nsId))
      join databaseTable on (_._1.nsDatabaseId === _.id))
      .map {
        case ((ns, rel), database) => (ns.id, ns.nsSys, ns.nsInstance, ns.nsDatabase, ns.nsTable, ns.nsVersion, ns.nsDbpar, ns.nsTablepar, ns.permission, ns.keys,
          ns.nsDatabaseId, ns.nsInstanceId, ns.active, ns.createTime, ns.createBy, ns.updateTime, ns.updateBy, database.nsDatabase) <> (NamespaceTopic.tupled, NamespaceTopic.unapply)
      }.distinct.result).map[Seq[NamespaceTopic]] {
      nsSeq =>
        nsSeq.map {
          ns =>
            val topic = if (ns.topic == ns.nsDatabase) "" else ns.topic
            NamespaceTopic(ns.id, ns.nsSys, ns.nsInstance, ns.nsDatabase, ns.nsTable, ns.nsVersion, ns.nsDbpar, ns.nsTablepar, ns.permission, ns.keys,
              ns.nsDatabaseId, ns.nsInstanceId, ns.active, ns.createTime, ns.createBy, ns.updateTime, ns.updateBy, topic)
        }
    }
  }

  def getSourceNamespaceByProjectId(projectId: Long, streamId: Long, nsSys: String) =
    db.run(((namespaceTable.filter(ns => ns.nsSys === nsSys && ns.active === true) join
      relProjectNsTable.filter(rel => rel.projectId === projectId && rel.active === true) on (_.id === _.nsId))
      join streamTopicTable.filter(_.streamId === streamId) on (_._1.nsDatabaseId === _.nsDatabaseId))
      .map {
        case ((ns, rel), topic) => (ns.id, ns.nsSys, ns.nsInstance, ns.nsDatabase, ns.nsTable, ns.nsVersion, ns.nsDbpar, ns.nsTablepar, ns.permission, ns.keys,
          ns.nsDatabaseId, ns.nsInstanceId, ns.active, ns.createTime, ns.createBy, ns.updateTime, ns.updateBy) <> (Namespace.tupled, Namespace.unapply)
      }.result).mapTo[Seq[Namespace]]

  def getSinkNamespaceByProjectId(id: Long, nsSys: String) =
    db.run(((namespaceTable.filter(ns => ns.nsSys === nsSys && ns.active === true && ns.permission === READWRITE.toString) join
      relProjectNsTable.filter(rel => rel.projectId === id && rel.active === true) on (_.id === _.nsId)) join instanceTable on (_._1.nsInstanceId === _.id))
      .map {
        case ((ns, rel), instance) => (ns.id, ns.nsSys, ns.nsInstance, ns.nsDatabase, ns.nsTable, ns.nsVersion, ns.nsDbpar, ns.nsTablepar, ns.permission, ns.keys,
          ns.nsDatabaseId, ns.nsInstanceId, ns.active, ns.createTime, ns.createBy, ns.updateTime, ns.updateBy, instance.nsSys) <> (NamespaceTemp.tupled, NamespaceTemp.unapply)
      }.result).map[Seq[Namespace]] {
      nsSeq => nsSeq.filter(ns => ns.nsSys == ns.nsInstanceSys)
        .map(ns => Namespace(ns.id, ns.nsSys, ns.nsInstance, ns.nsDatabase, ns.nsTable, ns.nsVersion, ns.nsDbpar, ns.nsTablepar, ns.permission, ns.keys,
          ns.nsDatabaseId, ns.nsInstanceId, ns.active, ns.createTime, ns.createBy, ns.updateTime, ns.updateBy))
    }

  def getTransNamespaceByProjectId(id: Long, nsSys: String) =
    db.run((((namespaceTable.filter(ns => ns.nsSys === nsSys && ns.active === true) join relProjectNsTable.filter(rel => rel.projectId === id && rel.active === true) on (_.id === _.nsId))
      join databaseTable on (_._1.nsDatabaseId === _.id)) join instanceTable on (_._2.nsInstanceId === _.id))
      .map {
        case (((ns, rel), database), instance) => (instance, database, ns.nsSys) <> (TransNamespaceTemp.tupled, TransNamespaceTemp.unapply)
      }.distinct.result).map[Seq[TransNamespace]] {
      nsSeq =>
        nsSeq.filter(ns => ns.nsSys == ns.instance.nsSys)
          .map(ns => {
            val url = getConnUrl(ns.instance, ns.db)
            TransNamespace(ns.nsSys, ns.instance.nsInstance, ns.db.nsDatabase, url, ns.db.user, ns.db.pwd)
          })
    }

  def getInstanceByProjectId(projectId: Long, nsSys: String): Future[Seq[Instance]] = {
    db.run((relProjectNsTable.filter(rel => rel.projectId === projectId && rel.active === true) join namespaceTable.filter(_.nsSys === nsSys) on (_.nsId === _.id) join instanceTable.filter(_.nsSys === "kafka") on (_._2.nsInstanceId === _.id)).map {
      case ((rel, ns), instance) => instance
    }.distinct.result).mapTo[Seq[Instance]]
  }

  def getNamespaceAdmin[C: CanBeQueryCondition](f: (NamespaceTable) => C): Future[Seq[NamespaceAdmin]] = {
    try {
      val nsProjectMap = Await.result(getNsProjectName, minTimeOut)
      db.run((namespaceTable.withFilter(f) join databaseTable on (_.nsDatabaseId === _.id))
        .map {
          case (ns, database) => (ns.id, ns.nsSys, ns.nsInstance, ns.nsDatabase, ns.nsTable, ns.nsVersion, ns.nsDbpar, ns.nsTablepar, ns.permission, ns.keys,
            ns.nsDatabaseId, ns.nsInstanceId, ns.active, ns.createTime, ns.createBy, ns.updateTime, ns.updateBy, database.nsDatabase) <> (NamespaceTopic.tupled, NamespaceTopic.unapply)
        }.distinct.result)
        .map[Seq[NamespaceAdmin]] {
        namespaces =>
          val nsProjectSeq = new ArrayBuffer[NamespaceAdmin]
          namespaces.foreach(ns => {
            if (nsProjectMap.contains(ns.id))
              nsProjectSeq += NamespaceAdmin(ns.id, ns.nsSys, ns.nsInstance, ns.nsDatabase, ns.nsTable, ns.nsVersion, ns.nsDbpar, ns.nsTablepar,
                ns.permission, ns.keys, ns.nsDatabaseId, ns.nsInstanceId, ns.active, ns.createTime, ns.createBy, ns.updateTime, ns.updateBy,
                nsProjectMap(ns.id).mkString(","), ns.topic)
            else
              nsProjectSeq += NamespaceAdmin(ns.id, ns.nsSys, ns.nsInstance, ns.nsDatabase, ns.nsTable, ns.nsVersion, ns.nsDbpar, ns.nsTablepar,
                ns.permission, ns.keys, ns.nsDatabaseId, ns.nsInstanceId, ns.active, ns.createTime, ns.createBy, ns.updateTime, ns.updateBy, "", ns.topic)
          })
          nsProjectSeq
      }
    } catch {
      case ex: Exception =>
        riderLogger.error(s"admin refresh namespaces failed", ex)
        throw ex
    }

  }

  def isAvailable(projectId: Long, nsId: Long): Boolean = {
    try {
      val rel = Await.result(super.findByFilter(rel => rel.nsId === nsId && rel.projectId === projectId), minTimeOut)
      if (rel.isEmpty) false else true
    } catch {
      case ex: Exception =>
        riderLogger.error(s"check project id $projectId, namespace id $nsId permission failed", ex)
        throw ex
    }
  }
}
