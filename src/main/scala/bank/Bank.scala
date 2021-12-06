package bank

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import bank.AccountProtocol._
import bank.Bank.{BankOperations, CreateAccount, CreditAccountById, GetBalanceById, find}

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Bank {

  sealed trait BankOperations

  sealed trait AccountStateOperations extends BankOperations

  case class CreditAccountById(id: String, amount: Int) extends AccountStateOperations
  case class GetBalanceById(id: String, replyTo: ActorRef[BalanceResponse]) extends AccountStateOperations

  sealed trait AccountsManagementOperations extends BankOperations

  case class CreateAccount(id: String) extends AccountsManagementOperations

  private case class AccountToCredit(account: ActorRef[AccountOperation], amount: Int) extends AccountStateOperations
  private case class AccountToGetBalance(account: ActorRef[AccountOperation], replyTo: ActorRef[BalanceResponse]) extends AccountStateOperations

  private case class AccountCreditFailed(reason: String) extends AccountStateOperations

  def apply(): Behavior[BankOperations] = Behaviors.receive { (context, message) =>
    message match {
      case operations: AccountStateOperations => accountStateOps(context)(operations)
      case operations: AccountsManagementOperations => accountManagementOps(context)(operations)
    }
  }

  private def accountManagementOps(context: ActorContext[BankOperations]): AccountsManagementOperations => Behavior[BankOperations] = {
      case CreateAccount(id) =>
        val account = Account(id)
        val accountRef = context.spawn(account.behavior(), s"Account-$id")
        context.system.receptionist ! Receptionist.Register(account.serviceKey, accountRef)
        Behaviors.same
  }

  private def accountStateOps(context: ActorContext[BankOperations]): AccountStateOperations => Behavior[BankOperations] = {
    case AccountToCredit(account, amount) =>
      account ! CreditAccount(amount)
      Behaviors.same
    case AccountToGetBalance(account, replyTo) =>
      account ! GetBalance(replyTo)
      Behaviors.same
    case AccountCreditFailed(reason) =>
      println(reason)
      Behaviors.same
    case operations: AccountStateOperations => operations match {
      case CreditAccountById(id, amount) =>

        find(id, context, _.headOption.map(AccountToCredit(_, amount))
          .getOrElse(AccountCreditFailed(s"account with id = $id does not exist")))

        Behaviors.same
      case GetBalanceById(id, replyTo) =>
        find(id, context, _.headOption.map(AccountToGetBalance(_, replyTo))
          .getOrElse(AccountCreditFailed(s"account with id = $id does not exist")))

        Behaviors.same
    }
  }

  def find(id: String, context: ActorContext[BankOperations], f: Set[ActorRef[AccountOperation]] => BankOperations): Unit = {
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
  implicit val system: ActorSystem[BankOperations] =
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
