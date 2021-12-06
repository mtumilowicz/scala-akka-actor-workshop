package bank

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import bank.AccountProtocol._

object AccountProtocol {
  sealed trait AccountOperation
  sealed trait AccountCommand extends AccountOperation
  case class Credit(amount: Int) extends AccountCommand

  sealed trait AccountQuery extends AccountOperation
  case class GetBalance(replyTo: ActorRef[Balance]) extends AccountQuery
  case class Balance(id: AccountId, balance: Int)

  case class AccountState(balance: Int)
}

case class Account(id: AccountId) {

  val serviceKey: ServiceKey[AccountOperation] = ServiceKey[AccountOperation](id.raw)

  def behavior(): Behavior[AccountOperation] = behavior(AccountState(0))

  private def behavior(state: AccountState): Behavior[AccountOperation] = Behaviors.receiveMessage {
    case command: AccountCommand => handleCommand(state, command)
    case query: AccountQuery => handleQuery(state, query)
  }

  private def handleCommand(state: AccountState, command: AccountCommand): Behavior[AccountOperation] =
    command match {
      case Credit(amount) => behavior(state.copy(balance = state.balance + amount))
    }

  private def handleQuery(state: AccountState, query: AccountQuery): Behavior[AccountOperation] =
    query match {
      case GetBalance(replyTo) =>
        replyTo ! Balance(id, state.balance)
        Behaviors.same
    }

}