package bank

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import bank.AccountProtocol._

object AccountProtocol {
  sealed trait AccountOperation

  sealed trait AccountCommand extends AccountOperation
  case class Credit(amount: NonNegativeInt) extends AccountCommand
  case class Debit(amount: NonNegativeInt, replyTo: ActorRef[Either[InsufficientFundsForDebit, Debited]]) extends AccountCommand
  case class Debited(id: AccountId, amount: NonNegativeInt)
  case class InsufficientFundsForDebit(id: AccountId, balance: NonNegativeInt)

  sealed trait AccountQuery extends AccountOperation
  case class GetBalance(replyTo: ActorRef[Balance]) extends AccountQuery
  case class Balance(id: AccountId, balance: NonNegativeInt)

}

case class Account(id: AccountId) {

  private case class AccountState(balance: NonNegativeInt)

  val serviceKey: ServiceKey[AccountOperation] = ServiceKey[AccountOperation](id.raw)

  def behavior(): Behavior[AccountOperation] = behavior(AccountState(NonNegativeInt(0)))

  private def behavior(implicit state: AccountState): Behavior[AccountOperation] = Behaviors.receiveMessage {
    case command: AccountCommand => handleCommand(command)
    case query: AccountQuery => handleQuery(query)
  }

  private def handleCommand(command: AccountCommand)(implicit state: AccountState): Behavior[AccountOperation] =
    command match {
      case Credit(amount) => behavior(state.copy(balance = state.balance + amount))
      case debit@Debit(_, _) => handleDebit(debit)
    }

  private def handleDebit(debit: Debit)(implicit state: AccountState): Behavior[AccountOperation] = {
    val Debit(amount, replyTo) = debit
    if (state.balance < amount) {
      replyTo ! Left(InsufficientFundsForDebit(id, state.balance))
      Behaviors.same
    } else {
      if (amount.raw > 1_000_000) throw new RuntimeException("You are a big spender, try to spend less!")
      replyTo ! Right(Debited(id = id, amount = amount))
      behavior(state.copy(balance = state.balance - amount))
    }
  }

  private def handleQuery(query: AccountQuery)(implicit state: AccountState): Behavior[AccountOperation] =
    query match {
      case GetBalance(replyTo) =>
        replyTo ! Balance(id, state.balance)
        Behaviors.same
    }

}