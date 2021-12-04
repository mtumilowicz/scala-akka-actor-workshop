package bld

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.util.Success

case class Addition(id: String) {

  val serviceKey = ServiceKey[Operations.Calculate](id)

  def behavior(): Behavior[Operations.Calculate] = Behaviors.setup { context =>
    Behaviors.receiveMessage[Operations.Calculate]{
      message =>
        context.log.info(s"$id: ${message.a} + ${message.b} = ${message.a + message.b}")
        Behaviors.same
    }
  }
}

object Operations {
  final case class Setup()
  final case class Calculate(a: Int, b: Int)

  def apply(): Behavior[Setup] = Behaviors.setup{ context =>
    Behaviors.receiveMessage[Setup]{ _ =>
      context.log.info("Registering operations...")

      val a = Addition("1")
      val addition = context.spawnAnonymous(a.behavior())
      context.system.receptionist ! Receptionist.Register(a.serviceKey, addition)
      context.log.info("Registered addition")

      val a2 = Addition("2")
      val multiplication = context.spawnAnonymous(a2.behavior())
      context.system.receptionist ! Receptionist.Register(a2.serviceKey, multiplication)
      context.log.info("Registered multiplication")

      Behaviors.same
    }
  }
}

object RegistrationListener {
  def apply(): Behavior[Receptionist.Listing] = Behaviors.setup{ context =>
    context.system.receptionist ! Receptionist.Subscribe(Addition("1").serviceKey, context.self)
    context.system.receptionist ! Receptionist.Subscribe(Addition("2").serviceKey, context.self)

    Behaviors.receiveMessage[Receptionist.Listing] {
      listing =>
        val key = listing.key
        listing.getServiceInstances(key).forEach{ reference =>
          context.log.info(s"Registered: ${reference.path}")
        }
        Behaviors.same
    }
  }
}

object Calculator {
  sealed trait Command
  final case class Calculate(operation: String, a: Int, b: Int) extends Command
  final case class CalculateUsingOperation(operation: ActorRef[Operations.Calculate], a: Int, b: Int) extends Command

  private def getKey: String => ServiceKey[Operations.Calculate] = (operationName: String) => {
    val x: Any = Addition("1").serviceKey

    operationName match {
      case "addition" => Addition("1").serviceKey
      case "multiplication" => Addition("2").serviceKey
    }
  }

  def apply(): Behavior[Command] =
    Behaviors.setup{ context =>
      context.spawnAnonymous(RegistrationListener())
      context.spawn(Operations(), "operations") ! Operations.Setup()

      implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

      Behaviors.receiveMessagePartial[Command] {
        case Calculate(operation, a, b) => {
          context.log.info(s"Looking for implementation of ${operation}")
          val operationKey = getKey(operation)
          context.ask(
            context.system.receptionist,
            Receptionist.Find(operationKey)
          ) {
            case Success(listing) => {
              val instances = listing.serviceInstances(operationKey)
              val firstImplementation = instances.iterator.next()
              CalculateUsingOperation(firstImplementation, a, b)
            }
          }

          Behaviors.same
        }

        case CalculateUsingOperation(operation, a, b) => {
          context.log.info("Calculating...")
          operation ! Operations.Calculate(a, b)
          Behaviors.same
        }
      }
    }
}

object MathActorDiscovery extends App {
  val system: ActorSystem[Calculator.Calculate] =
    ActorSystem(Calculator(), "calculator")

  system ! Calculator.Calculate("addition", 3, 5)
  system ! Calculator.Calculate("multiplication", 3, 5)

  system.terminate()
}
