package objects

import java.util.UUID

case class UnsignedTransaction(
                                id: String,           // ID unique (ex: UUID)
                                from: String,         // Public Key de l'expéditeur
                                to: String,           // Public Key du destinataire
                                amount: BigInt,       // Montant
                                fees: BigInt,         // Frais pour la Priority Queue
                                timestamp: Long       // Date de création
                              )

case class SignedTransaction(
                              tx: UnsignedTransaction,
                              signature: String
                            )