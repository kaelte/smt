package smt

import smt.db._
import smt.report.{ReportersAction, Reporter}
import scalaz.Scalaz._
import smt.describe.DescribeAction
import sbt.Logger
import scalaz.-\/
import smt.migration.{MigrationInfo, Migration, Up}
import smt.db.ConnectionAction.HasConnection
import smt.db.AddAction.{HasUser, HasRemark}

case class HandlingDep(db: Database, rps: List[Reporter], logger: Logger, user: String, remark: Option[String])

trait StateHandling[T] extends DbAction[T] {
  
  lazy val connectionHandling = new ConnectionHandling[Connection] {
    lazy val hasConnection: HasConnection[Connection] = identity
  }

  def state(): EDKleisli[Seq[MigrationInfo]] = {
    import eSyntax._

    connection() >=> {
      for {
        _ <- connectionHandling.init()
        migs <- connectionHandling.state()
        _ <- connectionHandling.close()
      } yield migs
    }
  }

  def latestCommon(mhs: Seq[(Migration, Seq[Byte])]): EDKleisli[Option[connectionHandling.Common]] = {
    import eSyntax._

    connection() >=> {
      for {
        _ <- connectionHandling.init()
        co <-connectionHandling.latestCommon(mhs)
        _ <- connectionHandling.close()
      } yield co
    }
  }

  def applyScript(scr: migration.Script): EDKleisli[Unit] = {
    import eSyntax._

    connection() >=> {
      connectionHandling.applyScript(scr, Up) >> connectionHandling.close()
    }
  }
}

trait Handling[T] extends DbAction[T] with DescribeAction[T] with ReportersAction[T] {

  handling =>

  val hasUser: HasUser[T]
  val hasRemark: HasRemark[T]

  lazy val connectionHandling = new AddHandling[(T, Connection)] {
    lazy val hasConnection: HasConnection[(T, Connection)] = _._2
    lazy val hasUser: HasUser[(T, Connection)] = t => handling.hasUser(t._1)
    lazy val hasRemark: HasRemark[(T, Connection)] = t => handling.hasRemark(t._1)
  }

  def applyMigrationsAndReport(ms: Seq[Migration], arb: Boolean, runTests: Boolean): DKleisli[Unit] = {
    for {
      nmse <- {
        {
          import namedMoveTypes._
          import namedMoveTypes.ewSyntax._

          namedMoveTypes.liftE(connection()) >=! Tuple2[T, Connection] !=> {
            import connectionHandling.namedMoveTypes._
            liftE(connectionHandling.init()) >>  connectionHandling.applyMigrations(ms, arb, runTests) >> liftE(connectionHandling.close())
          }
        }.run.run
      }

      (nms, e) = nmse

      _ <- {
        (nms.actions.lastOption, e) match {
          case (Some(ms), -\/(f)) => describe(ms._1, ms._2, f)
          case (None, -\/(f)) => describe(f)
          case _ => point(())
        }
      }

      _ <- reportToAll(nms)
    } yield ()
  }
}
