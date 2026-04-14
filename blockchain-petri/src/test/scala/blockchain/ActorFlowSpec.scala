package blockchain

import org.scalatest.funsuite.AnyFunSuite
import WalletDirectoryMessage._
import MempoolMessage._
import ValidatorMessage._

class ActorFlowSpec extends AnyFunSuite {

  test("snapshot codec round-trip preserves chain and wallets") {
    val snapshot = DemoData.initialSnapshot()
    val restored = SnapshotCodec.read(SnapshotCodec.write(snapshot))

    assert(restored.difficulty == snapshot.difficulty)
    assert(restored.miningReward == snapshot.miningReward)
    assert(restored.wallets == snapshot.wallets)
    assert(restored.mempool == snapshot.mempool)
    assert(restored.chain == snapshot.chain)
  }

  test("wallet directory creates a valid signed transaction") {
    val runtime = ActorRuntime.fromSnapshot(DemoData.initialSnapshot())
    val pending = runtime.mempool.ask(GetPendingOutgoing("alice", _))

    val result = runtime.walletDirectory.ask(CreateTransaction("alice", "bob", BigDecimal(10), pending, _))

    assert(result.isRight)
    result match {
      case Right(tx) =>
        assert(tx.from == "alice")
        assert(tx.to == "bob")
        assert(tx.amount == BigDecimal(10))
      case Left(error) =>
        fail(error)
    }
  }

  test("mempool accepts a valid transaction and validator can mine it") {
    val runtime = ActorRuntime.fromSnapshot(DemoData.initialSnapshot())
    val pending = runtime.mempool.ask(GetPendingOutgoing("alice", _))
    val tx = runtime.walletDirectory.ask(CreateTransaction("alice", "diana", BigDecimal(5), pending, _)).toOption.get

    assert(runtime.mempool.ask(TryAddTransaction(tx, runtime.walletDirectory, _)).isRight)

    val mined = runtime.validator("validator-1").get.ask(MineOnce(logEvery = Long.MaxValue, attemptDelayMs = 0L, _))
    assert(mined.isRight)

    val snapshot = runtime.snapshot()
    assert(snapshot.chain.size == 2)
    assert(snapshot.mempool.isEmpty)
    assert(ChainVerifier.isValid(snapshot))
  }
}
