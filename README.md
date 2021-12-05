# scala-akka-actor-workshop
* referneces
  * https://blog.rockthejvm.com/stateful-stateless-actors/
  * https://www.baeldung.com/scala/typed-akka
  * https://github.com/Baeldung/scala-tutorials/tree/master/scala-akka/src/main/scala/com/baeldung/scala/akka/typed
  * https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html
  * https://doc.akka.io/docs/akka/2.5.32/typed-actors.html
  * https://stackoverflow.com/questions/23908132/using-future-callback-inside-akka-actor
  * https://www.baeldung.com/scala/discovering-actors-in-akka
  * https://github.com/Baeldung/scala-tutorials/tree/master/scala-akka
  * [Farewell Any - Unit, welcome Akka Typed! by Heiko Seeberger](https://www.youtube.com/watch?v=YW2wiBERKH8)
  * https://heikoseeberger.rocks/2017/10/02/2017-10-02-actor-model/
  * https://github.com/hseeberger/welcome-akka-typed

* Typed Actors are implemented using JDK Proxies which provide a pretty easy-worked API to intercept method calls.
* Just as with regular Akka Actors, Typed Actors process one call at a time.
* supervision?
* receptionist?
* messageAdapter
* dispatcher
    * https://doc.akka.io/docs/akka/current/typed/dispatchers.html
* state
    * https://blog.rockthejvm.com/pipe-pattern/
