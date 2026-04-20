package models

import java.time.{LocalDate, Month, OffsetDateTime}
import java.time.temporal.ChronoUnit
import scala.util.Try

/**
 * Represents a raw, unprocessed order straight from the CSV file.
 * All fields are mandatory and directly mapped from the data source.
 */
case class Order(
                  timestamp: OffsetDateTime,   // When the order was placed
                  product_name: String,        // e.g., "cheese-cheddar", "wine-merlot"
                  expiry_date: LocalDate,      // When the product expires
                  quantity: Int,               // Number of items purchased
                  unit_price: Float,           // Price per single item
                  channel: String,             // e.g., "online", "in-store"
                  payment_method: String       // e.g., "card", "cash", "acorn"
                )

/**
 * Represents an enriched order after all discount rules have been applied.
 * Contains both the original fields and the computed discount / final price.
 */
case class OrderWithDiscount(
                              timestamp: OffsetDateTime,
                              product_name: String,
                              expiry_date: LocalDate,
                              quantity: Int,
                              unit_price: Float,
                              discount: Float,             // Discount percentage applied (0.0 to 100.0)
                              final_price: Float,          // Actual amount to be paid after discount
                              channel: String,
                              payment_method: String
                            )

/**
 * Pure functional helpers for parsing, qualifying, and calculating discounts.
 * No side effects. No external dependencies. Just data in, data out.
 */
object OrderHelpers {

  /**
   * Safely converts a single CSV line into an `Order` domain object.
   * Returns `Some(order)` on success, `None` on any parsing failure.
   *
   * This function is referentially transparent – calling it multiple times
   * with the same input always yields the same output.
   */
  def parseOrderSafe(line: String): Option[Order] = {
    Try {
      val cols = line.split(",").map(_.trim)
      Order(
        timestamp      = OffsetDateTime.parse(cols(0)),
        product_name   = cols(1),
        expiry_date    = LocalDate.parse(cols(2)),
        quantity       = cols(3).toInt,
        unit_price     = cols(4).toFloat,
        channel        = cols(5),
        payment_method = cols(6)
      )
    }.toOption
  }

  // --- Qualifying Rules (Pure Boolean Predicates) --------------------------
  // Each rule defines *when* a discount type should be considered.

  /**
   * Rule A: Product expires in less than 30 days from order date.
   * Already expired items (negative days) are excluded.
   */
  def isAQualified(order: Order): Boolean = {
    val daysToExpire = ChronoUnit.DAYS.between(order.timestamp.toLocalDate, order.expiry_date)
    daysToExpire > 0 && daysToExpire < 30
  }

  /**
   * Rule B: Product belongs to the "cheese" or "wine" category.
   * Category is determined by the prefix before the first hyphen.
   */
  def isBQualified(order: Order): Boolean = {
    val category = order.product_name.split("-")(0).toLowerCase.trim
    category == "cheese" || category == "wine"
  }

  /**
   * Rule C: Order was placed on March 23rd (special one‑day promotion).
   */
  def isCQualified(order: Order): Boolean = {
    val d = order.timestamp.toLocalDate
    d.getMonth == Month.MARCH && d.getDayOfMonth == 23
  }

  /**
   * Rule D: Customer bought more than 5 units of the product.
   */
  def isDQualified(order: Order): Boolean = order.quantity > 5

  // --- Discount Calculation Rules (Pure Float Functions) -------------------
  // Each rule computes the discount percentage *if* the corresponding
  // qualifier passes.

  /**
   * Rule A calculation: 30% minus the number of days until expiry.
   * Example: 5 days left → 30 - 5 = 25% discount.
   */
  def calcA(order: Order): Float =
    30f - ChronoUnit.DAYS.between(order.timestamp.toLocalDate, order.expiry_date).toFloat

  /**
   * Rule B calculation: 10% for cheese products, 5% for wine.
   */
  def calcB(order: Order): Float =
    if (order.product_name.toLowerCase.startsWith("cheese")) 10f else 5f

  /**
   * Rule C calculation: Flat 50% discount on March 23rd.
   */
  def calcC(order: Order): Float = 50f

  /**
   * Rule D calculation: Tiered discount based on quantity.
   *   6–9 units → 5%
   *   10–14 units → 7%
   *   15+ units  → 10%
   */
  def calcD(order: Order): Float =
    if (order.quantity >= 6 && order.quantity <= 9) 5f
    else if (order.quantity < 15) 7f
    else 10f

  /**
   * The complete rule set, expressed as a list of (qualifier, calculator) pairs.
   * This list can be treated as pure data – it can be filtered, mapped,
   * or replaced entirely for testing.
   */
  val discountRules: List[(Order => Boolean, Order => Float)] = List(
    (isAQualified, calcA),
    (isBQualified, calcB),
    (isCQualified, calcC),
    (isDQualified, calcD)
  )

  /**
   * Pure engine: Calculates the average of the *two highest* applicable
   * discount percentages for a given order.
   *
   * - Filters rules by qualifier
   * - Maps to calculated percentages
   * - Sorts descending
   * - Takes top two, computes average
   *
   * Returns 0.0 if no rules qualify.
   */
  def calculateDiscountPercentage(order: Order, rules: List[(Order => Boolean, Order => Float)]): Float = {
    val applicable = rules
      .filter { case (qr, _) => qr(order) }
      .map    { case (_, cr) => cr(order) }
      .sortBy(-_) // descending

    applicable.take(2) match {
      case Nil => 0.0f
      case xs  => xs.sum / xs.length
    }
  }

  /**
   * Pure transformation: Creates an `OrderWithDiscount` by applying the
   * given rule set to a valid `Order`.
   */
  def createOrderWithDiscount(order: Order, rules: List[(Order => Boolean, Order => Float)]): OrderWithDiscount = {
    val pct = calculateDiscountPercentage(order, rules)
    val finalPrice = order.unit_price * order.quantity * (1 - (pct / 100.0f))

    OrderWithDiscount(
      order.timestamp, order.product_name, order.expiry_date, order.quantity,
      order.unit_price, pct, finalPrice, order.channel, order.payment_method
    )
  }
}