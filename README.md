[![Build Status](https://app.travis-ci.com/mtumilowicz/scala-akka-actor-workshop.svg?branch=master)](https://app.travis-ci.com/mtumilowicz/scala-akka-actor-workshop)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

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
  * https://dzone.com/articles/akka-dispatcher-all-that-you-need-to-know
  * https://blog.rockthejvm.com/pipe-pattern/

## preface
* goals of this workshops
    * introduction to actors in akka context
        * ActorSystem, ActorContext, ActorRef, Dispatchers, Behavior
        * supervision and failure handling
        * lifecycle
* workshop plan
    * bank: implement debiting account
    * device: implement `onRespondTemperature` in `DeviceGroupQuery`
* may be worthwhile to first look on the previous workshops
    * scala basics: https://github.com/mtumilowicz/scala213-functional-programming-collections-workshop
    * actors basics: https://github.com/mtumilowicz/kotlin-functional-programming-actors-workshop

## introduction
* two ultimate goals during software development
    1. complexity has to stay as low as possible
    1. resources must be used efficiently while you scale the application
* if we want to scale, the programming model has to be asynchronous
    * allows components to continue working while others haven’t responded yet
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
    * digression: system coupled on all three axes can only exist on one runtime and will fail completely if
    one of its components fails

## constructs
* `ActorSystem`
    * actors can create other actors, but who creates the first one?
        * first thing that every Akka application does is create an `ActorSystem`
    * typically one `ActorSystem` per JVM process
        * heavyweight structure that will allocate threads, so create one per logical application
    * is a hierarchical group of actors which share common configuration
        * if your actor system has no hierarchy you are missing the point
        * similar to URL path structure
            * `ActorPath` - creation path from the root actor
                * example: `akka://bank/user/Account-1#-747509914`
            * every actor has a name (unique per level in the hierarchy)
    * `val system: ActorSystem[BankOperation] = ActorSystem(Bank(), "bank")`
    * are implemented using JDK Proxies
        * JDK Proxies provides a pretty easy-worked API to intercept method calls
* `ActorRef`
    * represents the address of an actor in Akka
    * actor could be on another server
    * used to send messages to the actor
    * you don't have access to the actor state because ActorRef has no notion about internal state
        * good for testing: focus on behavior and interactions
    * message -> `ActorRef` -> Mailbox -> Actor
    * `val accountRef: ActorRef[AccountOperation] = context.spawn(account.behavior(),s"Account-$rawId")`
    * `Receptionist` (actor discovery)
        * you register the specific actors that should be discoverable in the local Receptionist instance
            * `Behaviors.setup { ctx => ctx.system.receptionist ! Receptionist.Register(PingServiceKey, ctx.self)`
        * reply to a `Find` request is a `Listing`: contains a Set[ActorRef] registered for the key
            ```
            context.ask(
              context.system.receptionist,
              Receptionist.Find(serviceKey)
            ) {
              case Success(listing) => ...
              case Failure(exception) => ...
            }
            ```
        * you can also subscribe to changes - Receptionist will notify subscriber when entries for a key are changed
* `Mailbox`
    * each actor in Akka has a Mailbox, this is where the messages are enqueued before being processed by the actor
    * `context.spawn(childBehavior, "bounded-mailbox-child", MailboxSelector.bounded(100))`
* Dispatchers
    * are the communication coordinators responsible for receiving and passing messages
        * example: emergency services 911
            * dispatchers pass on the messages to the departments: medical, fire station, police, etc
    * the main engine of an `ActorSystem`
    * responsible for selecting an actor and it’s messages and assigning them the CPU
        * actors are lightweight because they run on top of dispatchers
            * actors aren’t proportional to the number of threads
            * 2.7 million actors vs 4096 threads can fit in 1 GB
    * actors are invoked at some point by a dispatcher
    * `context.spawn(yourBehavior, "DispatcherFromConfig", DispatcherSelector.fromConfig("your-dispatcher"))`
* Actor = Behavior + Context (in which behavior is executed)
    * Actor can perform the following actions when processing a message:
        * send a finite number of messages to other Actors it knows
            * `actorRef ! message`
            * Akka provides the following behavior for message sends
                * at-most-once delivery, that is, no guaranteed delivery
                    * digression about delivery semantics
                        * at-most-once delivery
                            * each message is delivered zero or one time
                            * messages can be lost, but never duplicated
                        * at-least-once delivery
                            * potentially multiple attempts until at least one succeeds
                            * messages can be duplicated but are never lost
                            * adds overhead of keeping the state at the sending end and having an acknowledgment
                            mechanism at the receiving end
                        * exactly-once delivery
                            * the message can neither be lost nor be duplicated
                          * overhead: at-least-once delivery + it requires the state to be kept at the receiving
                          end in order to filter out duplicate deliveries
                * for a given pair of actors, messages sent directly from the first to the second will not be
                received out-of-order
        * create a finite number of Actors
            * `context.spawn(account.behavior(),s"Account-$rawId")
        * designate the behavior for the next message
            ```
            private def behavior(implicit state: AccountState): Behavior[AccountOperation] = Behaviors.receiveMessage {
              case command: AccountCommand => handleCommand(command)
              case query: AccountQuery => handleQuery(query)
            }
            ```
* Behavior
    * can be seen as Finite state machines
        * FSM event = type of the message Actor supports
        * Each state = a distinct behavior
    * behaviors always return new behaviors
        * everything is now finite state machine
    * conceptually: behavior is a function from a typed message to the next behavior
        ```
        abstract class ExtensibleBehavior[T] extends Behavior[T] {
          def receiveMessage(ctx: ActorContext[T], msg: T): Behavior[T]
          def receiveSignal(ctx: ActorContext[T], msg: Signal): Behavior[T]
        }
        ```
    * `Behaviors.setup { context => ... }` vs `Behaviors.receive { (context, message) => }`
        * `Behaviors.receive` defines message-handling behavior
            * Actors created from this behavior will do nothing until they receive a message
        * `Behaviors.setup` defines behavior that does not wait to receive a message before executing
            * simply executes its body immediately after an actor is spawned from it
            * used to define the behavior for the first actor in your system
            * typically `Behaviors.receive` or `Behaviors.receiveMessage` used inside
    * digression: closing over mutable state in asynchronous calls
        ```
        asyncRetrievePhoneNumberFromDb(name).onComplete { ...modify state }
        ```
        * Future callbacks, as well as transformations, are evaluated on some thread (may or may not be the
        one that’s handling the message)
            * actor encapsulation is broken
        * use `def pipeToSelf[Value](future: Future[Value])(mapResult: Try[Value] => T)`
            * sends the result of the given Future to this Actor ("self"), after adapted it with the given function

## interaction Patterns
* Fire and Forget
    * is no way to know if the message was received, the processing succeeded or failed
    * `actorRef ! message`
* Request-Adapted Response
    ```
    case class Request(requestId: Long, query: String, replyTo: ActorRef[Response])
    case class Response(requestId: Long, result: String)
    ```
    * sending actor does not, and should not, support receiving the response messages of another actor
        ```
        context.messageAdapter {
        case type1 => adaptedType1
        ...
        }
        ```
    * it is also a good idea to include an ID field to provide maximum flexibility
        * we need to be able to correlate requests and responses

## supervision
* allows you to declaratively describe what should happen when a certain type of exceptions are thrown inside an actor
    * `Behaviors.supervise(behavior).onFailure[IllegalStateException](SupervisorStrategy.restart)`
        * returns `Behavior[T]`
* actor is the supervisor of the created child actor
* supervision hierarchy is fixed for the lifetime of a child actor
* actors that are most likely to crash should be as low down the hierarchy as possible
* supervisor doesn’t try to fix the actor or its state
    * renders a judgment on how to recover, and then triggers the corresponding strategy
* four options when deciding what to do with the actor
    * Restart
        * since the rest of the application uses an `ActorRef` to communicate with the actor, the new
        actor instance will automatically get the next messages
    * Resume
        * crash is ignored
    * Stop
        * it will no longer take part in processing messages
        * default strategy
    * Escalate
        * supervisor doesn’t know what to do with it and escalates the problem to its parent
* is too often misunderstood / abused
    * use: try object, try-catch
    * supervision is for when things are going really bad like exploding - try restarting
* Death Watch feature
    * allows an actor to watch another actor and be notified if the other actor is stopped
        * signal: Terminated(actorRef)
    * vs supervision: watching is not limited to parent-child relationships

## failure
* actor provides two separate flows
    * one for normal logic - consists of actors that handle normal messages
    * one for fault recovery logic - consists of actors that monitor the actors in the normal flow
* in most cases, you don’t want to reprocess a message, because it probably caused the error in the first place
    * Akka chooses not to provide the failing message to the mailbox again after a restart
* it is useful to apply the "let it crash" philosophy
    * instead of mixing fine grained recovery and correction of internal state that may have
    become partially invalid because of the failure with the business logic we move that
    responsibility somewhere else
    * for many cases the resolution can then be to "crash" the actor, and start a new one, with a fresh state that
    we know is valid
      * event sourced from database

## actor lifecycle
* every actor that is created must also explicitly be destroyed
* stopping a parent will also recursively stop all the child
* actor is automatically started by Akka when it’s created
* lifecycle: Started -> Stopped -> Terminated
    * started - can be restarted to reset the internal state of the actor
    * stopped - is disconnected from its `ActorRef`
        * `ActorRef` is redirected to the `deadLettersActorRef` of the actor system
            * special `ActorRef` that receives all messages that are sent to dead actors
    * terminated - can’t process messages anymore and will be eventually garbage collected
        * if the supervisor decides to stop the actor
        * if the stop method is used to stop the actor
        * if a `PoisonPill` message is sent to the actor
            * indirectly causes the stop method to be called
* there are some lifecycle signals, for example `PostStop` is sent just before the actor stops
    * `Behaviors.receiveSignal { case (context, PostStop) => ... }`
    * `receiveSignal` takes care of a few special messages like PreRestart or Terminated
    which of course are not covered by the message type `T`

## scheduling
* use `Behaviors.withTimers { timers => timers.startSingleTimer(msg, 1.second) }`
    * `def startSingleTimer(msg: T, delay: FiniteDuration)`
        * start a timer that will send msg once to the self actor after the given delay
    * 'def startTimerAtFixedRate(msg: T, interval: FiniteDuration)'

## testing
* `class ScalaTestIntegrationExampleSpec extends ScalaTestWithActorTestKit`
* `val actor = spawn(behavior, "name")`
* verifying reply
    ```
    val probe = createTestProbe[MessageType]()
    actor ! Message(payload, probe.ref) // replyTo = probe.ref
    probe.expectMessage(RepliedForMessage(...))
    ```
* mocking
    * mock behavior: accept and respond to messages in the same way the other actor would do
    but without executing any actual logic
    * it can also be useful to observe interactions to assert that the component under test did
    send the expected messages
        * `Behaviors.monitor(monitor: ActorRef[T], behavior: Behavior[T])`
            * it copies all received message to the designated monitor before invoking the wrapped behavior
    * example
        ```
        val mockedBehavior = Behaviors.receiveMessage[Message] { msg => ... }
        val monitorProbe = testKit.createTestProbe[Message]()
        val mockedActor = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))

        val classWithMockedDependencies = new UnderTestClass(..., mockedActor)
        // executing the logic of classWithMockedDependencies

        probe.expectMessageType[Message] // verifying correct messages to actor
        ```
* you can manually, explicitly advance the clock
    ```
    val manualTime: ManualTime = ManualTime()
    manualTime.expectNoMessageFor(9.millis, probe)
    manualTime.timePasses(2.millis)
    ```
