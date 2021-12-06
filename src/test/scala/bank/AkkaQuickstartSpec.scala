//#full-example
package bank

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import bank.AccountProtocol.Balance
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateQuery.GetAccountBalance
import bank.BankProtocol.BankOperation.AccountsManagementOperation.AccountsManagementCommand.CreateAccount
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class AkkaQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition

  "A Greeter" must {
    //#test
    "reply to greeted" in {
      val replyProbe = createTestProbe[Balance]()
      val underTest = spawn(Bank())
      underTest ! CreateAccount(AccountId("1"))
      underTest ! GetAccountBalance(Left(AccountId("1")), replyProbe.ref)
      replyProbe.expectMessage(Balance(AccountId("1"), 0))
    }
    //#test
  }

}
//#full-example
