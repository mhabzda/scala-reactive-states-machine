import Cart._
import Checkout.{SelectDelivery, SelectPayment, SendPaymentConfirmation}
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}

import scala.concurrent.duration._

object CartTimerKey

object CheckoutTimerKey

object PaymentTimerKey

object Cart {

  case object AddItem

  case object RemoveItem

  case class StartCheckout(cart: ActorRef)

  case object CancelCheckout

  case object CloseCheckout

  case object ExpireCart

}

class Cart extends Actor with Timers {
  override def receive: Receive = {
    case AddItem =>
      println("Item added, item count: 1")
      moveToNonEmptyState(1)
  }

  def awaitWhileNonEmpty(itemCount: Int): Receive = {
    case AddItem =>
      printf("Item added, item count: %d\n", itemCount + 1)
      context become awaitWhileNonEmpty(itemCount + 1)
    case RemoveItem =>
      printf("Item removed, item count: %d\n", itemCount - 1)
      if (itemCount > 0) context become awaitWhileNonEmpty(itemCount - 1)
      else exitNonEmptyState(receive)
    case StartCheckout =>
      println("Checkout started in Cart actor")
      exitNonEmptyState(awaitInCheckout(itemCount))
    case ExpireCart =>
      println("Cart Expired")
      context become receive
  }

  def awaitInCheckout(itemCount: Int): Receive = {
    case CancelCheckout =>
      println("Checkout cancelled")
      moveToNonEmptyState(itemCount)
    case CloseCheckout =>
      println("Checkout closed")
      context become receive
  }

  private def exitNonEmptyState(newState: Receive): Unit = {
    timers.cancel(CartTimerKey)
    context become newState
  }

  private def moveToNonEmptyState(itemCount: Int): Unit = {
    context become awaitWhileNonEmpty(itemCount)
    timers.startSingleTimer(CartTimerKey, ExpireCart, 5.second)
  }
}

object Checkout {

  case object Cancel

  case object SelectDelivery

  case object SelectPayment

  case object SendPaymentConfirmation

}

class Checkout extends Actor with Timers {
  override def receive: Receive = {
    case StartCheckout =>
      println("Checkout started in Checkout actor")
      timers.startSingleTimer(CheckoutTimerKey, Checkout.Cancel, 3.second)
      context become awaitWhileSelectingDelivery
  }

  def awaitWhileSelectingDelivery: Receive = {
    case SelectDelivery =>
      println("Delivery selected")
      context become awaitWhileSelectingPaymentMethod
    case Checkout.Cancel =>
      println("Checkout closed, closing actor")
      context stop self
  }

  def awaitWhileSelectingPaymentMethod: Receive = {
    case SelectPayment =>
      println("Payment selected")
      timers.cancel(CheckoutTimerKey)
      timers.startSingleTimer(PaymentTimerKey, Checkout.Cancel, 3.second)
      context become awaitInProcessingState
    case Checkout.Cancel =>
      println("Checkout closed, closing actor")
      context stop self
  }

  def awaitInProcessingState: Receive = {
    case SendPaymentConfirmation =>
      println("Payment received, checkout successfully closed")
      context stop self
    case Checkout.Cancel =>
      println("Payment closed, closing actor")
      context stop self
  }
}

object ShopApp extends App {
  val system = ActorSystem("Shop")
  val cartActor: ActorRef = system.actorOf(Props[Cart], "cart")
  val checkoutActor: ActorRef = system.actorOf(Props[Checkout], "checkout")

  cartActor ! AddItem
  cartActor ! AddItem
  cartActor ! RemoveItem
  cartActor ! AddItem

  Thread.sleep(200)

  checkoutActor ! StartCheckout
  checkoutActor ! SelectDelivery
  checkoutActor ! SelectPayment
  checkoutActor ! SendPaymentConfirmation
}