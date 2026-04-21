import cats.effect.{ExitCode, IO, IOApp}
import doobie.Transactor
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.update.Update
import fs2.{Stream, text}
import fs2.io.file.{Files, Path}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import models._

/**
 * Main entry point for the Orders Discount Engine – scaled for 100M+ records.
 *
 * This version uses streaming (fs2) to keep memory constant and parallelism
 * to saturate CPU cores. All side effects are wrapped in `IO` and `Stream`,
 * preserving the "functional core, imperative shell" pattern from the original.
 */
object OrdersMainScaled extends IOApp {

  // Logger instance – side‑effecting but safely wrapped in `IO`
  implicit def logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  /**
   * Creates a Doobie transactor using a plain `DriverManager` connection.
   *
   * No connection pooling – each batch insert opens and closes its own
   * database connection. This is safe for batch processing because we
   * control concurrency explicitly and avoid long‑lived connections.
   *
   * @return A transactor that can run `ConnectionIO` programs in `IO`
   */
  def createTransactor: Transactor[IO] = Transactor.fromDriverManager[IO]("org.postgresql.Driver", "jdbc:postgresql:ordersdb", "docker", "docker", None)

  /**
   * Reads a CSV file as a stream of lines, skipping the header and blank lines.
   *
   * Why streaming? Loading a 10GB file into memory would crash the JVM.
   * `fs2.io.file.readAll` reads the file in small chunks (typically 8KB),
   * so memory usage stays constant regardless of file size.
   *
   * @param path  Filesystem path to the CSV file
   * @return A `Stream[IO, String]` that emits one line at a time
   */
  def readLinesStream(path: String): Stream[IO, String] =
    Files[IO].readAll(Path(path))                // Stream of bytes
      .through(text.utf8.decode)                 // Bytes → String chunks
      .through(text.lines)                       // String chunks → individual lines
      .filter(_.trim.nonEmpty)                   // Drop completely empty lines
      .drop(1)                                   // Skip CSV header row

  /**
   * Transforms a stream of raw CSV lines into a stream of enriched order batches.
   *
   * Processing happens in two stages:
   *   1. Lines are grouped into fixed‑size chunks (e.g., 10,000 rows).
   *   2. Each chunk is parsed and enriched in parallel on all available CPU cores.
   *
   * The pure discount logic (`OrderHelpers.parseOrderSafe`, `createOrderWithDiscount`)
   * is executed inside an `IO` block to lift it into the effect context, but it
   * performs no I/O itself.
   *
   * @param lines      Stream of raw CSV lines (header already removed)
   * @param chunkSize  Number of lines to process per batch
   * @return A `Stream[IO, List[OrderWithDiscount]]` where each element is one batch
   */
  def processOrdersStream(lines: Stream[IO, String], chunkSize: Int): Stream[IO, List[OrderWithDiscount]] =
    lines.chunkN(chunkSize)
      .parEvalMapUnordered(Runtime.getRuntime.availableProcessors()) { chunk =>
        IO {
          chunk.toList
            .flatMap(OrderHelpers.parseOrderSafe)
            .map(o => OrderHelpers.createOrderWithDiscount(o, OrderHelpers.discountRules))
        }
      }

  /**
   * Inserts a single batch of enriched orders into the database.
   *
   * The insert is performed in its own transaction (Doobie's `transact` handles this).
   * Success and failure are both logged so we can monitor progress on long runs.
   *
   * @param batch  List of orders ready for insertion (may be empty)
   * @param xa     Transactor providing database connectivity
   * @return `IO[Unit]` that, when executed, performs the insert and logs the outcome
   */
  def saveBatchToDb(batch: List[OrderWithDiscount], xa: Transactor[IO]): IO[Unit] = {
    val sql =
      """INSERT INTO ORDERS
       (timestamp, product_name, expiry_date, quantity, unit_price, discount, final_price, channel, payment_method)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""

    val insert = Update[OrderWithDiscount](sql).updateMany(batch).transact(xa)

    (insert >> Logger[IO].info(s"Batch of ${batch.size} orders committed"))
      .handleErrorWith(err => Logger[IO].error(s"Batch insert failed: ${err.getMessage}"))
  }

  /**
   * The complete processing pipeline, expressed as a single `IO[Unit]` program.
   *
   * This composes the streaming stages (read → process → write) and adds
   * start/end logging. The `Stream` description is then "compiled" into an
   * `IO` via `.compile.drain` – this is the point where the lazy blueprint
   * becomes an executable effect.
   *
   * @param xa                Database transactor
   * @param path              Path to the input CSV file
   * @param chunkSize         Rows per batch (controls memory / I/O trade‑off)
   * @param writeParallelism  Maximum number of concurrent database inserts
   * @return `IO[Unit]` that, when run, processes the entire file
   */
  def pipeline(xa: Transactor[IO], path: String, chunkSize: Int, writeParallelism: Int): IO[Unit] = {
    Stream.eval(Logger[IO].info("--- Processing Started ---")) >>
      processOrdersStream(readLinesStream(path), chunkSize)
        .parEvalMapUnordered(writeParallelism)(batch => saveBatchToDb(batch, xa))
        .onFinalize(Logger[IO].info("--- Processing Completed Successfully ---"))
  }.compile.drain  // Convert the Stream description into an executable IO

  /**
   * Application entry point – called by `IOApp` when the program starts.
   *
   * This method is intentionally simple: it calls the pipeline with
   * configuration values, maps the result to an exit code, and adds a
   * top‑level error handler. All complex logic lives in the helpers above.
   */
  override def run(args: List[String]): IO[ExitCode] = {
    pipeline(createTransactor, "src/main/resources/TRX10M.csv", 10000, 8)
      .as(ExitCode.Success)
      .handleErrorWith(err => Logger[IO].error(s"Fatal error: ${err.getMessage}").as(ExitCode.Error))
  }
}
