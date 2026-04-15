messages :
1. Wallet → Mempool : SignedTransaction
2. Mempool : (dans son coin)
   - vérifie signature
   - stocke
3. Validator → Mempool : GetTopTransactions
4. Mempool → Validator : Transactions
5. Validator → Blockchain : GetLastBlock
6. Blockchain → Validator : Block
7. Validator : (dans son coin)
   - mine (PoW async)
   - construit bloc
8. Validator → Blockchain : AppendBlock
9. Blockchain : (dans son coin)
   - vérifie bloc
   - met à jour comptes
10. Blockchain → Mempool : RemoveTransactions
