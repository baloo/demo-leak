package controllers

import play.api._
import play.api.mvc._

import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.EventSource

import scala.concurrent.Future
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global


object Application extends Controller {

  def index = Action {
    Ok(views.html.index())
  }

  def streamDummy = Action {
    val futureEmptyString = Promise.timeout[Option[String]](Some(""), Duration(10,"minutes"))

    val dummyEnumerator = Enumerator.fromCallback1[String](
      x => futureEmptyString, 
      () => Logger("application").info("Socket closed"), 
      (_,_) => Logger("application").info("Socket error"))

    Logger("application").info("Dummy opened")
    Ok.stream(dummyEnumerator &> EventSource()).withHeaders(CONTENT_TYPE -> "text/event-stream")
  }


  def stream = Action {
    /**
     * Generates Input.Empty every 10 second in iteratee flow
     */
    def interleaver[E] = Enumerator.checkContinue0{
      val generator = () => Promise.timeout(Input.Empty, Duration(1, "seconds"))
      new Enumerator.TreatCont0[E] {
        def apply[A](loop: Iteratee[E, A] => Future[Iteratee[E,A]], k: Input[E] => Iteratee[E,A]) = generator().flatMap{
          e => {
            Logger("application").info("loop")
            loop(k(e))
          }
        }
      } 
    }

    val futureEmptyString = Promise.timeout[Option[String]](Some(""), Duration(10,"minutes"))
    val dummyEnumerator = Enumerator.fromCallback1[String](
      _ => futureEmptyString,
      () => Logger("application").info("Socket closed"),
      (_,_) => Logger("application").info("Socket error"))

    val smarterEnumerator = Enumerator.interleave(interleaver, dummyEnumerator)

    Logger("application").info("Smarter opened")
    Ok.stream(smarterEnumerator &> EventSource()).withHeaders(CONTENT_TYPE -> "text/event-stream")
  }
}
