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


* may be worthwhile to first look on the previous workshops
    * scala basics: https://github.com/mtumilowicz/scala213-functional-programming-collections-workshop
    * actors basics: https://github.com/mtumilowicz/kotlin-functional-programming-actors-workshop
* goals of this workshops
    * introduction to actors in akka context
        * ActorSystem, ActorContext, ActorRef, Dispatchers, Behavior
        * supervision and failure handling
        * lifecycle
## introduction
* two ultimate goals during software development
    1. complexity has to stay as low as possible
    1. resources must be used efficiently while you scale the application
* if we want to scale, the programming model has to be asynchronous
    * allows components to continue working while others haven’t responded yet
    * actor model chooses the abstraction of sending and receiving messages
* actors vs synchronous approach
    |                                   |actors   |synchronous approach   |
    |---                                |---      |---|
    |scaling                            |send and receive messages, no shared mutable state, immutable log of events    |mix of threads, shared mutable state in a CRUD database, and web service RPC calls   |
    |providing interactive information  |event-driven: push when the event occurs                                       |poll for current information    |
    |scaling out on the network         |asynchronous messaging, nonblocking I/O                                        |synchronous RPC, blocking I/O       |
    |handling failures                  |let it crash, isolate failure, and continue without failing parts              |handle all exceptions; only continue if everything works   |
* actors: decoupled on three axes
    * space/location
        * actor gives no guarantee and has no expectation about where another actor is located
    * time
        * actor gives no guarantee and has no expectation about when its work will be done
    * interface
        * nothing is shared between actors
        * information is passed in messages
    * if system coupled on all three axes can only exist on one runtime and will fail completely if
    one of its components fails

## constructs
* ActorSystem
    * actors can create other actors, but who creates the first one?
        * first thing that every Akka application does is create an ActorSystem
    * typically one ActorSystem per JVM process
    * hierarchical group of actors which share common configuration
        * similar to URL path structure
            * ActorPath - unique path to an actor that shows the creation path up through the actor tree to the
            root actor
            * every actor has a name (unique per level in the hierarchy)
                * generated automatically, but it’s a good idea to name all your actors
    * entry point for creating or looking up actors
    * is a heavyweight structure that will allocate threads, so create one per logical application
* ActorRef
    * ActorSystem returns an address (ActorRef) to the created top-level actor instead of the actor itself
        * makes sense - actor could be on another server
    * can be used to send messages to the actor
    * message -> ActorRef -> Mailbox -> Actor
* Dispatchers
    * in the real world, dispatchers are the communication coordinators responsible for receiving and passing
    messages
        * for example, emergency services 911 - the dispatchers are the people responsible for taking
        in the call and passing on the messages to the other departments like the medical, fire station,
        police, etc
    * the main engine of an ActorSystem
    * responsible for selecting an actor and it’s messages and assigning them the CPU
        * actors are lightweight because they run on top of dispatchers
            * actors aren’t proportional to the number of threads
            * 2.7 million actors vs 4096 threads can fit in 1 GB
    * actors are invoked at some point by a dispatcher
        * dispatcher pushes the messages in the mailbox through the actors
    * configuring custom dispatcher
        ```
        context.spawn(yourBehavior, "DispatcherFromConfig", DispatcherSelector.fromConfig("your-dispatcher"))
        ```
        and `application.conf`
        ```
        my-dispatcher {
            # Dispatcher is the name of the event-based dispatcher
            type = Dispatcher
            # What kind of ExecutionService to use
            executor = "fork-join-executor"
            # Configuration for the fork join pool
            fork-join-executor {
                # Min number of threads to cap factor-based parallelism number to
                parallelism-min = 2
                # Parallelism (threads) ... ceil(available processors * factor)
                parallelism-factor = 2.0
                # Max number of threads to cap factor-based parallelism number to
                parallelism-max = 10
            }
            # Throughput defines the maximum number of messages to be
            # processed per actor before the thread jumps to the next actor.
            # Set to 1 for as fair as possible.
            throughput = 100
        }
        ```
* Actor = Behavior + Context (in which behavior is executed)
    * ActorContext
        * provides access to the Actor’s own identity ("self"), list of child actors, etc
        * `context.spawn()` creates a child actor
        * `system.spawn()` creates top level
    * Behavior
        * defines how actor reacts to the messages that it receives
        ```
        def apply(): Behavior[SayHello] =
            Behaviors.setup { context => // typically used as the outer most behavior when spawning an actor
                val greeter = context.spawn(HelloWorld(), "greeter")

                Behaviors.receiveMessage { message => // useful for when the context is already accessible by other means, like being wrapped in an [[setup]] or similar
                    val replyTo = context.spawn(HelloWorldBot(max = 3), message.name)
                    greeter ! HelloWorld.Greet(message.name, replyTo)
                    Behaviors.same
                }
            }
        ```

## supervision
* actor is the supervisor of the created child actor
* supervision hierarchy is fixed for the lifetime of a child actor
* actors that are most likely to crash should be as low down the hierarchy as possible
    * when a fault occurs in the top level of the actor system, it could restart all the
    top-level actors or even shut down the actor system.
* The supervisor doesn’t try to fix the actor or its state.
    * It simply renders a judgment on how to recover, and then triggers the corresponding strategy
* mailbox for a crashed actor is suspended until the supervisor will decide what to do with the exception
* The supervisor has four options when deciding what to do with the actor:
    * Restart
        * The actor must be re-created from its Props
        * After it’s restarted (or rebooted, if you will), the actor will continue to process messages.
        * Since the rest of the application uses an ActorRef to communicate with the actor, the new
        actor instance will automatically get the next messages.
    * Resume
        * The same actor instance should continue to process messages;
        * the crash is ignored.
    * Stop
        * The actor must be terminated
        * It will no longer take part in processing messages.
    * Escalate
        * The supervisor doesn’t know what to do with it and escalates the problem to its parent,
        which is also a supervisor

## failure
* actor provides two separate flows
    * one for normal logic - consists of actors that handle normal messages
    * one for fault recovery logic - consists of actors that monitor the actors in the normal flow
* instead of catching exceptions in an actor, we’ll just let the actor crash
    * no error handling or fault recovery logic in actor
* in most cases, you don’t want to reprocess a message, because it probably caused the error in the first place
    * Akka chooses not to provide the failing message to the mailbox again after a restart

## actor lifecycle
* actors do not stop automatically when no longer referenced
    * every actor that is created must also explicitly be destroyed
    * stopping a parent will also recursively stop all the child
* actor is automatically started by Akka when it’s created
* lifecycle: Started -> Stopped -> Terminated
    * started - can be restarted to reset the internal state of the actor
    * stopped - is disconnected from its ActorRef
        * ActorRef is redirected to the deadLettersActorRef of the actor system (special ActorRef that receives all
        messages that are sent to dead actors)
    * terminated - can’t process messages anymore and will be eventually garbage collected
            * if the supervisor decides to stop the actor
            * if the stop method is used to stop the actor
            * if a PoisonPill message is sent to the actor
                * indirectly causes the stop method to be called
* there are several hooks, which are called when the events happen to indicate a lifecycle change
    ![alt text](img/hooks.png)
    * restart doesn’t stop the crashed actor in the same way as the stop methods
        * during restart - fresh actor instance is connected to the same ActorRef the crashed actor
        was using before the fault
        * stopped actor is disconnected from its ActorRef and redirected to the deadLettersActorRef










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
