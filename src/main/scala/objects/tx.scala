package objects

case class UnsignedTransaction(
                                from: String,
                                to: String,
                                amount: BigInt
                              )

case class SignedTransaction(
                              tx: UnsignedTransaction,
                              signature: String
                            )