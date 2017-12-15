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

package eu.hbp.mip.woken.authentication

import eu.hbp.mip.woken.config.AppConfiguration
import spray.routing.authentication.{ BasicAuth, UserPass }
import spray.routing.directives.AuthMagnet

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Simple support for basic authentication.
  */
trait BasicAuthentication {

  def appConfiguration: AppConfiguration

  def basicAuthenticator(implicit executionContext: ExecutionContext): AuthMagnet[String] = {
    val expectedUsername = appConfiguration.basicAuth.username
    val expectedPassword = appConfiguration.basicAuth.password

    def validateUser(userPass: Option[UserPass]): Option[String] =
      userPass
        .filter(up => up.user == expectedUsername && up.pass == expectedPassword)
        .map(_.user)

    def wokenUserPassAuthenticator(userPass: Option[UserPass]): Future[Option[String]] = Future {
      validateUser(userPass)
    }

    BasicAuth(wokenUserPassAuthenticator _, realm = "Woken Private API")
  }

}