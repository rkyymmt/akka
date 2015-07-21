導入
============

Akka HTTP モジュールは、akka-actor と akka-stream 上に実装されている、HTTP サーバのフルスタックと HTTP クライアントスタックです。
これは、Web フレームワークではなく、より一般的な HTTP ベースのサービスを提供・利用するためのツールキットです。ブラウザとの相互作用も、
Akka HTTP のスコープ内ですが、主な焦点ではありません。

Akka HTTP は、とてもオープンな設計になっており、「同じことを行う」ためのいくつかの異なるAPIレベルを提供しており、開発者は、実装時に
自分のアプリケーションに最適な抽象化レベルのAPIを選ぶことができます。
これは、高レベルAPIの利用時に何かトラブルを抱えた場合でも、低レベルAPIによってそれを乗り越えられる可能性があることを意味します。
これは、より多くの柔軟性を提供していますが、同時に、多くのアプリケーションコードを記述する必要が出てきます。

Akka HTTP は複数のモジュールで構成されています:

akka-http-core
  完全な、ほとんどが低レベル、 HTTP のサーバ/クライアント側の実装（ WebSocket を含む）

akka-http
  （アン）マーシャリング、圧縮・展開、および、サーバ側の HTTP ベースな API のための強力な DSL などの高レベル機能

akka-http-testkit
  テストハーネスとサーバ側サービス実装の検証のためのユーティリティ群

akka-http-spray-json
  spray-json_ による、事前定義された、カスタム型⇔JSON間（デ）シリアライズのためのグルーコード

akka-http-xml
  scala-xml_ による、事前定義された、カスタム型⇔XML間（デ）シリアライズのためのグルーコード

.. _spray-json: https://github.com/spray/spray-json
.. _scala-xml: https://github.com/scala/scala-xml
