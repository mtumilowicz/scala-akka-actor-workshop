package bank

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import bank.Bank.{AccountManagementOperations, CreateAccount}

case class Account(id: String) {
  def behavior(): Behavior[Unit] = Behaviors.setup { context =>
    println(s"Account with id = $id initialized")
    Behaviors.empty
  }
}

object Bank {

  sealed trait AccountManagementOperations

  case class CreateAccount(id: String) extends AccountManagementOperations

  def apply(): Behavior[AccountManagementOperations] = Behaviors.receive { (context, message) =>
    message match {
      case CreateAccount(id) =>
        context.spawn(Account(id).behavior(), s"Account-$id")
        Behaviors.same
    }
  }

}

object Main extends App {
  val system: ActorSystem[AccountManagementOperations] =
    ActorSystem(Bank(), "bank")

  system ! CreateAccount("1")
  system ! CreateAccount("2")

  system.terminate()
}
