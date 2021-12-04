package app

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.util.Timeout
import com.example.GreeterMain.SayHello

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}


sealed trait AccountOperation
case class CreditAccount(raw: Int) extends AccountOperation
case class DebitAccount(raw: Int) extends AccountOperation
case class GetBalance(replyTo: ActorRef[Int]) extends AccountOperation

object AccountActor {
  def apply(): Behavior[AccountOperation] =
    behavior(0)

  private def behavior(balance: Int): Behavior[AccountOperation] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case CreditAccount(amount) =>
        behavior(balance + amount)
      case DebitAccount(amount) =>
        behavior(balance - amount)
      case GetBalance(sender) =>
        sender ! balance
        Behaviors.same

    }
  }
}

object ActorMain {
  final case class Start(clientName: String)

  def apply(): Behavior[Start] =
    Behaviors.setup { context =>

      Behaviors.receiveMessage { message =>
        context.log.info("Start a new account")
        context.spawn(AccountActor(), "account")
        Behaviors.same
      }
    }
}

object Progress extends App {

  implicit val timeout: Timeout = 3.seconds
  // implicit ActorSystem in scope
  implicit val system: ActorSystem[AccountOperation] = ActorSystem(AccountActor(), "AccountActor")
  val accountActorRef: ActorRef[AccountOperation] = system
  implicit val ec = system.executionContext

  accountActorRef ! CreditAccount(100)
  accountActorRef ! DebitAccount(30)
  accountActorRef ! CreditAccount(800)

  val result: Future[Int] = accountActorRef ? GetBalance

  result.onComplete {
    case Failure(exception) => println(exception)
    case Success(value) => println(value)
  }

}
