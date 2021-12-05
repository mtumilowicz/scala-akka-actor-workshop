package bank

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import bank.Bank.{BankOperations, CreateAccount, CreditAccountById, find}

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

  private case class AccountToCredit(account: ActorRef[AccountOperations], amount: Int) extends AccountStateOperations

  private case class AccountCreditFailed(reason: String) extends AccountStateOperations

  private case class FindFailed(reason: String) extends BankOperations

  def apply(): Behavior[BankOperations] = Behaviors.setup { context =>
    Behaviors.receiveMessagePartial(
    accountStateOps(context).orElse {
      case FindFailed(reason) =>
        println(reason)
        Behaviors.same
      case operations: AccountsManagementOperations => operations match {
        case CreateAccount(id) =>
          val account = Account(id)
          val accountRef = context.spawn(account.behavior(), s"Account-$id")
          context.system.receptionist ! Receptionist.Register(account.serviceKey, accountRef)
          Behaviors.same
      }
    }
    )
  }

  private def accountStateOps(context: ActorContext[BankOperations]): PartialFunction[BankOperations, Behavior[BankOperations]] = {
    case AccountToCredit(account, amount) =>
      account ! CreditAccount(amount)
      Behaviors.same
    case AccountCreditFailed(reason) =>
      println(reason)
      Behaviors.same
    case operations: AccountStateOperations => operations match {
      case CreditAccountById(id, amount) =>

        find(id, context, _.headOption.map(AccountToCredit(_, amount))
          .getOrElse(AccountCreditFailed(s"account with id = $id does not exist")))

        Behaviors.same
    }
  }

  def find(id: String, context: ActorContext[BankOperations], f: Set[ActorRef[AccountOperations]] => BankOperations): Unit = {
    implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

    val serviceKey = ServiceKey[AccountOperations](id)

    context.ask(
      context.system.receptionist,
      Receptionist.Find(serviceKey)
    ) {
      case Success(listing) =>
        f(listing.serviceInstances(serviceKey))
      case Failure(exception) =>
        FindFailed(exception.getMessage)
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
