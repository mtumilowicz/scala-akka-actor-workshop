package bank

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.util.Timeout
import bank.AccountProtocol._
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateCommand.{CreditAccount, DebitAccount}
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateQuery.GetAccountBalance
import bank.BankProtocol.BankOperation.AccountsManagementOperation.AccountsManagementCommand.{AccountCreated, CreateAccount, CreatingAccountFailed}
import bank.BankProtocol.BankOperation.BankError.{CannotFindAccount, SelectorFailure, SelectorReturnManyAccounts}
import bank.BankProtocol._

import java.util.concurrent.TimeUnit
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
        case class CreditAccount(account: AccountRepresentation, amount: NonNegativeInt) extends AccountStateCommand

        case class DebitAccount(account: AccountRepresentation,
                                amount: NonNegativeInt,
                                replyTo: ActorRef[Either[InsufficientFundsForDebit, Debited]]
                               ) extends AccountStateCommand
      }

      object AccountStateQuery {
        case class GetAccountBalance(account: AccountRepresentation, replyTo: ActorRef[Balance]) extends AccountStateQuery
      }
    }

    object AccountsManagementOperation {
      object AccountsManagementCommand {
        case class CreateAccount(id: AccountId, replyTo: ActorRef[Either[CreatingAccountFailed, AccountCreated]]) extends AccountsManagementCommand
        case class AccountCreated(id: AccountId)
        case class CreatingAccountFailed(id: AccountId, reason: String)
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

  private def handleError(error: BankError)(implicit context: Context): Behavior[BankOperation] =
    error match {
      case failure@CannotFindAccount(_) =>
        context.log.info("Received message: {}", failure)
        Behaviors.same
      case failure@SelectorReturnManyAccounts(_) =>
        context.log.info("Received message: {}", failure)
        Behaviors.same
      case failure@SelectorFailure(_, _) =>
        context.log.info("Received message: {}", failure)
        Behaviors.same
    }

  private def handleManagement(operation: AccountsManagementOperation)(implicit context: Context): Behavior[BankOperation] =
    operation match {
      case command: AccountsManagementCommand => handleManagementCommand(command)
    }

  private def handleManagementCommand(command: AccountsManagementCommand)(implicit context: Context): Behavior[BankOperation] =
    command match {
      case CreateAccount(id@AccountId(rawId), replyTo) =>
        val account = Account(id)
        try {
          val accountRef = context.spawn(
            Behaviors.supervise(account.behavior())
              .onFailure[RuntimeException](SupervisorStrategy.resume),
            s"Account-$rawId"
          )
          context.system.receptionist ! Receptionist.Register(account.serviceKey, accountRef)
          replyTo ! Right(AccountCreated(id))
          Behaviors.same
        } catch {
          case ex: Throwable =>
            replyTo ! Left(CreatingAccountFailed(id, ex.getMessage))
            Behaviors.same
        }
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
      case DebitAccount(Left(id), amount, replyTo) =>
        selectAccountAndFire(id, ref => DebitAccount(Right(ref), amount, replyTo))

        Behaviors.same
      case DebitAccount(Right(accountRef), amount, replyTo) =>
        accountRef ! Debit(amount, replyTo)
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
