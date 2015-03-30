package smt

import sbt._
import smt.db.Database
import sbt.Keys._
import report.Reporter
import java.io.File
import smt.migration.FileSplitters._
import smt.migration.Migration
import smt.db.DbAction.HasDb
import smt.report.ReportersAction.HasReporters
import smt.describe.DescribeAction.HasLogger
import smt.db.AddAction.{HasUser, HasRemark}
import scalaz.\/

object SMTImpl {

  private def failException[T](s: TaskStreams)(e: String): T = {
    s.log.error(e)
    throw new Exception(e)
  }

  private def throwLeft[T](s: TaskStreams)(te: String \/ T): T = te.fold[T]((e: String) => failException(s)(e), identity)

  case class StateHandlingDep(db: Database, logger: Logger)

  val stateHandling = new StateHandling[StateHandlingDep] {
    lazy val hasDb: HasDb[StateHandlingDep] = _.db
    lazy val hasLogger: HasLogger[StateHandlingDep] = _.logger
  }

  val handling = new Handling[HandlingDep] {
    lazy val hasDb: HasDb[HandlingDep] = _.db
    lazy val hasLogger: HasLogger[HandlingDep] = _.logger
    lazy val hasReporters: HasReporters[HandlingDep] = _.rps
    lazy val hasUser: HasUser[HandlingDep] =  _.user
    lazy val hasRemark: HasRemark[HandlingDep] =  _.remark
  }

  def showDbState(db: Database, ms: Seq[Migration], imo: Option[(Int, String)], s: TaskStreams): Unit = {

    val result = stateHandling.common(MigrationHandling.hashMigrations(ms, imo)).run(StateHandlingDep(db, s.log)).run

    result.foreach(co => {
      co.common.foreach(st => s.log.info(st.toString))
      co.diffOnDb.foreach(st => s.log.info("(!) " + st.toString))
    })
    throwLeft(s)(result)
  }

  def showLatestCommon(db: Database, ms: Seq[Migration], imo: Option[(Int, String)], s: TaskStreams): Unit = {
    val result = stateHandling.latestCommon(MigrationHandling.hashMigrations(ms, imo)).run(StateHandlingDep(db, s.log)).run

    result.foreach(lco => s.log.info(lco.map(_.toString).getOrElse("None")))
    throwLeft(s)(result)
  }

  def applyMigrations(args: Seq[String], db: Database, ms: Seq[Migration], imo: Option[(Int, String)], arb: Boolean, runTests: Boolean, rs: Seq[Reporter], user: String, s: TaskStreams): Unit = {
    args match {
      case Seq(remark) => doApplyMigrations(db, ms, imo, arb, runTests, rs, user, Some(remark), s)
      case Seq() => doApplyMigrations(db, ms, imo, arb, runTests, rs, user, None, s)
      case _ => throw new Exception("Too many arguments. Optional remark expected.")
    }
  }

  def doApplyMigrations(db: Database, ms: Seq[Migration], imo: Option[(Int, String)], arb: Boolean, runTests: Boolean, rs: Seq[Reporter], user: String, remark: Option[String], s: TaskStreams): Unit = {
    val action = handling.applyMigrationsAndReport(ms, imo, arb, runTests)
    val dep = HandlingDep(db, rs.toList, s.log, user, remark)
    throwLeft(s)(action.run(dep).run)
  }

  def migrateTo(args: Seq[String], db: Database, ms: Seq[Migration], imo: Option[(Int, String)], arb: Boolean, runTests: Boolean, rs: Seq[Reporter], user: String, s: TaskStreams) {
    def checkMig(target: String): Seq[Migration] = {
      val mst = ms.reverse.dropWhile(_.name != target).reverse
      if (mst.isEmpty) throw new Exception("No migration named '" + target + "' defined")
      else mst
    }

    args match {
      case Seq(target) => {
        val mst = checkMig(target)
        doApplyMigrations(db, mst, imo, arb, runTests, rs, user, None, s)
      }
      case Seq(target, remark) => {
        val mst = checkMig(target)
        doApplyMigrations(db, mst, imo, arb, runTests, rs, user, Some(remark), s)
      }
      case Seq() => throw new Exception("Name of a migration expected.")
      case _ => throw new Exception("Too many arguments. Name of a migration and optional remark expected.")
    }
  }

  def runScript(args: Seq[String], sourceDir: File, db: Database, s: TaskStreams) {
    args match {
      case Seq(dir) => {
        val relPath = IO.pathSplit(dir).toSeq
        val fullPath = relPath.foldLeft[File](sourceDir)((p, s) => p / s)
        val script = OneFileOneScriptSplitter(fullPath).head
        val action = stateHandling.applyScript(script)
        val result = action.run(StateHandlingDep(db, s.log)).run
        throwLeft(s)(result)
      }
      case Seq() => throw new Exception("Path expected.")
      case _ => throw new Exception("Too many arguments. Path expected.")
    }
  }
}
