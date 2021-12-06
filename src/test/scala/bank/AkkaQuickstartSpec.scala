//#full-example
package bank

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import bank.AccountProtocol.BalanceResponse
import bank.Bank.{CreateAccount, GetBalanceById}
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class AkkaQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition

  "A Greeter" must {
    //#test
    "reply to greeted" in {
      val replyProbe = createTestProbe[BalanceResponse]()
      val underTest = spawn(Bank())
      underTest ! CreateAccount("1")
      underTest ! GetBalanceById("1", replyProbe.ref)
      replyProbe.expectMessage(BalanceResponse("1", 0))
    }
    //#test
  }

}
//#full-example
