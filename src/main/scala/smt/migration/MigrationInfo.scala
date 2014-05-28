package smt.migration

import java.util.Date
import smt.util.Util
import Util._

case class MigrationInfo(name: String, hash: Seq[Byte], dateTime: Date) {
  override def toString: String = {
    "MigrationInfo(" + name + ", " + bytesToHex(hash) + ", " + dateTime + ")"
  }
}