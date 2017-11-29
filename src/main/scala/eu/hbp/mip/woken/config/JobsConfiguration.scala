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

package eu.hbp.mip.woken.config

import com.typesafe.config.Config
import eu.hbp.mip.woken.cromwell.core.ConfigUtil._
import cats.data.Validated._
import cats.implicits._

final case class JobsConfiguration(
    node: String,
    owner: String,
    chronosServerUrl: String,
    featuresDb: String,
    resultDb: String
)

object JobsConfiguration {

  def read(config: Config, path: Seq[String] = List("jobs")): Validation[JobsConfiguration] = {
    val jobsConfig = path.foldRight(config) { (s, c) =>
      c.getConfig(s)
    }

    val node             = jobsConfig.validateString("node")
    val owner            = jobsConfig.validateString("owner")
    val chronosServerUrl = jobsConfig.validateString("chronosServerUrl")
    val featuresDb       = jobsConfig.validateString("featuresDb")
    val resultDb         = jobsConfig.validateString("resultDb")

    (node, owner, chronosServerUrl, featuresDb, resultDb) mapN JobsConfiguration.apply
  }

}