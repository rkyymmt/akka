package akka.persistence.query

import java.util.concurrent.atomic.AtomicReference

import akka.actor._
import akka.event.Logging
import akka.stream.scaladsl.Source

import scala.annotation.tailrec
import scala.util.Try

/**
 * Persistence extension for queries.
 */
object PersistenceQuery extends ExtensionId[PersistenceQuery] with ExtensionIdProvider {
  /**
   * Java API.
   */
  override def get(system: ActorSystem): PersistenceQuery = super.get(system)

  def createExtension(system: ExtendedActorSystem): PersistenceQuery = new PersistenceQuery(system)

  def lookup() = PersistenceQuery

  /** INTERNAL API. */
  private[persistence] case class PluginHolder(plugin: ReadJournal) extends Extension

}

class PersistenceQuery(system: ExtendedActorSystem) extends Extension {
  import PersistenceQuery._

  private val log = Logging(system, getClass)

  /** Discovered query plugins. */
  private val readJournalPluginExtensionIds = new AtomicReference[Map[String, ExtensionId[PluginHolder]]](Map.empty)

  /**
   * Absolute configuration path to the read journal configuration entry.
   */
  @tailrec final def readJournalFor(readJournalPluginId: String): ReadJournal = {
    val configPath = readJournalPluginId
    val extensionIdMap = readJournalPluginExtensionIds.get
    extensionIdMap.get(configPath) match {
      case Some(extensionId) ⇒
        extensionId(system).plugin
      case None ⇒
        val extensionId = new ExtensionId[PluginHolder] {
          override def createExtension(system: ExtendedActorSystem): PluginHolder =
            PluginHolder(createPlugin(configPath))
        }
        readJournalPluginExtensionIds.compareAndSet(extensionIdMap, extensionIdMap.updated(configPath, extensionId))
        readJournalFor(readJournalPluginId) // Recursive invocation.
    }
  }

  private def createPlugin(configPath: String): ReadJournal = {
    require(!isEmpty(configPath) && system.settings.config.hasPath(configPath),
      s"'reference.conf' is missing persistence read journal plugin config path: '${configPath}'")
    val pluginActorName = configPath
    val pluginConfig = system.settings.config.getConfig(configPath)
    val pluginClassName = pluginConfig.getString("class")
    log.debug(s"Create plugin: ${pluginActorName} ${pluginClassName}")
    val pluginClass = system.dynamicAccess.getClassFor[AnyRef](pluginClassName).get

    Try(system.dynamicAccess.createInstanceFor[ReadJournal](pluginClass, (classOf[ExtendedActorSystem], system) :: Nil))
      .getOrElse(system.dynamicAccess.createInstanceFor[ReadJournal](pluginClass, Nil)).get
  }

  /** Check for default or missing identity. */
  private def isEmpty(text: String) = text == null || text.length == 0
}

