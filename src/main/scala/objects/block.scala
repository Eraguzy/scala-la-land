package objects

import objects.SignedTransaction

// blocks are linked together through previousHash — changing any block
// would break all subsequent hashes, which is the whole point of a chain
case class Block(
  id: Long,
  transactions: List[SignedTransaction],
  previousHash: String,
  timestamp: Long
)
