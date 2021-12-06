package bank

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import bank.AccountProtocol._
import bank.BankProtocol._

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object BankProtocol {

  sealed trait BankOperation

  sealed trait AccountStateOperation extends BankOperation

  sealed trait AccountStateCommand extends AccountStateOperation
  case class CreditAccountById(id: String, amount: Int) extends AccountStateCommand
  case class AccountToCredit(account: ActorRef[AccountOperation], amount: Int) extends AccountStateCommand
  case class AccountCreditFailed(reason: String) extends AccountStateCommand

  sealed trait AccountStateQuery extends AccountStateOperation
  case class GetBalanceById(id: String, replyTo: ActorRef[BalanceResponse]) extends AccountStateQuery
  case class AccountToGetBalance(account: ActorRef[AccountOperation], replyTo: ActorRef[BalanceResponse]) extends AccountStateQuery

  sealed trait AccountsManagementOperation extends BankOperation
  sealed trait AccountsManagementCommand extends AccountsManagementOperation

  case class CreateAccount(id: String) extends AccountsManagementCommand
}

object Bank {

  def apply(): Behavior[BankOperation] = Behaviors.receive { (context, message) =>
    message match {
      case operations: AccountStateOperation => accountStateOps(context)(operations)
      case operations: AccountsManagementOperation => accountManagementOps(context)(operations)
    }
  }

  private def accountManagementOps(context: ActorContext[BankOperation]): AccountsManagementOperation => Behavior[BankOperation] = {
    case command: AccountsManagementCommand =>
      command match {
        case CreateAccount(id) =>
          val account = Account(id)
          val accountRef = context.spawn(account.behavior(), s"Account-$id")
          context.system.receptionist ! Receptionist.Register(account.serviceKey, accountRef)
          Behaviors.same
      }
  }

  private def accountStateOps(context: ActorContext[BankOperation]): AccountStateOperation => Behavior[BankOperation] = {
    case command: AccountStateCommand =>
      command match {
        case CreditAccountById(id, amount) =>
          find(id, context, _.headOption.map(AccountToCredit(_, amount))
            .getOrElse(AccountCreditFailed(s"account with id = $id does not exist")))

          Behaviors.same
        case AccountToCredit(account, amount) =>
          account ! CreditAccount(amount)
          Behaviors.same
        case AccountCreditFailed(reason) =>
          println(reason)
          Behaviors.same
      }
    case query: AccountStateQuery =>
      query match {
        case GetBalanceById(id, replyTo) =>
          find(id, context, _.headOption.map(AccountToGetBalance(_, replyTo))
            .getOrElse(AccountCreditFailed(s"account with id = $id does not exist")))

          Behaviors.same
        case AccountToGetBalance(account, replyTo) =>
          account ! GetBalance(replyTo)
          Behaviors.same
      }
  }

  def find(id: String, context: ActorContext[BankOperation], f: Set[ActorRef[AccountOperation]] => BankOperation): Unit = {
    implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

    val serviceKey = ServiceKey[AccountOperation](id)

    context.ask(
      context.system.receptionist,
      Receptionist.Find(serviceKey)
    ) {
      case Success(listing) =>
        f(listing.serviceInstances(serviceKey))
    }
  }


}

object Main extends App {

  implicit val timeout: Timeout = 3.seconds
  implicit val system: ActorSystem[BankOperation] =
    ActorSystem(Bank(), "bank")
  implicit val ec = system.executionContext

  system ! CreateAccount("1")
  system ! CreateAccount("2")
  system ! CreditAccountById("1", 100)
  system ! CreditAccountById("2", 150)
  system ! CreditAccountById("3", 99)
  val result: Future[BalanceResponse] = system.ask(GetBalanceById("1", _))

  result.onComplete {
    case Failure(exception) => println(exception)
    case Success(value) => println(value)
  }

  system.terminate()
}
