package blockchain

object DemoData {
  def initialSnapshot(): BlockchainSnapshot = {
    val alice = wallet("alice", 120, isValidator = false)
    val bob = wallet("bob", 75, isValidator = false)
    val charlie = wallet("charlie", 35, isValidator = false)
    val diana = wallet("diana", 20, isValidator = false)
    val validator1 = wallet("validator-1", 0, isValidator = true)
    val validator2 = wallet("validator-2", 0, isValidator = true)

    val wallets = Vector(
      alice,
      bob,
      charlie,
      diana,
      validator1,
      validator2
    ).sortBy(_.address)

    val genesis = LedgerActor.genesisBlock()

    BlockchainSnapshot(
      difficulty = 3,
      miningReward = BigDecimal(10),
      wallets = wallets,
      mempool = Vector(
        signedTx(wallets, "alice", "bob", BigDecimal(15), BigDecimal(0.4), 1L),
        signedTx(wallets, "alice", "charlie", BigDecimal(20), BigDecimal(0.1), 2L),
        signedTx(wallets, "bob", "diana", BigDecimal(10), BigDecimal(0.8), 3L),
        signedTx(wallets, "charlie", "alice", BigDecimal(5), BigDecimal(0.2), 4L)
      ),
      chain = Vector(genesis)
    )
  }

  private def wallet(address: String, initialBalance: BigDecimal, isValidator: Boolean): WalletSnapshot = {
    val (publicKey, privateKey) = CryptoUtils.generateKeyPair()
    WalletSnapshot(address, initialBalance, initialBalance, privateKey, publicKey, isValidator)
  }

  private def signedTx(
      wallets: Vector[WalletSnapshot],
      from: String,
      to: String,
      amount: BigDecimal,
      fees: BigDecimal,
      timestampSeed: Long
  ): Transaction = {
    val sender = wallets.find(_.address == from).getOrElse(
      throw new IllegalArgumentException(s"Wallet introuvable : $from")
    )
    val timestamp = System.currentTimeMillis() + timestampSeed
    val unsigned = Transaction(from, to, amount, fees, timestamp, sender.publicKey, signature = "")
    val signature = CryptoUtils.sign(unsigned.payload, sender.secret)
    unsigned.copy(signature = signature)
  }
}
