package api

import spray.json._

// --- Request types (frontend → backend) ---
case class CreateWalletRequest(name: String, initialBalance: Long)
case class TransferRequest(to: String, amount: Long, fee: Long)

// --- Response types (backend → frontend) ---
case class ErrorResponse(error: String)
case class WalletResponse(name: String)
case class WalletListItem(name: String)
case class BalanceResponse(balance: Long)
case class PubKeyResponse(pubKey: String)
case class TransferResponse(accepted: Boolean, message: String)
case class TxInfoResponse(from: String, to: String, amount: Double)
case class BlockInfoResponse(id: Long, prevHash: String, timestamp: Long, txs: List[TxInfoResponse])
case class MempoolTxResponse(txId: String, from: String, to: String, amount: Long, fee: Long, timestamp: Long)

object JsonFormats extends DefaultJsonProtocol {
  implicit val createWalletRequestFormat: RootJsonFormat[CreateWalletRequest] = jsonFormat2(CreateWalletRequest.apply)
  implicit val transferRequestFormat: RootJsonFormat[TransferRequest]         = jsonFormat3(TransferRequest.apply)
  implicit val errorResponseFormat: RootJsonFormat[ErrorResponse]             = jsonFormat1(ErrorResponse.apply)
  implicit val walletResponseFormat: RootJsonFormat[WalletResponse]           = jsonFormat1(WalletResponse.apply)
  implicit val walletListItemFormat: RootJsonFormat[WalletListItem]           = jsonFormat1(WalletListItem.apply)
  implicit val balanceResponseFormat: RootJsonFormat[BalanceResponse]         = jsonFormat1(BalanceResponse.apply)
  implicit val pubKeyResponseFormat: RootJsonFormat[PubKeyResponse]           = jsonFormat1(PubKeyResponse.apply)
  implicit val transferResponseFormat: RootJsonFormat[TransferResponse]       = jsonFormat2(TransferResponse.apply)
  implicit val txInfoResponseFormat: RootJsonFormat[TxInfoResponse]           = jsonFormat3(TxInfoResponse.apply)
  implicit val blockInfoResponseFormat: RootJsonFormat[BlockInfoResponse]     = jsonFormat4(BlockInfoResponse.apply)
  implicit val mempoolTxResponseFormat: RootJsonFormat[MempoolTxResponse]     = jsonFormat6(MempoolTxResponse.apply)
}
