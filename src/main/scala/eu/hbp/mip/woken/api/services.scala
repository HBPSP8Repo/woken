/*
 * Copyright 2017 Human Brain Project MIP by LREN CHUV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hbp.mip.woken.api

import akka.http.scaladsl.model.{ HttpEntity, StatusCode, StatusCodes }
import akka.http.scaladsl.server.{ ExceptionHandler, RejectionHandler, RequestContext }
import StatusCodes._

/**
  * Holds potential error response with the HTTP status and optional body
  *
  * @param responseStatus the status code
  * @param response       the optional body
  */
case class ErrorResponseException(responseStatus: StatusCode, response: Option[HttpEntity])
    extends Exception

/**
  * Provides a hook to catch exceptions and rejections from routes, allowing custom
  * responses to be provided, logs to be captured, and potentially remedial actions.
  *
  * Note that this is not marshalled, but it is possible to do so allowing for a fully
  * JSON API (e.g. see how Foursquare do it).
  */
trait FailureHandling {

  implicit def rejectionHandler: RejectionHandler = RejectionHandler.default

  implicit def exceptionHandler: ExceptionHandler = ExceptionHandler {

    case e: IllegalArgumentException =>
      ctx =>
        loggedFailureResponse(
          ctx,
          e,
          message = "The server was asked a question that didn't make sense: " + e.getMessage,
          error = StatusCodes.NotAcceptable
        )

    case e: NoSuchElementException =>
      ctx =>
        loggedFailureResponse(
          ctx,
          e,
          message = "The server is missing some information. Try again in a few moments.",
          error = NotFound
        )

    case t: Throwable =>
      ctx =>
        // note that toString here may expose information and cause a security leak, so don't do it.
        loggedFailureResponse(ctx, t)
  }

  private def loggedFailureResponse(
      ctx: RequestContext,
      thrown: Throwable,
      message: String = "The server is having problems.",
      error: StatusCode = StatusCodes.InternalServerError
  ) =
    //log.error(thrown, ctx.request.toString)
    ctx.complete((error, message))

}
