package akka

import akka.persistence.query.PersistenceQueries

package object persistence {

  final implicit class QueryPersistence(val p: Persistence) extends AnyVal {
    def queriesFor(queriesPluginId: String) =
      PersistenceQueries(p.system).queriesFor(queriesPluginId)
  }

}
