package blockchain

final case class WalletSnapshot(
    address: String,
    balance: BigDecimal,
    initialBalance: BigDecimal,
    secret: String,
    publicKey: String,
    isValidator: Boolean
)

final case class Transaction(
    from: String,
    to: String,
    amount: BigDecimal,
    fees: BigDecimal,
    timestamp: Long,
    publicKey: String,
    signature: String
) {
  def payload: String =
    s"$from|$to|${Transaction.formatAmount(amount)}|${Transaction.formatAmount(fees)}|$timestamp"

  def legacyPayload: String = s"$from|$to|${Transaction.formatAmount(amount)}"

  def totalDebit: BigDecimal = amount + fees

  override def toString: String =
    s"Transaction(from=$from, to=$to, amount=${Transaction.formatAmount(amount)}, fees=${Transaction.formatAmount(fees)}, timestamp=$timestamp, signature=$signature)"
}

object Transaction {
  val SystemAddress: String = "SYSTEM"
  val SystemSignature: String = "SYSTEM"
  val SystemPublicKey: String = "SYSTEM"

  def reward(to: String, amount: BigDecimal): Transaction =
    Transaction(SystemAddress, to, amount, BigDecimal(0), System.currentTimeMillis(), SystemPublicKey, SystemSignature)

  def formatAmount(amount: BigDecimal): String =
    amount.bigDecimal.stripTrailingZeros.toPlainString
}

final case class Block(
    index: Int,
    previousHash: String,
    transactions: Vector[Transaction],
    validator: String,
    timestamp: Long = System.currentTimeMillis(),
    var nonce: Long = 0L,
    var hash: String = ""
) {
  def computeHash: String = {
    val txData = transactions
      .map { tx =>
        s"${tx.from}->${tx.to}:${Transaction.formatAmount(tx.amount)}:${Transaction.formatAmount(tx.fees)}:${tx.timestamp}:${tx.publicKey}:${tx.signature}"
      }
      .mkString(";")

    val raw = s"$index|$previousHash|$txData|$validator|$timestamp|$nonce"
    CryptoUtils.sha256(raw)
  }

  def mine(difficulty: Int, onAttempt: (Long, String) => Unit = (_, _) => ()): Unit = {
    val prefix = "0" * difficulty
    hash = computeHash
    onAttempt(nonce, hash)

    while (!hash.startsWith(prefix)) {
      nonce += 1L
      hash = computeHash
      onAttempt(nonce, hash)
    }
  }
}

final case class BlockchainSnapshot(
    difficulty: Int,
    miningReward: BigDecimal,
    wallets: Vector[WalletSnapshot],
    mempool: Vector[Transaction],
    chain: Vector[Block]
)

final case class LedgerSnapshot(
    difficulty: Int,
    miningReward: BigDecimal,
    chain: Vector[Block]
) {
  def nextIndex: Int = chain.size
  def lastHash: String = chain.last.hash
}
