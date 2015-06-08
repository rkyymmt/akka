.. _persistence-query-scala:

#################
Persistence Query
#################

Akka persistence query complements Akka persistence by providing

as a natural extension and API for the "read side" of the popular CQRS architecture pattern

.. warning::

  This module is marked as **“experimental”** as of its introduction in Akka 2.3.0. We will continue to
  improve this API based on our users’ feedback, which implies that while we try to keep incompatible
  changes to a minimum the binary compatibility guarantee for maintenance releases does not apply to the
  contents of the ``akka.persistence.query`` package.


Dependencies
============

Akka persistence query is a separate jar file. Make sure that you have the following dependency in your project::

  "com.typesafe.akka" %% "akka-persistence-query-experimental" % "@version@" @crossString@


Architecture
============

Akka Persistence Query is purposely designed to be a very loose API. This is in order to keep the provided APIs
general enough for each journal implementation to be able to expose its best features, e.g. a SQL journal can
use complex SQL queries or if a journal is able to subscribe to a live event stream this should also be possible to
expose the same API - a typed stream of events.

Each read journal must explicitly explain which types of queries it supports.
While Akka Persistence Query provides a few pre-defined query types for the most common query scenarios,
it is not mendatory for journals to implement them.

The predefined queries are:

* *AllPersistenceIds* - TODO...
* *EventsByPersistenceId* - TODO...

Read journal implementations are available as `Community plugins`_.

.. _Community plugins: http://akka.io/community/

Querying
========

Query Hints
-----------

.. _read-journal-plugin-api-scala:

Read Journal Plugin API
=======================

Implementing plugins in Java
----------------------------

Since Akka Streams provide