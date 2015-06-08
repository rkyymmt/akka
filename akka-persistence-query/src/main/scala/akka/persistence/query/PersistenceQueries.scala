package akka.persistence.query

import java.util.concurrent.atomic.AtomicReference

import akka.actor._
import akka.event.Logging
import akka.stream.scaladsl.Source

import scala.annotation.tailrec
import scala.util.Try

/**
 * Persistence extension.
 */
object PersistenceQueries extends ExtensionId[PersistenceQueries] with ExtensionIdProvider {
  /**
   * Java API.
   */
  override def get(system: ActorSystem): PersistenceQueries = super.get(system)

  def createExtension(system: ExtendedActorSystem): PersistenceQueries = new PersistenceQueries(system)

  def lookup() = PersistenceQueries

  /** INTERNAL API. */
  private[persistence] case class PluginHolder(plugin: EventQueries) extends Extension

}

// TODO these things need better names
class PersistenceQueries(system: ExtendedActorSystem) extends Extension {
  import PersistenceQueries._

  val log = Logging(system, getClass)

  /** Discovered query plugins. */
  private val queryPluginExtensionIds = new AtomicReference[Map[String, ExtensionId[PluginHolder]]](Map.empty)

  /**
   * TODO
   */
  @tailrec final def queriesFor(queriesPluginId: String): EventQueries = {
    val configPath = queriesPluginId
    val extensionIdMap = queryPluginExtensionIds.get
    extensionIdMap.get(configPath) match {
      case Some(extensionId) ⇒
        extensionId(system).plugin
      case None ⇒
        val extensionId = new ExtensionId[PluginHolder] {
          override def createExtension(system: ExtendedActorSystem): PluginHolder =
            PluginHolder(createPlugin(configPath))
        }
        queryPluginExtensionIds.compareAndSet(extensionIdMap, extensionIdMap.updated(configPath, extensionId))
        queriesFor(queriesPluginId) // Recursive invocation.
    }
  }

  private def createPlugin(configPath: String): EventQueries = {
    require(!isEmpty(configPath) && system.settings.config.hasPath(configPath),
      s"'reference.conf' is missing persistence plugin config path: '${configPath}'")
    val pluginActorName = configPath
    val pluginConfig = system.settings.config.getConfig(configPath)
    val pluginClassName = pluginConfig.getString("class")
    log.debug(s"Create plugin: ${pluginActorName} ${pluginClassName}")
    val pluginClass = system.dynamicAccess.getClassFor[AnyRef](pluginClassName).get
    //    val pluginInjectConfig = if (pluginConfig.hasPath("inject-config")) pluginConfig.getBoolean("inject-config") else false
    //    val pluginDispatcherId = if (pluginConfig.hasPath("plugin-dispatcher")) pluginConfig.getString("plugin-dispatcher") else dispatcherSelector(pluginClass)
    //    val pluginActorArgs = if (pluginInjectConfig) List(pluginConfig) else Nil
    //    val pluginActorProps = Props(Deploy.local, pluginClass, pluginActorArgs)
    //    system.systemActorOf(pluginActorProps, pluginActorName)

    Try(system.dynamicAccess.createInstanceFor[EventQueries](pluginClass, (classOf[ExtendedActorSystem], system) :: Nil))
      .getOrElse(system.dynamicAccess.createInstanceFor[EventQueries](pluginClass, Nil)).get
  }

  /** Check for default or missing identity. */
  private def isEmpty(text: String) = text == null || text.length == 0
}

