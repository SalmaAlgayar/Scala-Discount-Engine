import models._
import cats.effect.{ExitCode, IO, IOApp}
import doobie.util.transactor.Transactor
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.update.Update
import scala.io.{Codec, Source}
import scala.util.Using
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/**
 * Main entry point for the Orders Discount Engine.
 *
 * This application follows the "functional core, imperative shell" pattern:
 *   - Pure logic resides in `models.OrderHelpers`
 *   - All side effects (file I/O, database writes, logging) are wrapped in `IO`
 *   - The main pipeline composes these `IO` descriptions and executes them
 *     only when the `IOApp` runtime interprets the program.
 */
object OrdersMain extends IOApp {

  // Logger instance (side‑effecting, but safely wrapped in `IO` by the library)
  implicit def logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  /**
   * Creates a Doobie transactor using a plain `DriverManager` connection.
   * No connection pooling – each `transact` block opens and closes its own
   * database connection. Perfect for batch processing.
   *
   * @param driver JDBC driver class name
   * @param url    JDBC connection URL
   * @param user   Database username
   * @param pass   Database password
   * @return A transactor that can run `ConnectionIO` programs in `IO`
   */
  def createTransactor(driver: String, url: String, user: String, pass: String): Transactor[IO] = Transactor.fromDriverManager[IO](driver, url, user, pass)

  /**
   * Reads all lines from a text file, returning them as a list of strings.
   *
   * This operation is a side effect (file system access), so it is wrapped
   * in `IO.blocking` to prevent blocking the main compute thread pool.
   * The `Using.resource` ensures the file handle is closed automatically.
   *
   * @param path  Path to the file
   * @param codec Character encoding (default UTF-8)
   * @return `IO` description that, when executed, yields the list of lines
   */
  def readLines(path: String, codec: String = Codec.UTF8.name): IO[List[String]] = IO.blocking(Using.resource(Source.fromFile(path, codec))(_.getLines().toList))

  /**
   * Inserts a batch of enriched orders into the database.
   *
   * Another side effect wrapped in `IO`. Doobie's `transact` method converts
   * a `ConnectionIO` program into an `IO` that manages the connection lifecycle.
   *
   * @param ordersWithDiscount The list of orders ready for insertion
   * @param xa                 Transactor providing database connectivity
   * @return `IO` description that, when executed, returns the number of rows inserted
   */
  def saveToDb(ordersWithDiscount: List[OrderWithDiscount], xa: Transactor[IO]): IO[Int] = {
    val sql =
      """INSERT INTO ORDERS
         (timestamp, product_name, expiry_date, quantity, unit_price, discount, final_price, channel, payment_method)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
    Update[OrderWithDiscount](sql).updateMany(ordersWithDiscount).transact(xa)
  }

  /**
   * The complete pipeline, expressed as a single `IO` program.
   *
   * The `for`‑comprehension sequences the side‑effecting steps:
   *   1. Log start
   *   2. Read CSV file
   *   3. Parse and apply discount rules (pure, no `IO`)
   *   4. Log results
   *   5. Write to database
   *
   * If any `IO` action fails, the remainder is skipped and the error is
   * handled centrally by `.handleErrorWith`.
   */
  override def run(args: List[String]): IO[ExitCode] = {
    val path = "src/main/resources/TRX1000.csv"
    val xa   = createTransactor("org.postgresql.Driver", "jdbc:postgresql:ordersdb", "docker", "docker")

    val program = for {
      _     <- Logger[IO].info("--- Processing Started ---")
      lines <- readLines(path)   // If this fails, control jumps to handleErrorWith
      _     <- Logger[IO].info(s"File read. Found ${lines.size} records.")

      // Pure processing – no `IO` required, no side effects
      ordersWithDiscount = lines.tail
        .flatMap(OrderHelpers.parseOrderSafe)      // Drop header, keep only valid orders
        .map(o => OrderHelpers.createOrderWithDiscount(o, OrderHelpers.discountRules))

      _     <- Logger[IO].info(s"Orders processed. Resulted in ${ordersWithDiscount.size} valid records.")
      rows  <- saveToDb(ordersWithDiscount, xa)
      _     <- Logger[IO].info(s"Success! $rows records inserted.")
    } yield ExitCode.Success

    // Centralised error handling for the entire pipeline.
    // Any exception thrown inside an `IO` block becomes a failed `IO`,
    // which is caught here and turned into a log message and an error exit code.
    program.handleErrorWith(err => Logger[IO].error(s"Application Error: ${err.getMessage}") as ExitCode.Error
    )
  }
}