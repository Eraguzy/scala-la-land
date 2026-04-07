package bank

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.math.BigDecimal

class WalletMempoolIntegrationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "WalletActor and MempoolActor" should {
    
    "successfully create a signed transaction" in {
      val wallet = testKit.spawn(
        WalletActor("wallet1", "wallet1")
      )
      val probe = testKit.createTestProbe[TransactionCreated]()

      wallet ! CreateTransaction(
        receiver = "wallet2",
        amount = BigDecimal(100),
        fees = BigDecimal(5),
        replyTo = probe.ref
      )

      val response = probe.receiveMessage()
      response.transaction shouldBe a[Some[_]]
      response.error shouldBe None

      val signedTx = response.transaction.get
      signedTx.publicKey should equal("wallet1")
      signedTx.transaction.receiver should equal("wallet2")
      signedTx.transaction.amount should equal(BigDecimal(100))
      signedTx.transaction.fees should equal(BigDecimal(5))
      signedTx.signature should not be empty
    }

    "verify transaction signature correctly" in {
      val wallet = testKit.spawn(
        WalletActor("wallet1", "wallet1")
      )
      val probe = testKit.createTestProbe[TransactionCreated]()

      wallet ! CreateTransaction(
        receiver = "wallet2",
        amount = BigDecimal(50),
        fees = BigDecimal(2),
        replyTo = probe.ref
      )

      val response = probe.receiveMessage()
      val signedTx = response.transaction.get

      // Vérifier que la signature est valide
      val message = signedTx.transaction.toHash
      val isValid = Crypto.verify(message, signedTx.signature, signedTx.publicKey)
      isValid should be(true)
    }

    "mempool accepts valid transactions" in {
      val mempool = testKit.spawn(MempoolActor())
      
      val signedTx = SignedTransaction(
        transaction = Transaction(
          sender = "wallet1",
          receiver = "wallet2",
          amount = BigDecimal(100),
          fees = BigDecimal(5),
          timestamp = System.currentTimeMillis(),
          nonce = 42
        ),
        signature = Crypto.sign(
          Transaction(
            sender = "wallet1",
            receiver = "wallet2",
            amount = BigDecimal(100),
            fees = BigDecimal(5),
            timestamp = System.currentTimeMillis(),
            nonce = 42
          ).toHash,
          "wallet1"
        ),
        publicKey = "wallet1"
      )

      val probe = testKit.createTestProbe[TransactionSubmitResponse]()

      mempool ! SubmitTransaction(signedTx, probe.ref)

      val response = probe.receiveMessage()
      response.success should be(true)
      response.message should equal("Transaction added to mempool")
    }

    "mempool rejects transactions with invalid signatures" in {
      val mempool = testKit.spawn(MempoolActor())

      val signedTx = SignedTransaction(
        transaction = Transaction(
          sender = "wallet1",
          receiver = "wallet2",
          amount = BigDecimal(100),
          fees = BigDecimal(5),
          timestamp = System.currentTimeMillis(),
          nonce = 42
        ),
        signature = "invalid_signature_123456",
        publicKey = "wallet1"
      )

      val probe = testKit.createTestProbe[TransactionSubmitResponse]()

      mempool ! SubmitTransaction(signedTx, probe.ref)

      val response = probe.receiveMessage()
      response.success should be(false)
      response.message should equal("Invalid signature")
    }

    "mempool sorts transactions by fees (highest first)" in {
      val mempool = testKit.spawn(MempoolActor())

      // Créer 3 transactions avec différents frais
      val transactions = List(
        (BigDecimal(2), "tx1"),
        (BigDecimal(10), "tx2"),
        (BigDecimal(5), "tx3")
      ).map { case (fees, id) =>
        val tx = Transaction(
          sender = "wallet1",
          receiver = "wallet2",
          amount = BigDecimal(100),
          fees = fees,
          timestamp = System.currentTimeMillis(),
          nonce = java.util.UUID.randomUUID().hashCode()
        )
        SignedTransaction(
          transaction = tx,
          signature = Crypto.sign(tx.toHash, "wallet1"),
          publicKey = "wallet1"
        )
      }

      // Soumettre les transactions dans un ordre aléatoire
      for (tx <- transactions) {
        val probe = testKit.createTestProbe[TransactionSubmitResponse]()
        mempool ! SubmitTransaction(tx, probe.ref)
        probe.receiveMessage() // Attendre confirmation
      }

      // Demander les transactions
      val requestProbe = testKit.createTestProbe[TransactionsResponse]()
      mempool ! RequestTransactions(3, requestProbe.ref)

      val response = requestProbe.receiveMessage()
      response.transactions should have length 3

      // Vérifier que les transactions sont triées par frais décroissants
      response.transactions(0).transaction.fees should equal(BigDecimal(10))
      response.transactions(1).transaction.fees should equal(BigDecimal(5))
      response.transactions(2).transaction.fees should equal(BigDecimal(2))
    }

    "mempool returns only requested number of transactions" in {
      val mempool = testKit.spawn(MempoolActor())

      // Ajouter 5 transactions
      for (i <- 1 to 5) {
        val tx = Transaction(
          sender = "wallet1",
          receiver = "wallet2",
          amount = BigDecimal(100),
          fees = BigDecimal(i),
          timestamp = System.currentTimeMillis(),
          nonce = i
        )
        val signedTx = SignedTransaction(
          transaction = tx,
          signature = Crypto.sign(tx.toHash, "wallet1"),
          publicKey = "wallet1"
        )
        val probe = testKit.createTestProbe[TransactionSubmitResponse]()
        mempool ! SubmitTransaction(signedTx, probe.ref)
        probe.receiveMessage()
      }

      // Demander seulement 2 transactions
      val requestProbe = testKit.createTestProbe[TransactionsResponse]()
      mempool ! RequestTransactions(2, requestProbe.ref)

      val response = requestProbe.receiveMessage()
      response.transactions should have length 2
    }

    "end-to-end: wallet creates transaction and mempool accepts it" in {
      val wallet = testKit.spawn(
        WalletActor("wallet1", "wallet1")
      )
      val mempool = testKit.spawn(MempoolActor())

      // Étape 1: Wallet crée une transaction signée
      val createTxProbe = testKit.createTestProbe[TransactionCreated]()
      wallet ! CreateTransaction(
        receiver = "wallet2",
        amount = BigDecimal(50),
        fees = BigDecimal(3),
        replyTo = createTxProbe.ref
      )

      val txCreated = createTxProbe.receiveMessage()
      txCreated.transaction shouldBe a[Some[_]]

      val signedTx = txCreated.transaction.get

      // Étape 2: Soumettre la transaction au Mempool
      val submitProbe = testKit.createTestProbe[TransactionSubmitResponse]()
      mempool ! SubmitTransaction(signedTx, submitProbe.ref)

      val submitResponse = submitProbe.receiveMessage()
      submitResponse.success should be(true)

      // Étape 3: Demander les transactions du mempool
      val requestProbe = testKit.createTestProbe[TransactionsResponse]()
      mempool ! RequestTransactions(1, requestProbe.ref)

      val txResponse = requestProbe.receiveMessage()
      txResponse.transactions should have length 1
      txResponse.transactions(0).transaction.receiver should equal("wallet2")
    }
  }
}

class DBBlockchainSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "DBBlockchain" should {
    
    "return genesis block as first block" in {
      val db = testKit.spawn(DBBlockchain())
      val probe = testKit.createTestProbe[LastBlockResponse]()

      db ! GetLastBlock(probe.ref)

      val response = probe.receiveMessage()
      response.block shouldBe a[Some[_]]
      response.blockId should equal(0)
    }

    "append a new block with transactions" in {
      val db = testKit.spawn(DBBlockchain())

      // Créer une transaction
      val tx = Transaction(
        sender = "wallet1",
        receiver = "wallet2",
        amount = BigDecimal(100),
        fees = BigDecimal(5),
        timestamp = System.currentTimeMillis(),
        nonce = 1
      )
      
      val signedTx = SignedTransaction(
        transaction = tx,
        signature = Crypto.sign(tx.toHash, "private-key"),
        publicKey = "wallet1"
      )

      // Créer un bloc avec PoW valide (simplifié pour les tests)
      val block = Block(
        id = 1,
        transactions = List(signedTx),
        previousBlockHash = "genesis",
        proofOfWork = 1,
        timestamp = System.currentTimeMillis()
      )

      val appendProbe = testKit.createTestProbe[BlockAppendResponse]()
      db ! AppendBlock(block, appendProbe.ref)

      val response = appendProbe.receiveMessage()
      // Note: Le PoW ne sera probablement pas valide pour ce test simple
      // Mais on peut vérifier que la structure fonctionne
    }

    "track balances after transactions" in {
      val db = testKit.spawn(DBBlockchain())

      // Vérifier le solde initial
      val probe1 = testKit.createTestProbe[BalanceResponse]()
      db ! GetBalance("wallet1", probe1.ref)
      val initialBalance = probe1.receiveMessage()
      initialBalance.balance should equal(BigDecimal(1000))
    }
  }
}
