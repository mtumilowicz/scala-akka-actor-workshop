# scala-akka-actor-workshop
* references
  * https://blog.rockthejvm.com/stateful-stateless-actors/
  * https://www.baeldung.com/scala/typed-akka
  * https://github.com/Baeldung/scala-tutorials/tree/master/scala-akka/src/main/scala/com/baeldung/scala/akka/typed
  * https://stackoverflow.com/questions/23908132/using-future-callback-inside-akka-actor
  * https://www.baeldung.com/scala/discovering-actors-in-akka
  * https://github.com/Baeldung/scala-tutorials/tree/master/scala-akka
  * [Farewell Any - Unit, welcome Akka Typed! by Heiko Seeberger](https://www.youtube.com/watch?v=YW2wiBERKH8)
  * https://heikoseeberger.rocks/2017/10/02/2017-10-02-actor-model/
  * https://github.com/hseeberger/welcome-akka-typed
  * [Networks and Types — the Future of Akka by Konrad ‘ktoso’ Malawski](https://www.youtube.com/watch?v=Qb9Cnii-34c)
  * https://stackoverflow.com/questions/64805718/in-akka-actor-typed-what-is-the-difference-between-behaviors-setup-and-behavior
  * [Scala Swarm 2017 | Konrad Malawski: Though this be madness yet there's method in't (keynote)](https://www.youtube.com/watch?v=G1V0Mg0j6-M)
  * [Farewell Any - Unit, welcome Akka Typed! by Heiko Seeberger](https://www.youtube.com/watch?v=YW2wiBERKH8)
  * [8 Akka anti-patterns you'd better be aware of by Manuel Bernhardt](https://www.youtube.com/watch?v=hr3UdktX-As)
  * https://doc.akka.io/docs/akka/2.5/typed/index.html
  * https://github.com/akka/akka/tree/main/akka-docs/src/test/scala/typed/tutorial_5

* antipattern
    * closing over mutable state in asynchronous calls
      * instead use queries for state inquiries (using the ask pattern)
    * if your actor system has no hierarchy you are missing the point
      * actor systems are designed to handle failures through hierarchy
    * supervision too often misunderstood / abused
      * use: try object, try-catch; supervision is for when things are going really bad
      like exploding - try restarting
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
* receptionist (actor discovery)
    * You register the specific actors that should be discoverable from other nodes in the local Receptionist instance
    *  The reply to such a Find request is a Listing, which contains a Set of actor references that are registered for the key
    * you can also subscribe to changes with the Receptionist.Subscribe message. It will send Listing messages to the subscriber when entries for a key are changed.
    * The primary scenario for using the receptionist is when an actor needs to be discovered by another actor but you are unable to put a reference to it in an incoming message
    * Behaviors.setup { ctx => ctx.system.receptionist ! Receptionist.Register(PingServiceKey, ctx.self)
    *
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
* behaviors always return new behaviors (state machine for the win)
  * everything is now finite state machine
* akka.actor.typed.scaladsl.Behaviors.setup vs akka.actor.typed.scaladsl.Behaviors.receive
  * Behaviors.setup defines behavior that does not wait to receive a message before executing. It simply executes its body immediately after an actor is spawned from it.
    * You must return a Behavior[T] from it, which might be a message-handling behavior. To do this, you would likely use Behaviors.receive or Behaviors.receiveMessage. (Or else Behaviors.stopped, if the actor is only required to do its work once once, and then disappear.)
    * Because it executes without waiting, Behaviors.setup is often used to define the behavior for the first actor in your system
      * Its initial behavior will likely be responsible for spawning the next brood of actors that your program will need, before adopting a new, message-handling behavior.
  * Behaviors.receive defines message-handling behavior.
    * You pass it a function that has parameters for both the actor context, and an argument containing the message
    * Actors created from this behavior will do nothing until they receive a message of a type that this behavior can handle.
* you don't have access to the actor state because ActorRef has no notion about internal state
  * test: behavior and interactions


* In summary, this is what happens when an actor receives a message:

  The actor adds the message to the end of a queue.
  If the actor was not scheduled for execution, it is marked as ready to execute.
  A (hidden) scheduler entity takes the actor and starts executing it.
  Actor picks the message from the front of the queue.
  Actor modifies internal state, sends messages to other actors.
  The actor is unscheduled.
* all actors have a common parent, the user guardian, which is defined and created when you start the ActorSystem
* creation of an actor returns a reference that is a valid URL. So, for example, if we create an actor named someActor from the user guardian with context.spawn(someBehavior, "someActor"), its reference will include the path /user/someActor
    * The easiest way to see the actor hierarchy in action is to print ActorRef instances
* In fact, before you create an actor in your code, Akka has already created three actors in the system.
    * / the so-called root guardian. This is the parent of all actors in the system, and the last one to stop when the system itself is terminated.
    * /user the guardian. This is the parent actor for all user created actors. Don’t let the name user confuse you, it has nothing to do with end users, nor with user handling. Every actor you create using the Akka library will have the constant path /user/ prepended to it.
    * /system the system guardian. Akka or other libraries built on top of Akka may create actors in the system namespace.

* The actor lifecycle
    * Whenever an actor is stopped, all of its children are recursively stopped too
    * To stop an actor, the recommended pattern is to return Behaviors.stopped()
    * The Akka actor API exposes some lifecycle signals, for example PostStop is sent just before the actor stops

* Failure handling
    * Whenever an actor fails (throws an exception or an unhandled exception bubbles out from onMessage) the failure information is propagated to the supervision strategy, which then decides how to handle the exception caused by the actor.
    * The supervision strategy is typically defined by the parent actor when it spawns a child actor. In this way, parents act as supervisors for their children.
    * The default supervisor strategy is to stop the child

* In the world of actors, protocols take the place of interfaces
* Akka provides the following behavior for message sends:

  At-most-once delivery, that is, no guaranteed delivery.
  Message ordering is maintained per sender, receiver pair.
* The delivery semantics provided by messaging subsystems typically fall into the following categories:

  At-most-once delivery — each message is delivered zero or one time; in more causal terms it means that messages can be lost, but are never duplicated.
  At-least-once delivery — potentially multiple attempts are made to deliver each message, until at least one succeeds; again, in more causal terms this means that messages can be duplicated but are never lost.
    * This adds the overhead of keeping the state at the sending end and having an acknowledgment mechanism at the receiving end
  Exactly-once delivery — each message is delivered exactly once to the recipient; the message can neither be lost nor be duplicated.
    * in addition to the overhead added by at-least-once delivery, it requires the state to be kept at the receiving end in order to filter out duplicate deliveries
* in Akka, for a given pair of actors, messages sent directly from the first to the second will not be received out-of-order.
* we need to be able to correlate requests and responses. Hence, we add one more field to our messages, so that an ID can be provided by the requester (we will add this code to our app in a later step):
    sealed trait DeviceMessage
    final case class ReadTemperature(requestId: Long, replyTo: ActorRef[RespondTemperature]) extends DeviceMessage
    final case class RespondTemperature(requestId: Long, value: Option[Double])
    * it is also a good idea to include an ID field to provide maximum flexibility


* Akka provides a Death Watch feature that allows an actor to watch another actor and be notified if the other actor is stopped.
    * Unlike supervision, watching is not limited to parent-child relationships, any actor can watch any other actor as long as it knows the ActorRef
    * After a watched actor stops, the watcher receives a Terminated(actorRef) signal which also contains the reference to the watched actor
    * The watcher can either handle this message explicitly or will fail with a DeathPactException. This latter is useful if the actor can no longer perform its own duties after the watched actor has been stopped.
    *

* Scheduling the query timeout
    *  Using Behaviors.withTimers and startSingleTimer to schedule a message that will be sent after a given delay.
    *
* Actor could be a query as well
    * example: https://doc.akka.io/docs/akka/2.5/typed/guide/tutorial_5.html

* dispatcher
    * context.spawn(yourBehavior, "DispatcherFromConfig", DispatcherSelector.fromConfig("your-dispatcher"))
* The root actor, also called the guardian actor, is created along with the ActorSystem. Messages sent to the actor system are directed to the root actor.
    * val system: ActorSystem[HelloWorldMain.Start] =
        ActorSystem(HelloWorldMain.main, "hello")
* Actors seldom have a response message from another actor as a part of their protocol (see adapted response)
    * Most often the sending actor does not, and should not, support receiving the response messages of another actor
    * In such cases we need to provide an ActorRef of the right type and adapt the response message to a type that the sending actor can handle
    * context.messageAdapter(rsp => WrappedBackendResponse(rsp))
* Behaviors.withTimers
* Fault Tolerance
    * When an actor throws an unexpected exception, a failure, while processing a message or during initialization, the actor will by default be stopped.
    * For failures it is useful to apply the “let it crash” philosophy: instead of mixing fine grained recovery and correction of internal state that may have become partially invalid because of the failure with the business logic we move that responsibility somewhere else. For many cases the resolution can then be to “crash” the actor, and start a new one, with a fresh state that we know is valid.
* supervision
    * Supervision allows you to declaratively describe what should happen when a certain type of exceptions are thrown inside an actor
        * Behaviors.supervise(behavior).onFailure[IllegalStateException](SupervisorStrategy.restart)
    * Child actors are often started in a setup block that is run again when the parent actor is restarted. The child actors are stopped to avoid resource leaks of creating new child actors each time the parent is restarted
    * In some scenarios it may be useful to push the decision about what to do on a failure upwards in the Actor hierarchy and let the parent actor handle what should happen on failures
        * For a parent to be notified when a child is terminated it has to watch the child
        * If the child was stopped because of a failure the ChildFailed signal will be received which will contain the cause
        * ChildFailed extends Terminated so if your use case does not need to distinguish between stopping and failing you can handle both cases with the Terminated signal
* Behaviors as Finite state machines
    * FSM event becomes the type of the message Actor supports
    * Each state becomes a distinct behavior
* testing
    * class ScalaTestIntegrationExampleSpec extends ScalaTestWithActorTestKit with WordSpecLike
    * val pinger = spawn(echoActor, "ping")
      val probe = createTestProbe[Pong]()
      pinger ! Ping("hello", probe.ref)
      probe.expectMessage(Pong("hello"))
    * mocking
        * mock behaviors that accept and possibly respond to messages in the same way the other actor would do but without executing any actual logic
        * In addition to this it can also be useful to observe those interactions to assert that the component under test did send the expected messages
        * Behaviors.monitor(monitor: ActorRef[T], behavior: Behavior[T])
            * Behavior decorator that copies all received message to the designated monitor before invoking the wrapped behavior
    * Controlling the scheduler
        * a scheduler where you can manually, explicitly advance the clock
        val manualTime: ManualTime = ManualTime()
        manualTime.expectNoMessageFor(9.millis, probe)
        manualTime.timePasses(2.millis)