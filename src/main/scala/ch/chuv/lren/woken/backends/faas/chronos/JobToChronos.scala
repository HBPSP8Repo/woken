/*
 * Copyright (C) 2017  LREN CHUV for Human Brain Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ch.chuv.lren.woken.backends.faas.chronos

import ch.chuv.lren.woken.config.{ DatabaseConfiguration, DatabaseId, JobsConfiguration }
import ch.chuv.lren.woken.core.model.jobs.DockerJob
import ch.chuv.lren.woken.cromwell.core.ConfigUtil.Validation
import cats.implicits._

object JobToChronos {
  type EV = EnvironmentVariable
  val EV: EnvironmentVariable.type = EnvironmentVariable

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  private[this] def dbEnvironment(conf: DatabaseConfiguration,
                                  prefix: String = ""): List[EnvironmentVariable] =
    List(
      EV(prefix + "DBI_DRIVER", conf.dbiDriver),
      EV(prefix + "DBAPI_DRIVER", conf.dbApiDriver),
      EV(prefix + "JDBC_DRIVER", conf.jdbcDriver),
      EV(prefix + "JDBC_URL", conf.jdbcUrl),
      EV(prefix + "HOST", conf.host),
      EV(prefix + "PORT", conf.port.toString),
      EV(prefix + "DATABASE", conf.database),
      EV(prefix + "USER", conf.user),
      EV(prefix + "PASSWORD", conf.password)
    )

  def apply(job: DockerJob,
            dockerBridgeNetwork: Option[String],
            jobsConf: JobsConfiguration,
            jdbcConfF: DatabaseId => Validation[DatabaseConfiguration]): Validation[ChronosJob] = {

    val container = dockerBridgeNetwork.fold(
      Container(`type` = ContainerType.DOCKER, image = job.algorithmDefinition.dockerImage)
    )(
      bridge =>
        // LATER: adding --network=<bridge> is still required, despite having the information in networkInfos
        // networkInfos = List(Network(name = bridge)),
        Container(`type` = ContainerType.DOCKER,
                  image = job.algorithmDefinition.dockerImage,
                  network = NetworkMode.BRIDGE,
                  parameters = List(Parameter("network", bridge)))
    )

    def buildChronosJob(inputDb: DatabaseConfiguration,
                        outputDb: DatabaseConfiguration): ChronosJob = {
      val environmentVariables: List[EV] = List(
        EV("JOB_ID", job.jobId),
        EV("NODE", jobsConf.node),
        EV("DOCKER_IMAGE", job.algorithmDefinition.dockerImage)
      ) ++
        job.environmentVariables.map(kv => EV(kv._1, kv._2)) ++
        dbEnvironment(inputDb, "IN_") ++
        dbEnvironment(outputDb, "OUT_")

      ChronosJob(
        name = job.jobName,
        command = job.dockerCommand,
        arguments = job.dockerArguments,
        shell = false,
        schedule = "R1//PT1M",
        epsilon = Some("PT5M"),
        container = Some(container),
        cpus = Some(jobsConf.defaultJobCpus),
        mem = Some(jobsConf.defaultJobMemory),
        owner = Some(jobsConf.owner),
        environmentVariables = environmentVariables.sortBy(_.name),
        retries = 0
      )
    }

    val inputDb  = jdbcConfF(DatabaseId(job.query.dbTable.database))
    val outputDb = jdbcConfF(jobsConf.resultDb)

    (inputDb, outputDb) mapN buildChronosJob

  }
}
