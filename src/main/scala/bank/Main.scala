package bank

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.util.Timeout
import bank.AccountProtocol.Balance
import bank.BankProtocol.BankOperation
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateCommand.{CreditAccount, DebitAccount}
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateQuery.GetAccountBalance
import bank.BankProtocol.BankOperation.AccountsManagementOperation.AccountsManagementCommand.CreateAccount

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Main extends App {

  implicit val timeout: Timeout = 3.seconds
  implicit val system: ActorSystem[BankOperation] =
    ActorSystem(Bank(), "bank")
  implicit val ec = system.executionContext

  system.ask(CreateAccount(AccountId("1"), _))
  system.ask(CreateAccount(AccountId("2"), _))
  system ! CreditAccount(Left(AccountId("1")), NonNegativeInt(100))
  system ! CreditAccount(Left(AccountId("2")), NonNegativeInt(150))
  system ! CreditAccount(Left(AccountId("3")), NonNegativeInt(99))
  system.ask(DebitAccount(Left(AccountId("1")), NonNegativeInt(99), _))
  val result: Future[Balance] = system.ask(GetAccountBalance(Left(AccountId("1")), _))

  result.onComplete {
    case Failure(exception) => println(exception)
    case Success(value) => println(value)
  }

  system.terminate()

}
