package kamon.system.custom

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{ Paths, Files }

import akka.actor.{ Props, Actor, ActorSystem }
import akka.event.{ Logging, LoggingAdapter }
import kamon.Kamon
import kamon.metric._
import kamon.metric.instrument.InstrumentFactory
import kamon.system.custom.ContextSwitchesUpdater.UpdateContextSwitches
import org.hyperic.sigar.Sigar
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.duration.FiniteDuration

class ContextSwitchesMetrics(pid: Long, log: LoggingAdapter, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val perProcessVoluntary = histogram("context-switches-process-voluntary")
  val perProcessNonVoluntary = histogram("context-switches-process-non-voluntary")
  val global = histogram("context-switches-global")

  def update(): Unit = {
    def contextSwitchesByProcess(pid: Long): (Long, Long) = {
      val filename = s"/proc/$pid/status"
      var voluntaryContextSwitches = 0L
      var nonVoluntaryContextSwitches = 0L

      try {
        for (line ← Files.readAllLines(Paths.get(filename), StandardCharsets.US_ASCII).asScala.toList) {
          if (line.startsWith("voluntary_ctxt_switches")) {
            voluntaryContextSwitches = line.substring(line.indexOf(":") + 1).trim.toLong
          }
          if (line.startsWith("nonvoluntary_ctxt_switches")) {
            nonVoluntaryContextSwitches = line.substring(line.indexOf(":") + 1).trim.toLong
          }
        }
      } catch {
        case ex: IOException ⇒ log.error("Error trying to read [{}]", filename)
      }
      (voluntaryContextSwitches, nonVoluntaryContextSwitches)
    }

    def contextSwitches: Long = {
      val filename = "/proc/stat"
      var contextSwitches = 0L

      try {
        for (line ← Files.readAllLines(Paths.get(filename), StandardCharsets.US_ASCII).asScala.toList) {
          if (line.startsWith("rcs")) {
            contextSwitches = line.substring(line.indexOf(" ") + 1).toLong
          }
        }
      } catch {
        case ex: IOException ⇒ log.error("Error trying to read [{}]", filename)
      }
      contextSwitches
    }

    val (voluntary, nonVoluntary) = contextSwitchesByProcess(pid)
    perProcessVoluntary.record(voluntary)
    perProcessNonVoluntary.record(nonVoluntary)
    global.record(contextSwitches)
  }
}

object ContextSwitchesMetrics {

  def register(system: ActorSystem, refreshInterval: FiniteDuration): ContextSwitchesMetrics = {
    val metricsExtension = Kamon(Metrics)(system)
    val log = Logging(system, "ContextSwitchesMetrics")
    val pid = (new Sigar).getPid

    val instrumentFactory = metricsExtension.instrumentFactory("system-metric")
    metricsExtension.register(Entity("context-switches", "system-metric"), new ContextSwitchesMetrics(pid, log, instrumentFactory)).recorder
  }
}

class ContextSwitchesUpdater(csm: ContextSwitchesMetrics, refreshInterval: FiniteDuration) extends Actor {
  val schedule = context.system.scheduler.schedule(refreshInterval, refreshInterval, self, UpdateContextSwitches)(context.dispatcher)

  def receive = {
    case UpdateContextSwitches ⇒ csm.update()
  }

  override def postStop(): Unit = {
    schedule.cancel()
    super.postStop()
  }
}

object ContextSwitchesUpdater {
  case object UpdateContextSwitches

  def props(csm: ContextSwitchesMetrics, refreshInterval: FiniteDuration): Props =
    Props(new ContextSwitchesUpdater(csm, refreshInterval))
}