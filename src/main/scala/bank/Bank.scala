package bank

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import bank.AccountProtocol._
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateCommand.CreditAccount
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateQuery.GetAccountBalance
import bank.BankProtocol.BankOperation.AccountsManagementOperation.AccountsManagementCommand.CreateAccount
import bank.BankProtocol.BankOperation.BankError.{CannotFindAccount, SelectorFailure, SelectorReturnManyAccounts}
import bank.BankProtocol._

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object BankProtocol {

  type AccountRepresentation = Either[AccountId, ActorRef[AccountOperation]]

  sealed trait BankOperation

  sealed trait BankError extends BankOperation

  sealed trait AccountStateOperation extends BankOperation

  sealed trait AccountStateCommand extends AccountStateOperation

  sealed trait AccountStateQuery extends AccountStateOperation

  sealed trait AccountsManagementOperation extends BankOperation

  sealed trait AccountsManagementCommand extends AccountsManagementOperation

  object BankOperation {
    object BankError {
      case class CannotFindAccount(id: AccountId) extends BankError

      case class SelectorReturnManyAccounts(id: ServiceKey[AccountOperation]) extends BankError

      case class SelectorFailure(id: ServiceKey[AccountOperation], ex: Throwable) extends BankError
    }

    object AccountStateOperation {
      object AccountStateCommand {
        case class CreditAccount(account: AccountRepresentation, amount: Int) extends AccountStateCommand
      }

      object AccountStateQuery {
        case class GetAccountBalance(account: AccountRepresentation, replyTo: ActorRef[Balance]) extends AccountStateQuery
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
      case error: BankError => handleError(error)
      case operation: AccountStateOperation => handleState(operation)
      case operation: AccountsManagementOperation => handleManagement(operation)
    }
  }

  private def handleError(error: BankError): Behavior[BankOperation] =
    error match {
      case failure@CannotFindAccount(_) =>
        println(failure)
        Behaviors.same
      case failure@SelectorReturnManyAccounts(_) =>
        println(failure)
        Behaviors.same
      case failure@SelectorFailure(_, _) =>
        println(failure)
        Behaviors.same
    }

  private def handleManagement(operation: AccountsManagementOperation)(implicit context: Context): Behavior[BankOperation] =
    operation match {
      case command: AccountsManagementCommand => handleManagementCommand(command)
    }

  private def handleManagementCommand(command: AccountsManagementCommand)(implicit context: Context): Behavior[BankOperation] =
    command match {
      case CreateAccount(id@AccountId(rawId)) =>
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
      case CreditAccount(Left(id), amount) =>
        selectAccountAndFire(id, ref => CreditAccount(Right(ref), amount))

        Behaviors.same
      case CreditAccount(Right(accountRef), amount) =>
        accountRef ! Credit(amount)
        Behaviors.same
    }

  private def handleStateQuery(query: AccountStateQuery)(implicit context: Context): Behavior[BankOperation] =
    query match {
      case GetAccountBalance(Left(id), replyTo) =>
        selectAccountAndFire(id, ref => GetAccountBalance(Right(ref), replyTo))

        Behaviors.same
      case GetAccountBalance(Right(accountRef), replyTo) =>
        accountRef ! GetBalance(replyTo)
        Behaviors.same
    }

  private def selectAccountAndFire(id: AccountId, mapping: ActorRef[AccountOperation] => BankOperation)(implicit context: Context): Unit = {
    implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

    val serviceKey = ServiceKey[AccountOperation](id.raw)

    context.ask(
      context.system.receptionist,
      Receptionist.Find(serviceKey)
    ) {
      case Success(listing) =>
        val instances = listing.serviceInstances(serviceKey)
        instances.toList match {
          case Nil => CannotFindAccount(id)
          case head :: Nil => mapping(head)
          case _ => SelectorReturnManyAccounts(serviceKey)
        }
      case Failure(exception) =>
        SelectorFailure(serviceKey, exception)
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
  val result: Future[Balance] = system.ask(GetAccountBalance(Left(AccountId("1")), _))

  result.onComplete {
    case Failure(exception) => println(exception)
    case Success(value) => println(value)
  }

  system.terminate()
}
