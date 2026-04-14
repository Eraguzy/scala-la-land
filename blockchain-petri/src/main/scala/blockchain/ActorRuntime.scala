package blockchain

import WalletDirectoryMessage._
import MempoolMessage._
import LedgerMessage._

final class ActorRuntime(
    val walletDirectory: ActorRef[WalletDirectoryMessage],
    val mempool: ActorRef[MempoolMessage],
    val ledger: ActorRef[LedgerMessage],
    val validators: Map[String, ActorRef[ValidatorMessage]]
) {
  def snapshot(): BlockchainSnapshot = {
    val wallets = walletDirectory.ask(GetAllWallets).sortBy(_.address)
    val mempoolTransactions = mempool.ask(GetTransactions)
    val ledgerSnapshot = ledger.ask(GetSnapshot)

    BlockchainSnapshot(
      difficulty = ledgerSnapshot.difficulty,
      miningReward = ledgerSnapshot.miningReward,
      wallets = wallets,
      mempool = mempoolTransactions,
      chain = ledgerSnapshot.chain
    )
  }

  def validator(address: String): Option[ActorRef[ValidatorMessage]] = validators.get(address)
}

object ActorRuntime {
  def fromSnapshot(snapshot: BlockchainSnapshot): ActorRuntime = {
    val walletRefs = snapshot.wallets.map { wallet =>
      wallet.address -> new WalletActor(wallet).ref
    }.toMap

    val walletDirectory = new WalletDirectoryActor(walletRefs).ref
    val mempool = new MempoolActor(snapshot.mempool).ref
    val ledger = new LedgerActor(snapshot.chain, snapshot.difficulty, snapshot.miningReward).ref
    val validators = snapshot.wallets.filter(_.isValidator).map { wallet =>
      wallet.address -> new ValidatorActor(wallet.address, walletDirectory, mempool, ledger).ref
    }.toMap

    new ActorRuntime(walletDirectory, mempool, ledger, validators)
  }
}
