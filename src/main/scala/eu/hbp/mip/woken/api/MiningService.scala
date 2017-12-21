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

import akka.actor.{ ActorRef, ActorRefFactory, ActorSystem }
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import eu.hbp.mip.woken.api.swagger.MiningServiceApi
import eu.hbp.mip.woken.authentication.BasicAuthentication
import eu.hbp.mip.woken.config.{
  AlgorithmDefinition,
  AppConfiguration,
  DatabaseConfiguration,
  JobsConfiguration
}
import eu.hbp.mip.woken.messages.external._
import eu.hbp.mip.woken.core._
import eu.hbp.mip.woken.dao.FeaturesDAL
import eu.hbp.mip.woken.service.{ AlgorithmLibraryService, JobResultService, VariablesMetaService }
import eu.hbp.mip.woken.cromwell.core.ConfigUtil.Validation
import MiningQueries._
import eu.hbp.mip.woken.core.commands.JobCommands.{
  Command,
  StartCoordinatorJob,
  StartExperimentJob
}

object MiningService {}

// this trait defines our service behavior independently from the service actor
class MiningService(val chronosService: ActorRef,
                    val featuresDatabase: FeaturesDAL,
                    val jobResultService: JobResultService,
                    val variablesMetaService: VariablesMetaService,
                    override val appConfiguration: AppConfiguration,
                    val jobsConf: JobsConfiguration,
                    val defaultFeaturesTable: String)(implicit system: ActorSystem)
    extends MiningServiceApi
    with FailureHandling
    with PerRequestCreator
    with DefaultJsonFormats
    with BasicAuthentication {

  override def context: ActorRefFactory = system

  implicit val executionContext = context.dispatcher

  val routes: Route = mining ~ experiment ~ listMethods

  import ApiJsonSupport._

  // TODO: improve passing configuration around
  private lazy val config = ConfigFactory.load()
  private lazy val coordinatorConfig = CoordinatorConfig(chronosService,
                                                         appConfiguration.dockerBridgeNetwork,
                                                         featuresDatabase,
                                                         jobResultService,
                                                         jobsConf,
                                                         DatabaseConfiguration.factory(config))

  override def listMethods: Route = path("mining" / "list-methods") {
    authenticateBasicAsync(realm = "Woken Secure API", basicAuthenticator) { user =>
      import spray.json._
      get {
        complete(AlgorithmLibraryService().algorithms())
      }
    }

  }

  override def mining: Route = path("mining" / "job") {
    authenticateBasicAsync(realm = "Woken Secure API", basicAuthenticator) { user =>
      post {
        entity(as[MiningQuery]) {
          case MiningQuery(variables, covariables, groups, filters, Algorithm(c, n, p))
              if c == "" || c == "data" =>
            ctx =>
              {
                ctx.complete(
                  featuresDatabase.queryData(defaultFeaturesTable, {
                    variables ++ covariables ++ groups
                  }.distinct.map(_.code))
                )
              }

          case query: MiningQuery =>
            val job = miningQuery2job(variablesMetaService, jobsConf, ???)(query)
            job.fold(
              errors => ???,
              dockerJob =>
                miningJob(coordinatorConfig) {
                  StartCoordinatorJob(dockerJob)
              }
            )

        }
      }
    }

  }

  override def experiment: Route = path("mining" / "experiment") {
    authenticateBasicAsync(realm = "Woken Secure API", basicAuthenticator) { user =>
      post {
        entity(as[ExperimentQuery]) { query: ExperimentQuery =>
          {
            val job = experimentQuery2job(variablesMetaService, jobsConf)(query)
            job.fold(
              errors => ???,
              experimentActorJob =>
                experimentJob(coordinatorConfig, ???) {
                  StartExperimentJob(experimentActorJob)
              }
            )
          }
        }
      }
    }

  }

  private def newCoordinatorActor(coordinatorConfig: CoordinatorConfig): ActorRef =
    context.actorOf(CoordinatorActor.props(coordinatorConfig))

  private def newExperimentActor(
      coordinatorConfig: CoordinatorConfig,
      algorithmLookup: String => Validation[AlgorithmDefinition]
  ): ActorRef =
    context.actorOf(ExperimentActor.props(coordinatorConfig, algorithmLookup))

  import PerRequest._

  def miningJob(coordinatorConfig: CoordinatorConfig)(command: Command): Route =
    asyncComplete { ctx =>
      perRequest(ctx, newCoordinatorActor(coordinatorConfig), command)
    }

  def experimentJob(
      coordinatorConfig: CoordinatorConfig,
      algorithmLookup: String => Validation[AlgorithmDefinition]
  )(command: Command): Route =
    asyncComplete { ctx =>
      perRequest(ctx, newExperimentActor(coordinatorConfig, algorithmLookup), command)
    }

}
