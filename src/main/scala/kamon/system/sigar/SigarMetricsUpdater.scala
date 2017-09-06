/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.system.sigar

import kamon.Kamon
import kamon.system.SystemMetrics
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

class SigarMetricsUpdater(logger: Logger) {
  val sigar = new Sigar

  val sigarMetrics = List(
    CpuMetrics.register(sigar),
    FileSystemMetrics.register(sigar),
    LoadAverageMetrics.register(sigar),
    MemoryMetrics.register(sigar),
    NetworkMetrics.register(sigar),
    ProcessCpuMetrics.register(sigar),
    ULimitMetrics.register(sigar)).flatten

  def updateMetrics(): Unit = {
    sigarMetrics.foreach(_.update())
  }

}


trait SigarMetric {
  def update(): Unit
}

object SigarSafeRunner {
  private val errorLogged = scala.collection.mutable.Set[String]()

  def runSafe[T](thunk: ⇒ T, defaultValue: ⇒ T, error: String, logger: Logger): T = {
    try thunk catch {
      case e: Exception ⇒
        if (!errorLogged.contains(error)) {
          errorLogged += error
          logger.warn(s"Couldn't get the metric [${error}]. Due to [${e.getMessage}]")
        }
        defaultValue
    }
  }
}

abstract class SigarMetricBuilder(metricName: String) {
  private val filterName = SystemMetrics.FilterName
  private val logger = SystemMetrics.logger

  def metricPrefix: String = s"$filterName.$metricName"

  def register(sigar: Sigar): Option[SigarMetric] = {
    if (Kamon.filter(filterName, metricName))
      Some(build(sigar, metricPrefix, logger))
    else
      None
  }

  def build(sigar: Sigar, metricPrefix: String, logger: Logger): SigarMetric
}
