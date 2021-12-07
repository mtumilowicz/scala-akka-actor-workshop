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

* Carl Hewitt explains the actor as the “fundamental unit of computation embodying processing (do things), storage (state) and communications” where “everything is an actor” and “one actor is no actor, they come in systems”.
* Collaboration between actors happens via asynchronous communication, allowing for decoupling of sender and receiver: each actor has an address and can send messages to actors for which it knows their address without being blocked in its processing.
* When an actor processes a message it has received – which is performed by its so called behavior – it can do the following, concurrently and in any order:

  create new actors
  send messages to known actors
  designate the behavior for the next message
* Typed Actors are implemented using JDK Proxies which provide a pretty easy-worked API to intercept method calls.
* Just as with regular Akka Actors, Typed Actors process one call at a time.
* supervision?
* receptionist?
* messageAdapter
* dispatcher
    * https://doc.akka.io/docs/akka/current/typed/dispatchers.html
* state
    * https://blog.rockthejvm.com/pipe-pattern/

## untyped
trait Actor {
  def receive: PartialFunction[Any, Unit] // actually type alias `Receive`
}
* Akka actors are untyped: we can send any message to an actor even though it most probably only handles specific ones – the compiler is not able to help us
trait ActorRef { // actually represents the address of an actor in Akka
  def !(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit
}
* The ! operator allows us to send a message of type Any to the actor identified by the respective address
trait ActorContext {
  def become(behavior: PartialFunction[Any, Unit]): Unit // actually type alias `Receive`
}
* It returns Unit which means that designating a different behavior for the next message must happen as a side effect.

## typed
* But Akka Typed also resembles the actual definition of the actor model much closer than Akka by dropping the Actor trait and conceptually treating behavior as a function from a typed message to the next behavior
abstract class ExtensibleBehavior[T] extends Behavior[T] {
  def receiveMessage(ctx: ActorContext[T], msg: T): Behavior[T]
  def receiveSignal(ctx: ActorContext[T], msg: Signal): Behavior[T]
}
* receiveSignal takes care of a few special messages like PreRestart or Terminated which of course are not covered by the message type T
