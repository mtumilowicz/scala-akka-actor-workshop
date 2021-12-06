package bank

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import bank.AccountProtocol._
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateCommand.{CreditAccountFailed, CreditAccount}
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateQuery.GetAccountBalance
import bank.BankProtocol.BankOperation.AccountsManagementOperation.AccountsManagementCommand.CreateAccount
import bank.BankProtocol._

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object BankProtocol {

  sealed trait BankOperation

  sealed trait AccountStateOperation extends BankOperation

  sealed trait AccountStateCommand extends AccountStateOperation

  sealed trait AccountStateQuery extends AccountStateOperation

  sealed trait AccountsManagementOperation extends BankOperation

  sealed trait AccountsManagementCommand extends AccountsManagementOperation

  object BankOperation {
    object AccountStateOperation {
      object AccountStateCommand {
        case class CreditAccount(account: Either[AccountId, ActorRef[AccountOperation]], amount: Int) extends AccountStateCommand

        case class CreditAccountFailed(reason: String) extends AccountStateCommand
      }

      object AccountStateQuery {
        case class GetAccountBalance(account: Either[AccountId, ActorRef[AccountOperation]], replyTo: ActorRef[BalanceResponse]) extends AccountStateQuery
      }

    }

    object AccountsManagementOperation {
      object AccountsManagementCommand {
        case class CreateAccount(id: AccountId) extends AccountsManagementCommand
      }

    }

  }

}

object Bank {

  type Context = ActorContext[BankOperation]

  def apply(): Behavior[BankOperation] = Behaviors.receive { (context, message) =>
    implicit val implicitContext: Context = context
    message match {
      case operations: AccountStateOperation => handleState(operations)
      case operations: AccountsManagementOperation => handleManagement(operations)
    }
  }

  private def handleManagement(operation: AccountsManagementOperation)(implicit context: Context): Behavior[BankOperation] =
    operation match {
      case command: AccountsManagementCommand => handleManagementCommand(command)
    }

  private def handleManagementCommand(command: AccountsManagementCommand)(implicit context: Context): Behavior[BankOperation] =
    command match {
      case CreateAccount(id @ AccountId(rawId)) =>
        val account = Account(id)
        val accountRef = context.spawn(account.behavior(), s"Account-$rawId")
        context.system.receptionist ! Receptionist.Register(account.serviceKey, accountRef)
        Behaviors.same
    }

  private def handleState(operation: AccountStateOperation)(implicit context: Context): Behavior[BankOperation] =
    operation match {
      case command: AccountStateCommand => handleStateCommand(command)
      case query: AccountStateQuery => handleStateQuery(query)
    }

  private def handleStateCommand(command: AccountStateCommand)(implicit context: Context): Behavior[BankOperation] =
    command match {
      case CreditAccount(Left(id @ AccountId(rawId)), amount) =>
        find(id, context, _.headOption.map(ref => CreditAccount(Right(ref), amount))
          .getOrElse(CreditAccountFailed(s"account with id = $rawId does not exist")))

        Behaviors.same
      case CreditAccount(Right(accountRef), amount) =>
        accountRef ! Credit(amount)
        Behaviors.same
      case CreditAccountFailed(reason) =>
        println(reason)
        Behaviors.same
    }

  private def handleStateQuery(query: AccountStateQuery)(implicit context: Context): Behavior[BankOperation] =
    query match {
      case GetAccountBalance(Left(id @ AccountId(rawId)), replyTo) =>
        find(id, context, _.headOption.map(ref => GetAccountBalance(Right(ref), replyTo))
          .getOrElse(CreditAccountFailed(s"account with id = $rawId does not exist")))

        Behaviors.same
      case GetAccountBalance(Right(accountRef), replyTo) =>
        accountRef ! GetBalance(replyTo)
        Behaviors.same
    }

  private def find(id: AccountId, context: Context, f: Set[ActorRef[AccountOperation]] => BankOperation): Unit = {
    implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

    val serviceKey = ServiceKey[AccountOperation](id.raw)

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

  system ! CreateAccount(AccountId("1"))
  system ! CreateAccount(AccountId("2"))
  system ! CreditAccount(Left(AccountId("1")), 100)
  system ! CreditAccount(Left(AccountId("2")), 150)
  system ! CreditAccount(Left(AccountId("3")), 99)
  val result: Future[BalanceResponse] = system.ask(GetAccountBalance(Left(AccountId("1")), _))

  result.onComplete {
    case Failure(exception) => println(exception)
    case Success(value) => println(value)
  }

  system.terminate()
}
