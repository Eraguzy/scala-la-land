# Blockchain Akka

> A fully functional blockchain simulation built with **Scala 3** and **Akka Typed actors**, featuring a REST API and a live web dashboard.

---

## Overview

This project implements a minimal but complete blockchain from scratch, without any external blockchain library. Every component — from RSA key generation and transaction signing to Proof-of-Work mining and ledger persistence — is built by hand.

The system runs as a network of concurrent **Akka Typed actors**, each responsible for a single well-defined role. Communication happens exclusively through typed message passing with no shared mutable state.

Two run modes are available:

| Mode | Command | Description |
|------|---------|-------------|
| **HTTP + Frontend** | `sbt run` | REST API on port 8080 + live web dashboard |
| **Terminal** | `sbt "runMain blockchain.MainTerminal"` | Automated demo scenario, results printed to stdout |

---

## Architecture

```
                         ┌───────────────────────────────────┐
                         │         Akka Actor System         │
                         │                                   │
  HTTP Request           │  ┌─────────────┐                  │
──────────────────►      │  │  Registry   │  spawns          │
  (Routes.scala)         │  │   Actor     │──────────►  WalletActor × N
                         │  └─────────────┘                  │
                         │         │ CreateTx                │
                         │         ▼                         │
                         │  ┌─────────────┐  GetTxs          │
                         │  │   Mempool   │◄────────────────►│
                         │  │   Actor     │  (Priority Queue)│
                         │  └─────────────┘                  │
                         │         │ Txs                     │
                         │         ▼                         │
                         │  ┌─────────────┐  SaveBlock       │
                         │  │  Validator  │─────────────────►│
                         │  │   Actor     │  (every 5s)      │
                         │  └─────────────┘                  │
                         │         │ GetLastBlock            │
                         │         ▼                         │
                         │  ┌─────────────┐                  │
                         │  │   DB Actor  │──► ledger.txt    │
                         │  └─────────────┘                  │
                         └───────────────────────────────────┘
```

---

## Key Features

### Cryptography (from scratch)
- **RSA key generation** — two random 256-bit primes, compute `n`, `phi`, find `e`, derive `d = e⁻¹ mod phi`
- **Transaction signing** — textbook RSA: `signature = hash^d mod n`
- **Signature verification** — `recovered = signature^e mod n`, must match `SHA-256(txData)`
- **SHA-256 hashing** — used for transaction IDs, block hashes, and Proof-of-Work

### Blockchain
- **Proof-of-Work** — tail-recursive mining loop; finds a `nonce` such that `SHA-256(txData-prevHash-timestamp-nonce)` starts with the difficulty prefix
- **Linked blocks** — each block stores the hash of the previous one, forming a tamper-evident chain
- **File persistence** — the ledger is written to `ledger.txt` in a structured text format
- **Balance computation** — replayed from ledger history up to a given timestamp

### Mempool
- **Priority Queue** — pending transactions are sorted by fee (descending); the highest-fee transactions are mined first
- **Signature validation** — transactions with invalid signatures are rejected before entering the queue
- **Sequential wallet processing** — each wallet processes one transaction at a time via an internal queue to prevent nonce/balance race conditions

### HTTP API + Frontend
- Full REST API served by Akka HTTP
- Single-page web dashboard with live polling (blockchain refreshes every 5 s, mempool every 3 s)
- Wallet creation, balance display, transaction sending, blockchain explorer, mempool viewer

---

## Running the Project

### Prerequisites
- JDK 11+
- SBT 1.x

### HTTP Mode (recommended)

```bash
sbt run
```

Open your browser at **http://localhost:8080**

### Terminal Mode

```bash
sbt "runMain blockchain.MainTerminal"
```

Runs a hardcoded scenario:
- **Alice** (500 SAT) sends 3 transactions to Charlie with fees 4, 3, 2
- **Charlie** (300 SAT) sends 2 transactions to Alice with fees 1, 5
- The priority queue mines the highest-fee transactions first
- Press **ENTER** to print final balances and stop

---

## REST API Reference

All endpoints are prefixed with `/api`.

### Wallets

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `GET` | `/api/wallets` | — | List all wallets |
| `POST` | `/api/wallets` | `{"name": "Alice", "initialBalance": 500}` | Create a new wallet |
| `GET` | `/api/wallets/:name/balance` | — | Get current balance |
| `GET` | `/api/wallets/:name/pubkey` | — | Get public key |
| `POST` | `/api/wallets/:name/transfer` | `{"to": "<pubKey>", "amount": 50, "fee": 3}` | Send a transaction |

### Blockchain & Mempool

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/blockchain` | All mined blocks with their transactions |
| `GET` | `/api/mempool` | Pending transactions (priority-ordered) |

### Example: create a wallet and send a transaction

```bash
# Create wallet
curl -X POST http://localhost:8080/api/wallets \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "initialBalance": 500}'

# Get public key
curl http://localhost:8080/api/wallets/Alice/pubkey

# Send transaction (replace <charlieKey> with Charlie's actual public key)
curl -X POST http://localhost:8080/api/wallets/Alice/transfer \
  -H "Content-Type: application/json" \
  -d '{"to": "<charlieKey>", "amount": 50, "fee": 3}'
```

---

## How Mining Works

```
Every 5 seconds:
  1. ValidatorActor polls the Mempool for the top 2 transactions (by fee)
  2. Fetches the last block hash and ID from DBActor
  3. Runs Proof-of-Work:
       nonce = 0
       loop:
         hash = SHA-256(txIds + prevHash + timestamp + nonce)
         if hash starts with DIFFICULTY → found!
         else nonce += 1
  4. Saves the new block to DBActor (written to ledger.txt)
  5. On DB confirmation:
       - notifies each transaction's wallet (accepted = true)
       - removes confirmed transactions from Mempool
```

Current difficulty: `"caca"` (the block hash must begin with those 4 characters).

---

## Transaction Lifecycle

```
  WalletActor.CreateTx(to, amount, fee)
      │
      ▼
  Check balance (ask DBActor)
      │
      ├── insufficient funds → replyTo ! false
      │
      └── sign with RSA (SHA-256 hash, modPow with private key)
              │
              ▼
          MempoolActor.AddTx
              │
              ├── invalid signature → replyTo ! false
              │
              └── insert into priority queue (sorted by fee desc)
                      │
                      ▼ (next 5s tick)
                  ValidatorActor mines block
                      │
                      └── DBActor saves → replyTo ! true
```

---

## Project Structure

```
scala-la-land/
├── build.sbt                        # Dependencies (Akka Typed, Akka HTTP, Spray JSON)
├── ledger.txt                       # Blockchain file (written at runtime)
├── frontend/
│   └── index.html                   # Single-page web dashboard
└── src/main/scala/
    ├── main.scala                   # Entry point — HTTP mode
    ├── MainTerminal.scala           # Entry point — terminal mode
    ├── actors/
    │   ├── blockchain.scala         # DBActor    — ledger read/write, balance queries
    │   ├── mempool.scala            # MempoolActor — priority queue of pending txs
    │   ├── validator.scala          # ValidatorActor — scheduled PoW miner
    │   ├── wallet.scala             # WalletActor — RSA keys, signing, balance tracking
    │   └── registry.scala          # RegistryActor — dynamic wallet management
    ├── messages/
    │   ├── blockchain.scala         # DB command/response types
    │   ├── mempool.scala            # Mempool command/response types
    │   ├── wallet.scala             # Wallet command types
    │   ├── registry.scala          # Registry command/response types
    │   └── validator.scala          # Validator command types
    ├── objects/
    │   ├── block.scala              # Block data class
    │   └── tx.scala                 # UnsignedTransaction, SignedTransaction, PendingTx
    ├── functions/
    │   ├── crypto.scala             # SHA-256, RSA sign/verify, key generation
    │   ├── miner.scala              # Tail-recursive Proof-of-Work
    │   └── utils.scala              # Prime generation, coprime finder
    └── api/
        ├── HttpServer.scala         # Akka HTTP server startup
        ├── Routes.scala             # REST API route definitions
        └── JsonFormats.scala        # Spray JSON serialisation formats
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Scala 3.3.1 |
| Actor model | Akka Typed 2.8.5 |
| HTTP server | Akka HTTP 10.5.3 |
| JSON | Spray JSON (via akka-http-spray-json) |
| Logging | Logback 1.4.14 |
| Cryptography | Hand-rolled RSA + SHA-256 (Java MessageDigest) |
| Persistence | Plain text file (`ledger.txt`) |
| Frontend | Vanilla HTML / CSS / JavaScript (no framework) |

---

## Design Principles

- **No shared mutable state** — every actor holds its state as immutable values passed to the next `behavior()` call (functional state management via recursion)
- **Type-safe messaging** — each actor only accepts its own sealed `Command` trait; wrong message types are caught at compile time
- **Separation of concerns** — each actor has exactly one responsibility; communication between them is explicit and traceable
- **Defense in depth** — validation happens at the frontend (JS), the HTTP route, and inside the actor, ensuring invalid data never propagates deeper than necessary
