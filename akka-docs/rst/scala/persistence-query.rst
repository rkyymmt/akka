.. _persistence-query-scala:

#################
Persistence Query
#################

Akka persistence query complements :ref:`persistence-scala` by providing an universal asynchronous stream based
query interface that various journal plugins can implement in order to expose their query capabilities.

The most typical use case of persistence query is implementing the so-called query side (also known as "read side"),
in the popular CQRS architecture pattern - in which the writing side of the application (e.g. implemented using akka
persistence) is completely separated from the query side which can be implemented using akka persistence query.

While queries may be performed directly on the same datastore, it is also a very common pattern to use the queries
to create projections of the write-side's events and store them into a separate journal which is optimised for more
complex queries - this migration of data, possibly including some transformation along the way, can be easily implemented
using akka persistence query as well - by pulling out of one Journal and storing into another one.

.. warning::

  This module is marked as **“experimental”** as of its introduction in Akka 2.4.0. We will continue to
  improve this API based on our users’ feedback, which implies that while we try to keep incompatible
  changes to a minimum the binary compatibility guarantee for maintenance releases does not apply to the
  contents of the ``akka.persistence.query`` package.

Design overview
===============

Akka persistence query is purposely designed to be a very loose API.
This is in order to keep the provided APIs general enough for each journal implementation to be able to expose its best
features, e.g. a SQL journal can use complex SQL queries or if a journal is able to subscribe to a live event stream
this should also be possible to expose the same API - a typed stream of events.

**Each read journal must explicitly document which types of queries it supports.**
Refer to the your journal's plugins documentation for details on which queries and semantics it supports.

While Akka Persistence Query does not provide actual implementations of ReadJournals, it defines a number of pre-defined
query types for the most common query scenarios, that most journals are likely to implement (however they are not required to).

Read Journals
=============

In order to issue queries one has to first obtain an instance of a ``ReadJournal``.
Read journals are implemented as `Community plugins`_, each targeting a specific datastore (for example Cassandra or JDBC
databases). For example, given a library that provides a ``akka.persistence.query.noop-read-journal`` obtaining the related
journal is as simple as:

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#basic-usage

Journal implementers are encouraged to put this identified in a variable known to the user, such that one can access it via
``journalFor(NoopJournal.identifier)``, however this is not enforced.

Read journal implementations are available as `Community plugins`_.


Predefined queries
------------------
Akka persistence query comes with a number of ``Query`` objects built in and suggests journal implementors to implement
them according to the semantics described below. It is important to notice that while these query types are very common
a journal is not obliged to implement all of them - for example because in a given journal such query would be
significantly inefficient.

.. note::
  Refer to the documentation of the ``ReadJournal`` plugin you are using for a specific list of supported query types.
  For example, Journal plugins should document their stream completion strategies.

The predefined queries are:

``AllPersistenceIds`` which is designed to allow users to subscribe to a stream of all persistent ids in the system.
By default this stream should be assumed to be a "live" stream, which means that the journal should keep emitting new
persistence ids as they come into the system:

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#all-persistence-ids-live

If your usage does not require a live stream, you can disable refreshing by using *hints*, providing the built-in
``NoRefresh`` hint to the query:

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#all-persistence-ids-snap

``EventsByPersistenceId`` is a query equivalent to replaying an :ref:`PersistentActor <event-sourcing>`,
however, since it is a stream it is possible to keep it alive and watch for additional incoming events persisted by the
persistent actor identified by the given ``persistenceId``. Most journal will have to revert to polling in order to achieve
this, which can be configured using the ``RefreshInterval`` query hint:

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#events-by-persistent-id-refresh

``EventsByTag`` allows querying events regardles of which ``persistenceId`` they are associated with. This query is hard to
implement in some journals or may need some additional preparation of the used data store to be executed efficiently,
please refer to your read journal plugin's documentation to find out if and how it is supported. The goal of this query
is to allow querying for all events which are "tagged" with a specific tag - again, how exactly this is implemented
depends on the used journal.

.. note::
  A very important thing to keep in mind when using queries spanning multiple persistenceIds, such as ``EventsByTag``
  is that the order of events at which the events appear in the stream rarely is guaranteed (or stable between materializations).

  Journals *may* choose to opt for strict ordering of the events, and should then document explicitly what kind of ordering
  guarantee they provide - for example "*ordered by timestamp ascending, independently of persistenceId*" is easy to achieve
  on relational databases, yet may be hard to implement efficiently on plain key-value datastores.

In the example below we query all events which have been tagged (we assume this was performed by the write-side using an
:ref:`EventAdapter <event-adapter-scala>`, or that the journal is smart enough that it can figure out what we mean by this
tag - for example if the journal stored the events as json it may try to find those with the field ``tag`` set to this value etc.).

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#events-by-tag

As you can see, we can use all the usual stream combinators available from `Akka Streams`_ on the resulting query stream,
including for example taking the first 10 and cancelling the stream. It is worth pointing out that the built-in ``EventsByTag``
query has an optionally supported offset parameter (of type ``Long``) which the journals can use to implement resumable-streams.
For example a journal may be able to use a WHERE clause to begin the read starting from a specific row, or in a datastore
that is able to order events by insertion time it could treat the Long as a timestamp and select only older events.
Again, specific sapabilities are specific to the journal you are using, so you have to


Materialized values of queries
------------------------------
Journals are able to provide additional information related to a query by exposing `materialized values`_,
which are a feature of `Akka Streams`_ that allows to expose additional values at stream materialization time.

More advanced query journals may use this technique to expose information about the character of the materialized
stream, for example if it's finite or infinite, strictly ordered or not ordered at all. The materialized value type
is defined as the ``M`` type parameter of a query (``Query[T,M]``), which allows journals to provide users with their
specialised query object, as demonstrated in the sample below:

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#materialized-query-metadata

.. _materialized values: http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0-RC3/scala/stream-quickstart.html#Materialized_values
.. _Akka Streams: http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0-RC3/scala.html
.. _Community plugins: http://akka.io/community/

Performance and denormalization
===============================
When building systems using :ref:`event-sourcing` and CQRS (`Command & Query Responsibility Segragation`_) techniques
it is tremendously important to realise that the write-side has completely different needs from the read-side,
and separating those concerns into datastores that are optimised for either side makes it possible to offer the best
expirience for the write and read sides independently.

For example in add bidding systems, it is important to "take the write" and respond to the bidder that we have accepted
the bid as soon as possible, which means that write-throughput is of highest importance for the write-side – often this
means that data stores which are able to scale to accomodate these requirements have a less expressive query side.

On the other hand the same application may have some complex statistics view or we may have analists working with the data
to figure out best bidding strategies and trends – this often requires some kind of expressive query capabilities like
for example SQL or writing Spark jobs to analyse the data. To offer data more efficiently to those kinds of

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#projection-into-different-store

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#projection-into-different-store-run

.. _Command & Query Responsibility Segragation: https://msdn.microsoft.com/en-us/library/jj554200.aspx

Dependencies
============

Akka persistence query is a separate jar file. Make sure that you have the following dependency in your project::

  "com.typesafe.akka" %% "akka-persistence-query-experimental" % "@version@" @crossString@


.. _read-journal-plugin-api-scala:

Query plugins
=============

Various read journal plugin implementations are what makes this module so powerful and elastic in its use.
Since different data stores provide different query capabilities journal plugins **must extensively document**
their exposed semantics as well as handled query scenarios.

Read Journal plugin API
-----------------------

Journals *MUST* return a *failed* ``Source`` if they are unable to execute the passed in query.
For example if the user accidentally passed in an ``SqlQuery()`` to a key-value journal.

Below is a simple journal implementations:

.. includecode:: code/docs/persistence/query/PersistenceQueryDocSpec.scala#my-read-journal

And the ``EventsByTag`` could be backed by such an Actor for example:

.. includecode:: code/docs/persistence/query/MyEventsByTagPublisher.scala#events-by-tag-publisher

More journal example implementations
------------------------------------

In order to help implementers get get started with implementing read journals a number of reference implementaions
have been prepared, each highlighting a specific style a journal might need to be implemented in:

* TODO LINK HERE – when the backing data store is unable to push events, nor does it expose an reactive streams interface,
  yet has rich query capabilities (like an SQL database for example)
* TODO LINK HERE – when a `Reactive Streams`_ adapter or driver is available for the datastore, yet it is not able to handle
  polling by itself. For example when using `Slick 3`_ along side with a typical SQL database.
* TODO LINK HERE – when the backing datastore already has a fully "reactive push/pull" adapter implemented, for example
  such exist for Kafka (see the `Reactive Kafka`_ project by Krzysztof Ciesielski for details).

.. _Reactive Kafka: https://github.com/softwaremill/reactive-kafka
.. _Reactive Streams: http://reactive-streams.org
.. _Slick 3: http://slick.typesafe.com/


Plugin TCK
----------

TODO, not available yet.


