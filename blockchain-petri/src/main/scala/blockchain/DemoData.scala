package blockchain

object DemoData {
  def initialSnapshot(): BlockchainSnapshot = {
    val wallets = Vector(
      WalletSnapshot("alice", BigDecimal(120), BigDecimal(120), "alice-secret", isValidator = false),
      WalletSnapshot("bob", BigDecimal(75), BigDecimal(75), "bob-secret", isValidator = false),
      WalletSnapshot("charlie", BigDecimal(35), BigDecimal(35), "charlie-secret", isValidator = false),
      WalletSnapshot("diana", BigDecimal(20), BigDecimal(20), "diana-secret", isValidator = false),
      WalletSnapshot("validator-1", BigDecimal(0), BigDecimal(0), "validator-1-secret", isValidator = true),
      WalletSnapshot("validator-2", BigDecimal(0), BigDecimal(0), "validator-2-secret", isValidator = true)
    ).sortBy(_.address)

    val genesis = LedgerActor.genesisBlock()

    BlockchainSnapshot(
      difficulty = 3,
      miningReward = BigDecimal(10),
      wallets = wallets,
      mempool = Vector(
        signedTx(wallets, "alice", "bob", BigDecimal(15)),
        signedTx(wallets, "alice", "charlie", BigDecimal(20)),
        signedTx(wallets, "bob", "diana", BigDecimal(10)),
        signedTx(wallets, "charlie", "alice", BigDecimal(5))
      ),
      chain = Vector(genesis)
    )
  }

  private def signedTx(
      wallets: Vector[WalletSnapshot],
      from: String,
      to: String,
      amount: BigDecimal
  ): Transaction = {
    val sender = wallets.find(_.address == from).getOrElse(
      throw new IllegalArgumentException(s"Wallet introuvable : $from")
    )
    val payload = s"$from|$to|${Transaction.formatAmount(amount)}"
    val signature = CryptoUtils.sha256(payload + sender.secret)
    Transaction(from, to, amount, signature)
  }
}
