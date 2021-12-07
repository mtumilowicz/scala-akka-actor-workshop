//#full-example
package bank

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.AskPattern.Askable
import bank.AccountProtocol.Balance
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateQuery.GetAccountBalance
import bank.BankProtocol.BankOperation.AccountsManagementOperation.AccountsManagementCommand.{AccountCreated, CreateAccount, CreatingAccountFailed}
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class AkkaQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition

  "A Greeter" must {
    //#test
    "reply to greeted" in {
      val replyProbe = createTestProbe[Balance]()
      val replyProbe2 = createTestProbe[Either[CreatingAccountFailed, AccountCreated]]()
      val underTest = spawn(Bank())
      underTest ! CreateAccount(AccountId("1"), replyProbe2.ref)
      underTest ! GetAccountBalance(Left(AccountId("1")), replyProbe.ref)
      replyProbe2.expectMessage(Right(AccountCreated(AccountId("1"))))
      replyProbe.expectMessage(Balance(AccountId("1"), NonNegativeInt(0)))
    }
    //#test
  }

}
//#full-example
