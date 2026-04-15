package blockchain

import java.nio.charset.StandardCharsets
import java.util.Base64

// Codec texte des snapshots runtime: stable, lisible et retro-compatible.
object SnapshotCodec {
  private val encoder = Base64.getUrlEncoder.withoutPadding()
  private val decoder = Base64.getUrlDecoder

  private def encode(value: String): String =
    encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))

  private def decode(value: String): String =
    new String(decoder.decode(value), StandardCharsets.UTF_8)

  def write(snapshot: BlockchainSnapshot): String = {
    val builder = new StringBuilder()

    // Format ligne par ligne: META, W, M, B puis T rattachees au dernier bloc B.
    builder.append(s"META|${snapshot.difficulty}|${Transaction.formatAmount(snapshot.miningReward)}\n")

    snapshot.wallets.sortBy(_.address).foreach { wallet =>
      builder.append(
        s"W|${encode(wallet.address)}|${Transaction.formatAmount(wallet.balance)}|${Transaction.formatAmount(wallet.initialBalance)}|${encode(wallet.secret)}|${encode(wallet.publicKey)}|${if (wallet.isValidator) 1 else 0}\n"
      )
    }

    snapshot.mempool.foreach { tx =>
      builder.append(
        s"M|${encode(tx.from)}|${encode(tx.to)}|${Transaction.formatAmount(tx.amount)}|${Transaction.formatAmount(tx.fees)}|${tx.timestamp}|${encode(tx.publicKey)}|${encode(tx.signature)}\n"
      )
    }

    snapshot.chain.foreach { block =>
      builder.append(
        s"B|${block.index}|${encode(block.previousHash)}|${encode(block.validator)}|${block.timestamp}|${block.nonce}|${encode(block.hash)}\n"
      )
      block.transactions.foreach { tx =>
        builder.append(
          s"T|${encode(tx.from)}|${encode(tx.to)}|${Transaction.formatAmount(tx.amount)}|${Transaction.formatAmount(tx.fees)}|${tx.timestamp}|${encode(tx.publicKey)}|${encode(tx.signature)}\n"
        )
      }
    }

    builder.toString()
  }

  def read(content: String): BlockchainSnapshot = {
    val lines = content.split("\r?\n").toVector.filter(_.trim.nonEmpty)

    var difficultyOpt: Option[Int] = None
    var miningRewardOpt: Option[BigDecimal] = None
    var wallets = Vector.empty[WalletSnapshot]
    var mempool = Vector.empty[Transaction]
    val chainBuilder = Vector.newBuilder[Block]
    var currentBlockMeta: Option[(Int, String, String, Long, Long, String)] = None
    var currentBlockTransactions = Vector.empty[Transaction]

    def flushCurrentBlock(): Unit = {
      // Commit le bloc en tampon (avec ses transactions) au prochain B ou a la fin du fichier.
      currentBlockMeta.foreach { case (index, previousHash, validator, timestamp, nonce, hash) =>
        chainBuilder += Block(
          index = index,
          previousHash = previousHash,
          transactions = currentBlockTransactions,
          validator = validator,
          timestamp = timestamp,
          nonce = nonce,
          hash = hash
        )
      }
      currentBlockMeta = None
      currentBlockTransactions = Vector.empty
    }

    lines.foreach { line =>
      val parts = line.split("\\|", -1).toVector
      parts.headOption match {
        case Some("META") =>
          if (parts.length != 3) throw new IllegalArgumentException("Ligne META invalide")
          difficultyOpt = Some(parts(1).toInt)
          miningRewardOpt = Some(BigDecimal(parts(2)))

        case Some("W") =>
          if (parts.length == 6) {
            // Ancien format wallet sans cle publique.
            wallets :+= WalletSnapshot(
              address = decode(parts(1)),
              balance = BigDecimal(parts(2)),
              initialBalance = BigDecimal(parts(3)),
              secret = decode(parts(4)),
              publicKey = "",
              isValidator = parts(5) == "1"
            )
          } else if (parts.length == 7) {
            wallets :+= WalletSnapshot(
              address = decode(parts(1)),
              balance = BigDecimal(parts(2)),
              initialBalance = BigDecimal(parts(3)),
              secret = decode(parts(4)),
              publicKey = decode(parts(5)),
              isValidator = parts(6) == "1"
            )
          } else {
            throw new IllegalArgumentException("Ligne wallet invalide")
          }

        case Some("M") =>
          if (parts.length == 5) {
            // Ancien format transaction sans fees/timestamp/publicKey.
            mempool :+= Transaction(
              from = decode(parts(1)),
              to = decode(parts(2)),
              amount = BigDecimal(parts(3)),
              fees = BigDecimal(0),
              timestamp = 0L,
              publicKey = "",
              signature = decode(parts(4))
            )
          } else if (parts.length == 8) {
            mempool :+= Transaction(
              from = decode(parts(1)),
              to = decode(parts(2)),
              amount = BigDecimal(parts(3)),
              fees = BigDecimal(parts(4)),
              timestamp = parts(5).toLong,
              publicKey = decode(parts(6)),
              signature = decode(parts(7))
            )
          } else {
            throw new IllegalArgumentException("Ligne mempool invalide")
          }

        case Some("B") =>
          if (parts.length != 7) throw new IllegalArgumentException("Ligne bloc invalide")
          flushCurrentBlock()
          currentBlockMeta = Some(
            (
              parts(1).toInt,
              decode(parts(2)),
              decode(parts(3)),
              parts(4).toLong,
              parts(5).toLong,
              decode(parts(6))
            )
          )

        case Some("T") =>
          if (parts.length == 5) {
            currentBlockTransactions :+= Transaction(
              from = decode(parts(1)),
              to = decode(parts(2)),
              amount = BigDecimal(parts(3)),
              fees = BigDecimal(0),
              timestamp = 0L,
              publicKey = "",
              signature = decode(parts(4))
            )
          } else if (parts.length == 8) {
            currentBlockTransactions :+= Transaction(
              from = decode(parts(1)),
              to = decode(parts(2)),
              amount = BigDecimal(parts(3)),
              fees = BigDecimal(parts(4)),
              timestamp = parts(5).toLong,
              publicKey = decode(parts(6)),
              signature = decode(parts(7))
            )
          } else {
            throw new IllegalArgumentException("Ligne transaction de bloc invalide")
          }

        case Some(other) =>
          throw new IllegalArgumentException(s"Type de ligne inconnu : $other")

        case None =>
          ()
      }
    }

    flushCurrentBlock()

    BlockchainSnapshot(
      difficulty = difficultyOpt.getOrElse(throw new IllegalArgumentException("META manquant")),
      miningReward = miningRewardOpt.getOrElse(throw new IllegalArgumentException("META manquant")),
      wallets = wallets.sortBy(_.address),
      mempool = mempool,
      chain = chainBuilder.result()
    )
  }
}
