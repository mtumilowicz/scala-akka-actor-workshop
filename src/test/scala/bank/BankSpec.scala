package bank

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import bank.AccountProtocol.Balance
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateCommand.CreditAccount
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateQuery.GetAccountBalance
import bank.BankProtocol.BankOperation.AccountsManagementOperation.AccountsManagementCommand.{AccountCreated, CreateAccount, CreatingAccountFailed}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class BankSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with GivenWhenThen  {

  "Bank" must {
    "successfully create an account" in {
      Given("prepare")
      val accountId = randomAccountId()
      val accountCreatedProbe = createTestProbe[Either[CreatingAccountFailed, AccountCreated]]()
      val bank = spawn(Bank())

      When("create account and ask for balance")
      bank ! CreateAccount(accountId, accountCreatedProbe.ref)

      Then("account is created")
      accountCreatedProbe.expectMessage(Right(AccountCreated(accountId)))
    }

    "create account with initial balance 0" in {
      Given("prepare")
      val accountId = randomAccountId()
      val balanceProbe = createTestProbe[Balance]()
      val accountCreatedProbe = createTestProbe[Either[CreatingAccountFailed, AccountCreated]]()
      val bank = spawn(Bank())
      bank ! CreateAccount(accountId, accountCreatedProbe.ref)

      When("ask for balance")
      bank ! GetAccountBalance(Left(accountId), balanceProbe.ref)

      Then("balance is 0")
      balanceProbe.expectMessage(Balance(accountId, NonNegativeInt(0)))
    }

    "successfully credit existing account" in {
      Given("prepare")
      val accountId = randomAccountId()
      val balanceProbe = createTestProbe[Balance]()
      val accountCreatedProbe = createTestProbe[Either[CreatingAccountFailed, AccountCreated]]()
      val bank = spawn(Bank())
      bank ! CreateAccount(accountId, accountCreatedProbe.ref)

      When("credit account")
      bank ! CreditAccount(Left(accountId), NonNegativeInt(123))

      Then("balance is 123")
      bank ! GetAccountBalance(Left(accountId), balanceProbe.ref)
      balanceProbe.expectMessage(Balance(accountId, NonNegativeInt(123)))
    }

    "notify when account for credit does not exist" in {
      Given("prepare")
      val accountId = randomAccountId()
      val bank = spawn(Bank())

      Then("cannot find account when crediting")
      LoggingTestKit.info("CannotFindAccount").expect {
        bank ! CreditAccount(Left(accountId), NonNegativeInt(123))
      }
    }
  }

  private def randomAccountId(): AccountId =
    AccountId(UUID.randomUUID().toString)

}