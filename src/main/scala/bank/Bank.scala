package bank

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import bank.Bank.{BankOperations, CreateAccount, CreditAccountById}

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

sealed trait AccountOperations

case class CreditAccount(amount: Int) extends AccountOperations

case class Account(id: String) {

  val serviceKey: ServiceKey[AccountOperations] = ServiceKey[AccountOperations](id)

  def behavior(): Behavior[AccountOperations] = behavior(0)

  def behavior(balance: Int): Behavior[AccountOperations] = Behaviors.receiveMessage {
    case CreditAccount(amount) =>
      println(s"Account with id = $id was credited with $amount")
      behavior(balance + amount)
  }
}

object Bank {

  sealed trait BankOperations

  sealed trait AccountStateOperations extends BankOperations

  case class CreditAccountById(id: String, amount: Int) extends AccountStateOperations

  sealed trait AccountsManagementOperations extends BankOperations

  case class CreateAccount(id: String) extends AccountsManagementOperations

  private case class AccountToCredit(account: ActorRef[AccountOperations], amount: Int) extends BankOperations
  private case class AccountCreditFailed(reason: String) extends BankOperations

  def apply(): Behavior[BankOperations] = Behaviors.receive { (context, message) =>
    message match {
      case AccountToCredit(account, amount) =>
        account ! CreditAccount(amount)
        Behaviors.same
      case AccountCreditFailed(reason) =>
        println(reason)
        Behaviors.same
      case operations: AccountStateOperations => operations match {
        case CreditAccountById(id, amount) =>

          implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

          val serviceKey = ServiceKey[AccountOperations](id)

          context.ask(
            context.system.receptionist,
            Receptionist.Find(serviceKey)
          ) {
            case Success(listing) =>
              val instances = listing.serviceInstances(serviceKey)
              instances.headOption.map(AccountToCredit(_, amount))
                .getOrElse(AccountCreditFailed(s"account with id = $id does not exist"))
            case Failure(exception) =>
              AccountCreditFailed(exception.getMessage)
          }
          Behaviors.same
      }

      case operations: AccountsManagementOperations => operations match {
        case CreateAccount(id) =>
          val account = Account(id)
          val accountRef = context.spawn(account.behavior(), s"Account-$id")
          context.system.receptionist ! Receptionist.Register(account.serviceKey, accountRef)
          Behaviors.same
      }
    }
  }

}

object Main extends App {
  val system: ActorSystem[BankOperations] =
    ActorSystem(Bank(), "bank")

  system ! CreateAccount("1")
  system ! CreateAccount("2")
  system ! CreditAccountById("1", 100)
  system ! CreditAccountById("2", 150)
  system ! CreditAccountById("3", 99)

  system.terminate()
}
