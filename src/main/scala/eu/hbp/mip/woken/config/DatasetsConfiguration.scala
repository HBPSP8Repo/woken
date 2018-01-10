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
import eu.hbp.mip.woken.config.AnonymisationLevel.AnonymisationLevel
import eu.hbp.mip.woken.fp.Traverse
import eu.hbp.mip.woken.messages.external.DatasetId

import cats.data.Validated._
import cats.implicits._

object AnonymisationLevel extends Enumeration {
  type AnonymisationLevel = Value
  val Identifying, Depersonalised, Anonymised = Value
}

case class Credentials(user: String, password: String)
case class Location(url: String, credentials: Option[Credentials])
case class Dataset(dataset: DatasetId,
                   description: String,
                   tables: List[String],
                   anonymisationLevel: AnonymisationLevel,
                   location: Option[Location])

object Dataset {

  // Seems useful as Scala enumeration and Cats mapN don't appear to work together well
  def apply2(dataset: String,
             description: String,
             tables: List[String],
             anonymisationLevel: String,
             location: Option[Location]): Dataset = Dataset(
    DatasetId(dataset),
    description,
    tables,
    AnonymisationLevel.withName(anonymisationLevel),
    location
  )
}

object DatasetsConfiguration {

  def read(config: Config, path: List[String]): Validation[Dataset] = {

    val federationConfig = config.validateConfig(path.mkString("."))

    federationConfig.andThen { f =>
      val dataset     = path.lastOption.map(lift).getOrElse("Empty path".invalidNel[String])
      val description = f.validateString("description")
      val tables: Validation[List[String]] = f.validateString("tables").map { s =>
        s.split(',').toIndexedSeq.toList
      }
      val location: Validation[Option[Location]] = f
        .validateConfig("location")
        .andThen { cl =>
          val url = cl.validateString("url")
          val credentials: Validation[Option[Credentials]] = cl
            .validateConfig("credentials")
            .andThen { cc =>
              val user     = cc.validateString("user")
              val password = cc.validateString("password")

              (user, password) mapN Credentials
            }
            .map(_.some)
            .orElse(lift(None.asInstanceOf[Option[Credentials]]))

          (url, credentials) mapN Location
        }
        .map(_.some)
        .orElse(lift(None.asInstanceOf[Option[Location]]))
      val anonymisationLevel: Validation[String] = f
        .validateString("anonymisationLevel")
        .ensure(
          throw new IllegalArgumentException(
            "anonymisationLevel: valid values are " + AnonymisationLevel.values.mkString(",")
          )
        ) { s =>
          AnonymisationLevel.withName(s); true
        }

      (dataset, description, tables, anonymisationLevel, location) mapN Dataset.apply2
    }

  }

  def factory(config: Config): String => Validation[Dataset] =
    dataset => read(config, List("datasets", dataset))

  def datasetNames(config: Config): Validation[Set[String]] =
    config.validateConfig("datasets").map(_.keys)

  def datasets(config: Config): Validation[Map[DatasetId, Dataset]] = {
    val datasetFactory = factory(config)
    datasetNames(config).andThen { names: Set[String] =>
      val m: List[Validation[(DatasetId, Dataset)]] =
        names.toList.map(n => lift(DatasetId(n)) -> datasetFactory(n)).map(_.tupled)
      val t: Validation[List[(DatasetId, Dataset)]] = Traverse.sequence(m)
      t.map(_.toMap)
    }
  }

}
