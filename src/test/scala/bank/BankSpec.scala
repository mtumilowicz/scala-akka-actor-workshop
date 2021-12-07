package bank

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import bank.AccountProtocol.{Balance, Debited, InsufficientFundsForDebit}
import bank.BankProtocol.BankOperation.AccountStateOperation.AccountStateCommand.{CreditAccount, DebitAccount}
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

    "fail when account queried for balance does not exist" in {
      Given("prepare")
      val accountId = randomAccountId()
      val balanceProbe = createTestProbe[Balance]()
      val bank = spawn(Bank())

      Then("cannot find account when querying for banalnce")
      LoggingTestKit.info("CannotFindAccount").expect {
        bank ! GetAccountBalance(Left(accountId), balanceProbe.ref)
      }
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

    "fail when account for credit does not exist" in {
      Given("prepare")
      val accountId = randomAccountId()
      val bank = spawn(Bank())

      Then("cannot find account when crediting")
      LoggingTestKit.info("CannotFindAccount").expect {
        bank ! CreditAccount(Left(accountId), NonNegativeInt(123))
      }
    }

    "fail when creating two accounts with same id" in {
      Given("prepare")
      val accountId = randomAccountId()
      val accountCreatedProbe = createTestProbe[Either[CreatingAccountFailed, AccountCreated]]()
      val bank = spawn(Bank())
      bank ! CreateAccount(accountId, accountCreatedProbe.ref)
      bank ! CreateAccount(accountId, accountCreatedProbe.ref)

      Then("creating account succeeded for the first time")
      accountCreatedProbe.expectMessage(Right(AccountCreated(accountId)))
      And("failed for the second time")
      accountCreatedProbe.expectMessage(Left(CreatingAccountFailed(accountId, s"actor name [Account-${accountId.raw}] is not unique!")))
    }

    "fail when debiting account that does not exist" in {
      Given("prepare")
      val accountId = randomAccountId()
      val debitProbe = createTestProbe[Either[InsufficientFundsForDebit, Debited]]()
      val bank = spawn(Bank())

      Then("cannot find account when debiting")
      LoggingTestKit.info("CannotFindAccount").expect {
        bank ! DebitAccount(Left(accountId), NonNegativeInt(123), debitProbe.ref)
      }
    }

    "succeed when debiting account that has sufficient funds" in {
      Given("prepare")
      val accountId = randomAccountId()
      val balanceProbe = createTestProbe[Balance]()
      val accountCreatedProbe = createTestProbe[Either[CreatingAccountFailed, AccountCreated]]()
      val debitProbe = createTestProbe[Either[InsufficientFundsForDebit, Debited]]()
      val bank = spawn(Bank())
      bank ! CreateAccount(accountId, accountCreatedProbe.ref)
      bank ! CreditAccount(Left(accountId), NonNegativeInt(123))

      When("debit account")
      bank ! DebitAccount(Left(accountId), NonNegativeInt(100), debitProbe.ref)

      Then("debit is successful")
      debitProbe.expectMessage(Right(Debited(accountId, NonNegativeInt(100))))

      And("balance is updated")
      bank ! GetAccountBalance(Left(accountId), balanceProbe.ref)
      balanceProbe.expectMessage(Balance(accountId, NonNegativeInt(23)))
    }

    "fail when debiting account that does not have sufficient funds" in {
      Given("prepare")
      val accountId = randomAccountId()
      val balanceProbe = createTestProbe[Balance]()
      val accountCreatedProbe = createTestProbe[Either[CreatingAccountFailed, AccountCreated]]()
      val debitProbe = createTestProbe[Either[InsufficientFundsForDebit, Debited]]()
      val bank = spawn(Bank())
      bank ! CreateAccount(accountId, accountCreatedProbe.ref)
      bank ! CreditAccount(Left(accountId), NonNegativeInt(23))

      When("debit account")
      bank ! DebitAccount(Left(accountId), NonNegativeInt(100), debitProbe.ref)

      Then("debit failed")
      debitProbe.expectMessage(Left(InsufficientFundsForDebit(accountId, NonNegativeInt(23))))

      And("balance stay the same")
      bank ! GetAccountBalance(Left(accountId), balanceProbe.ref)
      balanceProbe.expectMessage(Balance(accountId, NonNegativeInt(23)))
    }

    "fail when debiting account for more than 1_000_000" in {
      Given("prepare")
      val accountId = randomAccountId()
      val balanceProbe = createTestProbe[Balance]()
      val accountCreatedProbe = createTestProbe[Either[CreatingAccountFailed, AccountCreated]]()
      val debitProbe = createTestProbe[Either[InsufficientFundsForDebit, Debited]]()
      val bank = spawn(Bank())
      bank ! CreateAccount(accountId, accountCreatedProbe.ref)
      bank ! CreditAccount(Left(accountId), NonNegativeInt(10_000_000))

      Then("exception during debit")
      LoggingTestKit
        .error[RuntimeException]
        .withMessageContains("You are a big spender, try to spend less!")
        .withOccurrences(1)
        .expect {
          bank ! DebitAccount(Left(accountId), NonNegativeInt(1_000_001), debitProbe.ref)
        }

      And("balance stays intact")
      bank ! GetAccountBalance(Left(accountId), balanceProbe.ref)
      balanceProbe.expectMessage(Balance(accountId, NonNegativeInt(10_000_000)))
    }

  }

  private def randomAccountId(): AccountId =
    AccountId(UUID.randomUUID().toString)

}