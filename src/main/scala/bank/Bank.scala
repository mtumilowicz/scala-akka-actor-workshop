package bank

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import bank.Bank.{AccountsManagementOperations, BankOperations, CreateAccount, CreditAccountById}

sealed trait AccountOperations
case class CreditAccount(amount: Int) extends AccountOperations

case class Account(id: String) {

  def behavior(): Behavior[AccountOperations] = Behaviors.setup { context =>
    println(s"Account with id = $id initialized")
    Behaviors.receiveMessage {
      case CreditAccount(amount) => println(s"Account with id = $id was credited with $amount")
        Behaviors.same
    }
  }
}

object Bank {

  sealed trait BankOperations


  sealed trait AccountStateOperations extends BankOperations
  case class CreditAccountById(id: String, amount: Int) extends AccountStateOperations

  sealed trait AccountsManagementOperations extends BankOperations
  case class CreateAccount(id: String) extends AccountsManagementOperations

  def apply(): Behavior[BankOperations] = Behaviors.receive { (context, message) =>
    message match {
      case operations: AccountStateOperations => operations match {
        case CreditAccountById(id, amount) =>
          val account3 = context.spawn(Account("3").behavior(), "Account-3")
          account3 ! CreditAccount(amount)
          Behaviors.same
      }

      case operations: AccountsManagementOperations => operations match {
        case CreateAccount(id) =>
          context.spawn(Account(id).behavior(), s"Account-$id")
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
  system ! CreditAccountById("3", 100)

  system.terminate()
}
