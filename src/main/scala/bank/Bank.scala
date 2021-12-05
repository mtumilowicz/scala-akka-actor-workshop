package bank

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import bank.Bank.{BalanceResponse, BankOperations, CreateAccount, CreditAccountById, GetBalanceById, find}

import java.util.concurrent.TimeUnit
import scala.util.Success

sealed trait AccountOperations

case class CreditAccount(amount: Int) extends AccountOperations
case class GetBalance(replyTo: ActorRef[BalanceResponse]) extends AccountOperations

case class Account(id: String) {

  val serviceKey: ServiceKey[AccountOperations] = ServiceKey[AccountOperations](id)

  def behavior(): Behavior[AccountOperations] = behavior(0)

  def behavior(balance: Int): Behavior[AccountOperations] = Behaviors.receiveMessage {
    case CreditAccount(amount) =>
      println(s"Account with id = $id was credited with $amount")
      behavior(balance + amount)
    case GetBalance(sender) =>
      sender ! BalanceResponse(balance)
      Behaviors.same
  }
}

object Bank {

  sealed trait BankOperations

  sealed trait AccountStateOperations extends BankOperations

  case class CreditAccountById(id: String, amount: Int) extends AccountStateOperations
  case class GetBalanceById(id: String) extends AccountStateOperations
  case class BalanceResponse(balance: Int) extends AccountStateOperations

  sealed trait AccountsManagementOperations extends BankOperations

  case class CreateAccount(id: String) extends AccountsManagementOperations

  private case class AccountToCredit(account: ActorRef[AccountOperations], amount: Int) extends AccountStateOperations
  private case class AccountToGetBalance(account: ActorRef[AccountOperations]) extends AccountStateOperations

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
    case AccountToGetBalance(account) =>
      account ! GetBalance(context.self)
      Behaviors.same
    case BalanceResponse(balance) =>
      println(balance)
      Behaviors.same
    case AccountCreditFailed(reason) =>
      println(reason)
      Behaviors.same
    case operations: AccountStateOperations => operations match {
      case CreditAccountById(id, amount) =>

        find(id, context, _.headOption.map(AccountToCredit(_, amount))
          .getOrElse(AccountCreditFailed(s"account with id = $id does not exist")))

        Behaviors.same
      case GetBalanceById(id) =>
        find(id, context, _.headOption.map(AccountToGetBalance)
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
  system ! GetBalanceById("1")

  system.terminate()
}
