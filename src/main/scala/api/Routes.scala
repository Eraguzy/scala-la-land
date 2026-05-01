package api

import akka.actor.typed._
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.util.Timeout
import messages._
import spray.json._
import JsonFormats._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Routes(
    registry: ActorRef[Registry.Command],
    db: ActorRef[DB.Command],
    mempool: ActorRef[Mempool.Command]
)(implicit system: ActorSystem[_]) {

  // 30s because CreateTx waits for the validator to actually mine the block —
  // mining runs every 5s and there may be a queue of pending txs ahead
  implicit val timeout: Timeout         = Timeout(30.seconds)
  implicit val ec: ExecutionContext     = system.executionContext
  implicit val scheduler: Scheduler    = system.scheduler

  // Helper: resolve wallet ref then run f, or 404
  private def withWallet(name: String)(f: ActorRef[Wallet.Command] => Route): Route =
    onSuccess(registry.ask[Registry.GetWalletResponse](ref => Registry.GetWalletRef(name, ref))) {
      case Registry.WalletFound(ref) => f(ref)
      case Registry.WalletNotFound   => complete(StatusCodes.NotFound, ErrorResponse(s"Wallet '$name' not found").toJson)
    }

  val routes: Route =
    respondWithHeader(akka.http.scaladsl.model.headers.RawHeader("Access-Control-Allow-Origin", "*")) {
      concat(
        // ── API ───────────────────────────────────────────────────────────────
        pathPrefix("api") {
          concat(

            // GET  /api/wallets
            // POST /api/wallets
            path("wallets") {
              concat(
                get {
                  onSuccess(registry.ask[Registry.WalletsListed](ref => Registry.ListWallets(ref))) { listed =>
                    complete(listed.wallets.map(w => WalletListItem(w.name)).toJson)
                  }
                },
                post {
                  entity(as[CreateWalletRequest]) { req =>
                    if (req.initialBalance < 0) {
                      complete(StatusCodes.BadRequest, ErrorResponse("Initial balance cannot be negative").toJson)
                    } else {
                      onSuccess(registry.ask[Registry.CreateWalletResponse](
                        ref => Registry.CreateWallet(req.name, BigInt(req.initialBalance), ref)
                      )) {
                        case Registry.WalletCreated(name)  =>
                          complete(StatusCodes.Created, WalletResponse(name).toJson)
                        case Registry.WalletAlreadyExists  =>
                          complete(StatusCodes.Conflict,   ErrorResponse(s"Wallet '${req.name}' already exists").toJson)
                        case Registry.NegativeBalance      =>
                          complete(StatusCodes.BadRequest, ErrorResponse("Initial balance cannot be negative").toJson)
                      }
                    }
                  }
                }
              )
            },

            // GET /api/wallets/:name/balance
            path("wallets" / Segment / "balance") { name =>
              get {
                withWallet(name) { ref =>
                  onSuccess(ref.ask[BigInt](replyTo => Wallet.GetBalance(replyTo))) { balance =>
                    complete(BalanceResponse(balance.toLong).toJson)
                  }
                }
              }
            },

            // GET /api/wallets/:name/pubkey
            path("wallets" / Segment / "pubkey") { name =>
              get {
                withWallet(name) { ref =>
                  onSuccess(ref.ask[String](replyTo => Wallet.GetPublicKey(replyTo))) { pk =>
                    complete(PubKeyResponse(pk).toJson)
                  }
                }
              }
            },

            // POST /api/wallets/:name/transfer
            path("wallets" / Segment / "transfer") { name =>
              post {
                entity(as[TransferRequest]) { req =>
                  withWallet(name) { ref =>
                    onSuccess(ref.ask[Boolean](
                      replyTo => Wallet.CreateTx(req.to, BigInt(req.amount), BigInt(req.fee), replyTo)
                    )) { accepted =>
                      val msg = if (accepted) "Transaction confirmée et minée"
                                else "Transaction refusée (fonds insuffisants ou invalide)"
                      complete(TransferResponse(accepted, msg).toJson)
                    }
                  }
                }
              }
            },

            // GET /api/blockchain
            path("blockchain") {
              get {
                onSuccess(db.ask[DB.BlocksData](replyTo => DB.GetAllBlocks(replyTo))) { data =>
                  val blocks = data.blocks.map { b =>
                    BlockInfoResponse(b.id, b.prevHash, b.timestamp,
                      b.txs.map(t => TxInfoResponse(t.from, t.to, t.amount)))
                  }
                  complete(blocks.toJson)
                }
              }
            },

            // GET /api/mempool
            path("mempool") {
              get {
                onSuccess(mempool.ask[Mempool.PendingView](replyTo => Mempool.ViewPending(replyTo))) { view =>
                  val txs = view.txs.map { t =>
                    MempoolTxResponse(t.txId, t.from, t.to, t.amount.toLong, t.fee.toLong, t.timestamp)
                  }
                  complete(txs.toJson)
                }
              }
            }
          )
        },

        // ── Static frontend ────────────────────────────────────────────────────
        pathSingleSlash {
          getFromFile("frontend/index.html")
        },
        getFromDirectory("frontend")
      )
    }
}
