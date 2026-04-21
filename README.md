# 🛍️ Discount Engine: From 1K to 10M – A Functional Journey

<img width="1920" height="1080" alt="PIXEL" src="https://github.com/user-attachments/assets/3daf666a-e278-4d9e-9093-b2d3844c18f9" />

---

## 🧾 The Story So Far

### Chapter 1: The 1,000‑Order Prototype 😽

Our Scala instructor dropped a CSV file of **1,000 orders** on our desks and said: *"Calculate discounts based on four business rules, and shove it all into a database. Oh, and no side-effects, no mutable state, no exceptions."*

I built a **pure functional discount engine**:

- **Pure core:** `OrderHelpers` with referentially transparent qualifiers and calculators.
- **Effect cage:** `IO` for file I/O, logging, and database writes.
- **Clean composition:** A `for`-comprehension that read like a recipe.

It worked beautifully through the `OrdersMain` object. Tests were fast. The code was easy to reason about. Life was good.

### Chapter 2: The 10,000,000‑Order Wake‑Up Call 🐱‍👓

Then came the email: *"Great job. Tomorrow you'll receive **10 million orders per batch**. Please scale."*

The original design loaded the entire CSV into memory. With 10M rows, the JVM would explode. A single database transaction would time out. Sequential processing would take forever.

**I had to scale without rewriting the business logic.**

The solution: **streaming with fs2**. The pure core stayed **exactly the same**. Only the outer shell changed—from `List` to `Stream`, from one big `IO` to a pipeline of parallel, batched effects.

### Chapter 3: New Rules Drop Mid‑Flight 🎟

While scaling, the head of sales added **two new discount rules**:

- **App Channel:** Extra discount for orders placed via the App, with quantity-based tiers.
- **Visa Payment:** Flat 5% off for Visa card payments to promote paperless transactions.

Because the rule engine is just a **list of pure functions**, adding them took two minutes. The streaming pipeline didn't care—it just kept crunching.

---

## 🧠 The Architecture: Two Worlds, Forever Separated

| 🟢 Pure World (`OrderHelpers`) | 🔴 Impure World (`IO` + `Stream`) |
|-------------------------------|----------------------------------|
| Parsing CSV lines → `Option[Order]` | Reading the 10M records file lazily |
| Qualifying rules (`isXQualified`) | Batching rows into chunks |
| Discount calculations (`calcX`) | Concurrent database inserts |
| Averaging top two discounts | Logging progress and errors |

**The rule:** Pure logic never touches I/O. I/O never leaks into pure logic. The boundary is explicit in the types (`IO`, `Stream`).

---

## 📐 The Rule Engine (Pure & Untouched)

All discount rules live in `OrderHelpers`. They are **just data**:

```scala
val discountRules: List[(Order => Boolean, Order => Float)] = List(
  (isAQualified, calcA),
  (isBQualified, calcB),
  (isCQualified, calcC),
  (isDQualified, calcD),
  (isEQualified, calcE),
  (isFQualified, calcF)
)
```

## The Six Rules Explained

| Rule | Qualifier | Calculator |
|------|-----------|------------|
| A | Expires in < 30 days | `30% - daysToExpire` |
| B | Category cheese/wine | `10% cheese`, `5% wine` |
| C | Order date is March 23rd | Flat `50%` |
| D | Quantity > 5 | `5%`, `7%`, `10%` tiers |
| E (App) | `channel == "app"` | `ceil(quantity/5) * 5%` |
| F (Visa) | Payment contains visa | Flat `5%` |

The engine calculates the average of the two highest applicable discounts (or 0% if none apply).

This logic hasn't changed since day one—it's pure, fast, and testable in isolation.

---

## 🌊 The Streaming Pipeline (Scaling Edition)

The `OrdersMainScaled` object processes 10 million orders with constant memory and CPU parallelism.

### 1. Read the File Lazily

```scala
Files[IO].readAll(Path(path))
  .through(text.utf8.decode)
  .through(text.lines)
  .filter(_.trim.nonEmpty)
  .drop(1)
```

This returns a `Stream[IO, String]` that pulls data in small chunks.

### 2. Batch and Parse in Parallel

```scala
lines
  .chunkN(10000)
  .parEvalMapUnordered(cores) { chunk =>
    IO {
      chunk.toList
        .flatMap(OrderHelpers.parseOrderSafe)
        .map(o => OrderHelpers.createOrderWithDiscount(o, discountRules))
    }
  }
```

### 3. Insert into Database Concurrently

```scala
.parEvalMapUnordered(dbParallelism) { batch =>
  saveBatchToDb(batch, xa)
}
```

### 4. The Top-Level Pipeline

```scala
def pipeline(xa: Transactor[IO], path: String): IO[Unit] = {
  Stream.eval(Logger[IO].info("--- Processing Started ---")) >>
    processOrdersStream(readLinesStream(path), chunkSize)
      .parEvalMapUnordered(writeParallelism)(saveBatchToDb(_, xa))
      .onFinalize(Logger[IO].info("--- Completed ---"))
}.compile.drain
```

Nothing runs until `IOApp` executes the `IO`.

---

## 📦 The IO Cage & Doobie

Side effects are wrapped in `IO`:

- File reading: `Files[IO].readAll`
- Logging: `Logger[IO].info`
- Database: `transact(xa)`

```scala
Transactor.fromDriverManager[IO](
  "org.postgresql.Driver",
  "jdbc:postgresql:ordersdb",
  "docker",
  "docker"
)
```

---


## 🚀 Running the Beast

1. Start PostgreSQL (Docker works great).
2. Create database `ordersdb` and the `ORDERS` table (schema given).
3. Drop your massive CSV in `src/main/resources/TRX10M.csv`.
4. `sbt run

## 📚 Tech Stack

| Library | Purpose |
|--------|---------|
| Cats Effect | IO for side-effect management |
| fs2 | Streaming with backpressure |
| Doobie | PostgreSQL access |
| Log4Cats + Logback | Structured logging |
| Pure Scala | Business logic |

---

## 🧠 Key Takeaways

- Pure functional cores scale effortlessly.
- Streaming is not scary.
- Concurrency is manageable.
- Rules as data stay flexible.
- Error isolation keeps pipelines alive.

---

## 🤓 Final Thought

This project started as a tricky exercise and became a lesson in functional architecture under pressure. The code that handles 10 million orders is just as clean and testable as the one that handled 1,000.

Now go forth and process all the cheese. 🧀

**The End.**
