package smt

import Util._

class TestDatabase extends Database {

    def add(migrationInfo: MigrationInfo): (Option[Failure], Database)  = {
      println("adding " + migrationInfo)
      s =  s :+ migrationInfo
      (None, this)
    }

    def addDowns(migHash: Seq[Byte], downs: Seq[Script]): (Option[Failure], Database) = {
      println("adding " + downs)
      ds = ds + (migHash -> downs)
      (None, this)
    }

    def remove(hash: Seq[Byte]): (Option[Failure], Database) = {
      println("removing " + bytesToHex(hash))
      s = s.filterNot(_.hash == hash)
      (None, this)
    }

    def removeDowns(migHash: Seq[Byte]): (Option[Failure], Database) = {
      println("removing downs" + bytesToHex(migHash))
      ds = ds - migHash
      (None, this)
    }

    def applyScript(script: Script): (Option[Failure], Database) = {
      println("applying " + script)
      (None, this)
    }


  private var s: Seq[MigrationInfo] = Nil

  private var ds: Map[Seq[Byte], Seq[Script]] = Map()

  def state: Either[Failure, Seq[MigrationInfo]] = Right(s)

  def downs(hash: Seq[Byte]): Either[Failure, Seq[Script]] = Right(ds(hash))
}