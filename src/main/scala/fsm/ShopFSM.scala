package fsm

import akka.actor.{ActorRef, ActorSystem, FSM, Props}

import scala.concurrent.duration._

sealed trait CartEvent

case object AddItem extends CartEvent

case object RemoveItem extends CartEvent

case class StartCheckout(cart: ActorRef) extends CartEvent

case object CancelCheckout extends CartEvent

case object CloseCheckout extends CartEvent

case object ExpireCart extends CartEvent


sealed trait CartState

case object Empty extends CartState

case object NotEmpty extends CartState

case object InCheckout extends CartState


class CartFSM extends FSM[CartState, Int] {
  startWith(Empty, 0)

  when(Empty) {
    case Event(AddItem, 0) =>
      goto(NotEmpty) using 1
  }

  when(NotEmpty, stateTimeout = 5 second) {
    case Event(AddItem, quantity: Int) =>
      log.info("{} {}", AddItem, quantity + 1)
      stay using quantity + 1
    case Event(RemoveItem, 1) =>
      goto(Empty) using 0
    case Event(RemoveItem, quantity: Int) =>
      log.info("{} {}", RemoveItem, quantity - 1)
      stay using quantity - 1
    case Event(StartCheckout, quantity: Int) =>
      goto(InCheckout) using quantity
    case Event(StateTimeout, _: Int) =>
      goto(Empty) using 0
  }

  when(InCheckout) {
    case Event(CancelCheckout, quantity: Int) =>
      goto(NotEmpty) using quantity
    case Event(CloseCheckout, _: Int) =>
      goto(Empty) using 0
  }

  onTransition {
    case _ -> newState =>
      log.info("{} {}", newState, nextStateData)
  }
}

sealed trait CheckoutEvent

case object Cancel extends CheckoutEvent

case object SelectDelivery extends CheckoutEvent

case object SelectPayment extends CheckoutEvent

case object SendPaymentConfirmation extends CheckoutEvent


sealed trait CheckoutState

case object NotStarted extends CheckoutState

case object SelectingDelivery extends CheckoutState

case object SelectingPaymentMethod extends CheckoutState

case object ProcessingPayment extends CheckoutState


class CheckoutFSM extends FSM[CheckoutState, Any] {
  startWith(NotStarted, 0)

  when(NotStarted) {
    case Event(StartCheckout(cart: ActorRef), _) =>
      cart ! StartCheckout
      goto(SelectingDelivery)
  }

  when(SelectingDelivery, stateTimeout = 5 second) {
    case Event(SelectDelivery, _) =>
      goto(SelectingPaymentMethod)
    case Event(StateTimeout, _) =>
      log.info("Checkout done")
      context stop self
      stay
  }

  when(SelectingPaymentMethod, stateTimeout = 5 second) {
    case Event(SelectPayment, _) =>
      goto(ProcessingPayment)
    case Event(StateTimeout, _) =>
      log.info("Checkout done")
      context stop self
      stay
  }

  when(ProcessingPayment, stateTimeout = 5 second) {
    case Event(SendPaymentConfirmation, _) =>
      log.info("Checkout done")
      context stop self
      stay
    case Event(StateTimeout, _) =>
      log.info("Checkout done")
      context stop self
      stay
  }

  onTransition {
    case _ -> newState =>
      log.info(newState.toString)
  }
}

object ShopFSM extends App {
  val system = ActorSystem("ShopFSM")
  val cartActor: ActorRef = system.actorOf(Props[CartFSM], "cart_fsm")
  val checkoutActor: ActorRef = system.actorOf(Props[CheckoutFSM], "checkout_fsm")

  runSuccessfullyPath()
  // runCartExpired()
  //  runCheckoutExpired()
  //  runPaymentExpired()

  private def runSuccessfullyPath(): Unit = {
    cartActor ! AddItem
    cartActor ! AddItem
    cartActor ! RemoveItem
    cartActor ! AddItem

    Thread.sleep(200)

    checkoutActor ! StartCheckout(cartActor)
    checkoutActor ! SelectDelivery
    checkoutActor ! SelectPayment
    checkoutActor ! SendPaymentConfirmation
  }

  private def runCartExpired(): Unit = {
    cartActor ! AddItem
    cartActor ! AddItem
    cartActor ! RemoveItem
    cartActor ! AddItem
  }

  private def runCheckoutExpired(): Unit = {
    cartActor ! AddItem

    Thread.sleep(200)

    checkoutActor ! StartCheckout(cartActor)
    checkoutActor ! SelectDelivery
  }

  private def runPaymentExpired(): Unit = {
    cartActor ! AddItem

    Thread.sleep(200)

    checkoutActor ! StartCheckout(cartActor)
    checkoutActor ! SelectDelivery
    checkoutActor ! SelectPayment
  }
}
