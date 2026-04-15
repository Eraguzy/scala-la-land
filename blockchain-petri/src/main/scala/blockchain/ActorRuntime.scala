package blockchain

import WalletDirectoryMessage._
import MempoolMessage._
import LedgerMessage._

// Regroupe les références d'acteurs nécessaires à une exécution de commande.
// Un runtime est reconstruit à partir du snapshot, utilisé, puis arrêté.
final class ActorRuntime(
    val walletDirectory: ActorRef[WalletDirectoryMessage],
    val mempool: ActorRef[MempoolMessage],
    val ledger: ActorRef[LedgerMessage],
    val validators: Map[String, ActorRef[ValidatorMessage]],
    private val allRefs: Vector[ActorRef[_]]
) {
  // Construit un snapshot cohérent en interrogeant l'état courant des acteurs.
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

  // Arrête tous les acteurs créés pour cette instance de runtime.
  // Important pour éviter l'accumulation d'acteurs lors des commandes répétées.
  def shutdown(): Unit = {
    SimpleActor.stopAll(allRefs)
  }
}

object ActorRuntime {
  // Reconstruit tout le graphe d'acteurs en mémoire depuis un snapshot persistant.
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

    val allRefs = walletRefs.values.toVector ++ Vector(walletDirectory, mempool, ledger) ++ validators.values

    new ActorRuntime(walletDirectory, mempool, ledger, validators, allRefs)
  }
}
