package blockchain

import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ConsoleRenderer {
  private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

  def render(runtime: ActorRuntime, runtimeDir: Path): String = {
    val snapshot = runtime.snapshot()
    val builder = new StringBuilder()
    val wallets = snapshot.wallets.sortBy(_.address)
    val validators = wallets.filter(_.isValidator)
    val normalWallets = wallets.filterNot(_.isValidator)
    val recentBlocks = snapshot.chain.takeRight(8)

    builder.append("================= BLOCKCHAIN VIEWER =================\n")
    builder.append(s"State directory : ${runtimeDir.toAbsolutePath}\n")
    builder.append(s"Last refresh    : ${formatter.format(Instant.now())}\n")
    builder.append(s"Difficulty      : ${snapshot.difficulty}\n")
    builder.append(s"Mining reward   : ${formatAmount(snapshot.miningReward)}\n")
    builder.append(s"Chain length    : ${snapshot.chain.size} bloc(s)\n")
    builder.append(s"Mempool size    : ${snapshot.mempool.size} transaction(s)\n")
    builder.append(s"Chain valid     : ${ChainVerifier.isValid(snapshot)}\n")
    builder.append('\n')

    builder.append("-------------------- VALIDATORS --------------------\n")
    if (validators.isEmpty) {
      builder.append("Aucun validateur enregistré.\n")
    } else {
      validators.foreach { wallet =>
        val pending = snapshot.mempool.filter(_.from == wallet.address).map(_.totalDebit).sum
        builder.append(
          f"- ${wallet.address}%-14s | balance=${formatAmount(wallet.balance)}%-8s | pending=${formatAmount(pending)}%s\n"
        )
      }
    }
    builder.append('\n')

    builder.append("---------------------- WALLETS ---------------------\n")
    if (normalWallets.isEmpty) {
      builder.append("Aucun wallet utilisateur.\n")
    } else {
      normalWallets.foreach { wallet =>
        val pending = snapshot.mempool.filter(_.from == wallet.address).map(_.totalDebit).sum
        val available = wallet.balance - pending
        builder.append(
          f"- ${wallet.address}%-14s | balance=${formatAmount(wallet.balance)}%-8s | pending=${formatAmount(pending)}%-8s | available=${formatAmount(available)}%s\n"
        )
      }
    }
    builder.append('\n')

    builder.append("---------------------- MEMPOOL ---------------------\n")
    val topForNextBlock = snapshot.mempool.take(2)
    val topFees = topForNextBlock.map(_.fees).sum
    val potentialMinerGain = snapshot.miningReward + topFees
    builder.append(
      s"Reward bloc=${formatAmount(snapshot.miningReward)} | Gain mineur estimé (reward + fees top2)=${formatAmount(potentialMinerGain)}\n"
    )
    if (snapshot.mempool.isEmpty) {
      builder.append("Mempool vide.\n")
    } else {
      snapshot.mempool.zipWithIndex.foreach { case (tx, idx) =>
        val score = tx.amount * tx.fees
        builder.append(
          s"[$idx] ${tx.from} -> ${tx.to} | amount=${formatAmount(tx.amount)} | fees=${formatAmount(tx.fees)} | score=${formatAmount(score)} | sig=${shorten(tx.signature, 16)}\n"
        )
      }
    }
    builder.append('\n')

    builder.append("-------------------- BLOCKCHAIN --------------------\n")
    recentBlocks.foreach { block =>
      val minedAt = formatter.format(Instant.ofEpochMilli(block.timestamp))
      builder.append(
        s"#${block.index} | validator=${block.validator} | txs=${block.transactions.size} | nonce=${block.nonce} | hash=${shorten(block.hash, 18)} | time=$minedAt\n"
      )
      block.transactions.foreach { tx =>
        val kind = if (tx.from == Transaction.SystemAddress) "reward" else "tx"
        builder.append(
          s"    - [$kind] ${tx.from} -> ${tx.to} | amount=${formatAmount(tx.amount)} | fees=${formatAmount(tx.fees)}\n"
        )
      }
    }

    builder.toString()
  }

  def clearScreen(): Unit = {
    print("\u001b[H\u001b[2J")
    System.out.flush()
  }

  private def formatAmount(amount: BigDecimal): String =
    Transaction.formatAmount(amount)

  private def shorten(value: String, size: Int): String = {
    if (value.length <= size) value else value.take(size) + "..."
  }
}
