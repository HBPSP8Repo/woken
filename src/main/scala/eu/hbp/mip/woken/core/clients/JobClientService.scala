package eu.hbp.mip.woken.core.clients

import akka.actor.{ActorLogging, Actor}
import akka.io.IO
import akka.util.Timeout
import spray.can.Http
import spray.http.{StatusCodes, StatusCode, HttpResponse}
import spray.httpx.RequestBuilding._

import scala.concurrent.Future
import scala.concurrent.duration._

object JobClientService {

  // Requests
  type Start = eu.hbp.mip.woken.core.CoordinatorActor.Start
  val Start = eu.hbp.mip.woken.core.CoordinatorActor.Start

  // Responses

  case class JobComplete(node: String)
  case class JobError(node: String, message: String)
}

class JobClientService(node: String) extends Actor with ActorLogging {
  import JobClientService._
  import eu.hbp.mip.woken.config.WokenConfig.jobs._

  def receive = {
    case Start(job) => {
      import akka.pattern.{ask, pipe}
      import spray.httpx.SprayJsonSupport._
      import eu.hbp.mip.woken.api.JobDto._
      implicit val system = context.system
      implicit val executionContext = context.dispatcher
      implicit val timeout: Timeout = Timeout(180.seconds)

      log.warning(s"Send PUT request to ${nodeConfig(node).jobsUrl}/job")
      log.warning(jobDtoFormat.write(job).prettyPrint)

      val originalSender = sender()
      val jobResponse: Future[HttpResponse] =
        (IO(Http) ? Put(nodeConfig(node).jobsUrl + "/job", job)).mapTo[HttpResponse]

      jobResponse.map {
        case HttpResponse(statusCode: StatusCode, entity, _, _) => statusCode match {
          case ok: StatusCodes.Success => JobComplete(node)
          case _ => JobError(node, s"Error $statusCode: ${entity.asString}")
        }
      } recoverWith { case e: Throwable => Future(JobError(node, e.toString)) } pipeTo originalSender
    }

  }
}