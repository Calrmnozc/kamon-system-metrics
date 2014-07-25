/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */
package kamon.system

import java.lang.management.ManagementFactory

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metric.Metrics
import kamon.metrics._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object SystemMetrics extends ExtensionId[SystemMetricsExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = SystemMetrics

  override def createExtension(system: ExtendedActorSystem): SystemMetricsExtension = new SystemMetricsExtension(system)
}

class SystemMetricsExtension(private val system: ExtendedActorSystem) extends Kamon.Extension {
  import kamon.system.SystemMetricsExtension._

  val log = Logging(system, classOf[SystemMetricsExtension])

  log.info(s"Starting the Kamon(SystemMetrics) extension")

  val systemMetricsExtension = Kamon(Metrics)(system)

  //JVM Metrics
  systemMetricsExtension.register(HeapMetrics(Heap), HeapMetrics.Factory)
  garbageCollectors.map { gc ⇒ systemMetricsExtension.register(GCMetrics(gc.getName), GCMetrics.Factory(gc)) }

  //System Metrics
  system.actorOf(SystemMetricsCollector.props(1 second), "system-metrics-collector")
}

object SystemMetricsExtension {
  val CPU = "cpu"
  val ProcessCPU = "process-cpu"
  val Network = "network"
  val Memory = "memory"
  val Heap = "heap"

  val garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid)

  def toKB(value:Long):Long = value / 1024
  def toMB(value:Long):Long = value / 1024 / 1024
  def toLong(value:Double):Long = (value * 100L) toLong
}
