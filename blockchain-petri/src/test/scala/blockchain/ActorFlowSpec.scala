package blockchain

import org.scalatest.funsuite.AnyFunSuite
import WalletDirectoryMessage._
import MempoolMessage._
import ValidatorMessage._

// Tests d'integration legers verifies sur les flux acteurs principaux.
class ActorFlowSpec extends AnyFunSuite {

  test("snapshot codec round-trip preserves chain and wallets") {
    // Le codec doit etre bijectif sur les champs persistants.
    val snapshot = DemoData.initialSnapshot()
    val restored = SnapshotCodec.read(SnapshotCodec.write(snapshot))

    assert(restored.difficulty == snapshot.difficulty)
    assert(restored.miningReward == snapshot.miningReward)
    assert(restored.wallets == snapshot.wallets)
    assert(restored.mempool == snapshot.mempool)
    assert(restored.chain == snapshot.chain)
  }

  test("wallet directory creates a valid signed transaction") {
    // Validation du flux de creation/signature via annuaire wallet.
    val runtime = ActorRuntime.fromSnapshot(DemoData.initialSnapshot())
    try {
      val pending = runtime.mempool.ask(GetPendingOutgoing("alice", _))

      val result = runtime.walletDirectory.ask(CreateTransaction("alice", "bob", BigDecimal(10), pending, _))

      assert(result.isRight)
      result match {
        case Right(tx) =>
          assert(tx.from == "alice")
          assert(tx.to == "bob")
          assert(tx.amount == BigDecimal(10))
          assert(tx.fees == BigDecimal(0))
        case Left(error) =>
          fail(error)
      }
    } finally {
      runtime.shutdown()
    }
  }

  test("mempool accepts a valid transaction and validator can mine it") {
    // Scenario complet: submit -> mempool -> mine -> verification de chaine.
    val runtime = ActorRuntime.fromSnapshot(DemoData.initialSnapshot())
    try {
      val tx = runtime.walletDirectory
        .ask(SubmitTransactionFromWallet("alice", "diana", BigDecimal(5), BigDecimal(0.3), runtime.mempool, _))
        .toOption
        .get

      assert(runtime.mempool.ask(ContainsAll(Seq(tx), _)))

      val beforeMine = runtime.snapshot().mempool.size

      val mined = runtime.validator("validator-1").get.ask(MineOnce(logEvery = Long.MaxValue, attemptDelayMs = 0L, _))
      assert(mined.isRight)

      val snapshot = runtime.snapshot()
      assert(snapshot.chain.size == 2)
      assert(snapshot.mempool.size == math.max(0, beforeMine - 2))
      assert(ChainVerifier.isValid(snapshot))
    } finally {
      runtime.shutdown()
    }
  }
}
