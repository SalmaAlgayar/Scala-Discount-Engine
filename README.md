# 🛍️ Discount Engine: Referentially Transparent Edition 
<img width="1920" height="1080" alt="PIXEL (1)" src="https://github.com/user-attachments/assets/e0460b9e-4b75-47ae-9585-7dcb7343f6c5" />


---

## 🐱‍💻The Problem (a.k.a. Why I'm Even Doing This)
My Scala instructor at ITI just dropped a massive CSV scroll on our desks. It’s packed with about a thousand rows of people buying cheese, wine, and other... "interesting" life choices.

The Mission: He told us to calculate discounts based on four totally random rules (Is it about to expire? Is it cheese? Was it bought on March 23rd? Did they buy a literal truckload?) and then shove all that data into a database.

The Temptation: My first thought was to write "normal" code. You know, where you’re reading files, doing math, and screaming at a database all in the same breath. In the professional world, we call those Side Effects. They're like uninvited party guests: they're messy, they break things, and they make it impossible to clean up afterward. If I wrote it that way, the code would turn into a plate of spaghetti so tangled that testing it would make me cry, and debugging it would make me want to crawl under my desk and never come out.

The Solution: Instead, I decided to be "fancy" and use Functional Programming. I’m basically putting all those scary "Side Effects" (the file reading and the database talking) into soundproof cages called IO. This way, my actual logic stays pure, clean, and safe. Honestly? It’s kind of a vibe.

---
## 🧮 The Math Dungeon: How Functions Hold Hands and Skip Together

Okay, so the instructor said no loops, no vars, no mutating stuff. At first I was like, "How am I supposed to do anything?" But then I realized: in Functional Programming, you don't tell the computer how to do stuff step by step. You just describe what you want, and then you chain tiny functions together like snapping Lego pieces into one clean line.

Here's the secret recipe happening inside OrderHelpers:

---

### 🔗 Step 1: The Parsing Portal

First, I take a bit dusty line of CSV—something like:

`"2024-03-23T10:00Z, cheese-cheddar, 2024-04-01, 3, 4.99, online, card"`

—and I throw it at `parseOrderSafe`.

```scala
def parseOrderSafe(line: String): Option[Order] = { ... }
```

This function is total and pure. It either hands me a shiny `Order` inside a `Some()`, or it hands me a `None` and says, "Nope, this line is garbage, figure it out yourself." No exceptions. Just an `Option` that I can deal with later.

---

### 🧠 Step 2: The Rulebook as Data

Instead of writing a giant if-else monster, I store all four discount rules in a plain old List:

```scala
val discountRules: List[(Order => Boolean, Order => Float)] = List(
  (isAQualified, calcA),
  (isBQualified, calcB),
  (isCQualified, calcC),
  (isDQualified, calcD)
)
```

Look at that type! Each rule is a pair of functions:

- The first function asks: "Does this order deserve this discount?" (returns Boolean)  
- The second function says: "If so, here's how much percent off." (returns Float)  

Because they're just data, I can add new rules, remove old ones, or swap them out for testing without touching any of the pipeline code.

---

### 🧪 Step 3: The Pipeline That Does the Heavy Lifting

This is where the magic happens. Inside `calculateDiscountPercentage`, I do four things in a row, without a single loop or var:

```scala
val applicable = rules
  .filter { case (qr, _) => qr(order) }   // Keep only rules where the qualifier says "yes"
  .map    { case (_, cr) => cr(order) }   // Transform each qualifying rule into a discount number
  .sortBy(-_)                             // Sort them biggest first (the minus means descending)
```

- **filter** – It acts like a doorman. Only rules that pass the qualifier get in.  
- **map** – For the lucky rules that got in, we run the calculator function to get a number.  
- **sortBy(-_)** – We line up those numbers from biggest to smallest.  
- **take(2)** – Grab the top two. (Because the instructors's decree says "average of the two highest discounts".)  
- **Average them** – Sum divided by count. If there were none, we just return `0.0f`.  

No loops. No mutable counters. Just data flowing through tiny pure functions like water through a fancy espresso machine.

---

### 🎁 Step 4: Wrapping It All Up

Finally, `createOrderWithDiscount` takes an `Order`, feeds it into the discount-calculating machine, and gives an `OrderWithDiscount` with the final price all figured out.

```scala
val pct = calculateDiscountPercentage(order, rules)
val finalPrice = order.unit_price * order.quantity * (1 - (pct / 100.0f))
```

It's just multiplication. No database calls. No file reads. This part of the code is so pure I could run it on a calculator.

---

### 🌚 Why This Feels Like Cheating (In a Good Way)

In regular Java-style code, I'd probably have a big for loop with a bunch of if statements, a temporary ArrayList to collect discounts, and then I'd sort it with `Collections.sort()`. It works, but it's messy and hard to test.

With this pipeline style:

- I can test `isBQualified` by itself. Does it recognize `"wine-merlot"`? Yep.  
- I can test `calculateDiscountPercentage` by giving it a fake order and a custom rule list. No CSV needed.  
- If the instructor adds a fifth rule next week, I just add one line to `discountRules`. Done.  

It's like building with Legos instead of stitching parts together. Everything clicks into place, and if I mess up, I only have to replace one tiny brick.

There you have it. The pure logic pipeline is just a bunch of small, honest functions holding hands and passing data along. No side effects allowed in this part of town. The scary file I/O and database stuff lives somewhere else, safely caged in IO.

---

## 🧠 The Genius Plan: Two Separate Worlds

| 🟢 Pure World (Safe Space) | 🔴 Impure World (The Danger Zone) |
|---------------------------|----------------------------------|
| Math, rules, parsing strings | Reading files, talking to Postgres, logging |
| Works the same every single time | Might explode if the file is missing |
| I can test it without crying | I keep it at the very edges of my app |
| Zero surprises | Handled with IO cages |

Everything messy lives in IO boxes. I don't open the boxes until the absolute last second. It's like telling your mom you'll clean your room… eventually.

---

## 📦 The IO Box (Cats Effect Magic)

IO is just a description of something you want to do. It's not the actual doing.

```scala
val readTheFile: IO[List[String]] = IO.blocking {
  // This whole block is just a note. The file isn't touched yet!
  Source.fromFile("stuff.csv").getLines().toList
}
```

I can pass `readTheFile` around like a trading card. I can say "if this fails, do that." I can combine it with other IO notes. Nothing actually happens until the program reaches the very end and says "Okay, for real now." This is called lazy evaluation!

---

## 🐘 Doobie the Database Gryphon

Talking to Postgres is scary. You have to open a connection, run a query, and remember to close it or the Gryphon gets indigestion and crashes the whole kingdom.

Doobie handles that for me. It lets me write a plain description of the database work:

```scala
val insertOrders: ConnectionIO[Int] = Update[OrderWithDiscount](sql).updateMany(myOrders)
```

Then I wrap it in a `transact(xa)` and boom—it becomes an IO box. The Gryphon is fed, watered, and put back in its cage. Every. Single. Time.

---

## 🛤️ The Whole Pipeline (In Plain English)

1. Read the CSV (danger zone → IO box)  
2. Parse each line into an Order (pure, returns Option in case the line is junk)  
3. Throw away the bad lines (goodbye, jam stains)  
4. Run the discount rules (pure, fast, testable)  
5. Log some stuff (impure, but safely IO‑ified)  
6. Shove it all into Postgres (Doobie handles the Gryphon)  
7. Handle any errors gracefully (no crashes, just a sad log message)  

All of this is glued together with a for‑comprehension that looks super clean:

```scala
for {
      _     <- Logger[IO].info("--- Processing Started ---")
      lines <- readLines(path)
      _     <- Logger[IO].info(s"File read. Found ${lines.size} records.")
      ordersWithDiscount = lines.tail.flatMap(OrderHelpers.parseOrderSafe).map(o => OrderHelpers.createOrderWithDiscount(o, OrderHelpers.discountRules))
      _     <- Logger[IO].info(s"Orders processed. Resulted in ${ordersWithDiscount.size} valid records.")
      rows  <- saveToDb(ordersWithDiscount, xa)
      _     <- Logger[IO].info(s"Success! $rows records inserted.")
    } yield ExitCode.Success
```

It's like a to‑do list for the computer, and the computer doesn't cheat.

---

## 🧪 Why This Is Solid

- I can test the discount logic instantly without a database or a CSV file.  
- If the file is missing, the program just logs "Oops, file not found" and exits politely.  
- I can change the discount rules without touching the file reading or database code.  
- Everything is explicit. I know exactly where side effects happen because I see IO in the type signature.  

---

## 🚀 How to Run This Thing

1. Have PostgreSQL running (I use Docker).  
2. Create a database called `ordersdb`.  
3. Create the `ORDERS` table (check the SQL in `saveToDb`).  
4. Put your `TRX1000.csv` in `src/main/resources/`.  
5. Run `sbt run`.  

If it works, you'll see happy logs. If it doesn't, you'll see a sad log, but the program won't explode.

---

## 📚 Stuff I Used (My Backpack)

| Thing | Why I Used It |
|------|--------------|
| Scala | Because Java makes me write too much |
| Cats Effect | For the IO boxes that tame side effects |
| Doobie | To talk to Postgres without losing my mind |
| Log4Cats | Fancy logging that plays nice with IO |
| Pure Functions | The only part of this project that doesn't give me anxiety |

---

## 🤓 Final Thought (From Me)

Functional Programming isn't about being a math genius. It's about keeping the scary stuff in a cage and the safe stuff where you can see it. Once you get that, you'll never want to go back to spaghetti code again.

Now if you’ll excuse me, I have to finish this README. Sigh.

---

**The End. 🧀**
