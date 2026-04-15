package blockchain

import scala.collection.mutable

// Verificateur offline: revalide toute la chaine sans passer par les acteurs.
// Utilise par le viewer/tests pour controler la coherence globale.
object ChainVerifier {
  def isValid(snapshot: BlockchainSnapshot): Boolean = {
    if (snapshot.chain.isEmpty) return false

    // Etat de reference pour signatures/permissions.
    val walletsByAddress = snapshot.wallets.map(wallet => wallet.address -> wallet).toMap
    // Replay des soldes a partir des soldes initiaux.
    val replayBalances = mutable.Map.empty[String, BigDecimal] ++ snapshot.wallets.map { wallet =>
      wallet.address -> wallet.initialBalance
    }

    val prefix = "0" * snapshot.difficulty
    val genesis = snapshot.chain.head

    if (genesis.index != 0) return false
    if (genesis.previousHash != "0") return false
    if (genesis.validator != "GENESIS") return false
    if (genesis.transactions.nonEmpty) return false
    if (genesis.hash != genesis.computeHash) return false

    // Verification de chaque bloc (hors genesis) en sequence.
    snapshot.chain.drop(1).zipWithIndex.foreach { case (current, offset) =>
      val previous = snapshot.chain(offset)

      if (current.index != offset + 1) return false
      if (current.previousHash != previous.hash) return false
      if (current.hash != current.computeHash) return false
      if (!current.hash.startsWith(prefix)) return false
      if (!walletsByAddress.get(current.validator).exists(_.isValidator)) return false

      val rewardTxs = current.transactions.filter(_.from == Transaction.SystemAddress)
      val normalTxs = current.transactions.filterNot(_.from == Transaction.SystemAddress)

      if (rewardTxs.size != 1) return false
      if (normalTxs.isEmpty) return false

      val rewardTx = rewardTxs.head
      if (rewardTx.to != current.validator) return false
      if (rewardTx.amount != snapshot.miningReward) return false
      if (rewardTx.signature != Transaction.SystemSignature) return false

      normalTxs.foreach { tx =>
        // Verifications elementaires + signature avant simulation de solde.
        val sender = walletsByAddress.getOrElse(tx.from, return false)
        if (!walletsByAddress.contains(tx.to)) return false
        if (tx.amount <= 0) return false
        if (tx.fees < 0) return false
        if (sender.publicKey != tx.publicKey) return false
        val signatureValid =
          if (tx.publicKey.nonEmpty) CryptoUtils.verify(tx.payload, tx.signature, tx.publicKey)
          else CryptoUtils.sha256(tx.legacyPayload + sender.secret) == tx.signature
        if (!signatureValid) return false

        val senderBalance = replayBalances.getOrElse(tx.from, BigDecimal(0))
        if (senderBalance < tx.totalDebit) return false

        replayBalances.update(tx.from, senderBalance - tx.totalDebit)
        replayBalances.update(tx.to, replayBalances.getOrElse(tx.to, BigDecimal(0)) + tx.amount)
      }

      // Ajout de la reward de bloc au validateur.
      replayBalances.update(
        rewardTx.to,
        replayBalances.getOrElse(rewardTx.to, BigDecimal(0)) + rewardTx.amount
      )
    }

    // Les soldes recalcules doivent correspondre exactement aux soldes snapshot.
    snapshot.wallets.forall { wallet =>
      replayBalances.getOrElse(wallet.address, BigDecimal(0)) == wallet.balance
    }
  }
}
