package doobie.free

#+scalaz
import scalaz.{ Catchable, Free => F, Kleisli, Monad, ~>, \/ }
#-scalaz
#+cats
import cats.~>
import cats.data.Kleisli
import cats.free.{ Free => F }
import scala.util.{ Either => \/ }
#-cats
#+fs2
import fs2.util.{ Catchable, Suspendable }
import fs2.interop.cats._
#-fs2

import doobie.util.capture._
import doobie.free.kleislitrans._

import java.lang.Class
import java.lang.Object
import java.lang.String
import java.sql.Blob
import java.sql.CallableStatement
import java.sql.Clob
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Driver
import java.sql.NClob
import java.sql.PreparedStatement
import java.sql.Ref
import java.sql.ResultSet
import java.sql.RowIdLifetime
import java.sql.SQLData
import java.sql.SQLInput
import java.sql.SQLOutput
import java.sql.Statement

import nclob.NClobIO
import blob.BlobIO
import clob.ClobIO
import databasemetadata.DatabaseMetaDataIO
import driver.DriverIO
import ref.RefIO
import sqldata.SQLDataIO
import sqlinput.SQLInputIO
import sqloutput.SQLOutputIO
import connection.ConnectionIO
import statement.StatementIO
import preparedstatement.PreparedStatementIO
import callablestatement.CallableStatementIO
import resultset.ResultSetIO

/**
 * Algebra and free monad for primitive operations over a `java.sql.DatabaseMetaData`. This is
 * a low-level API that exposes lifecycle-managed JDBC objects directly and is intended mainly
 * for library developers. End users will prefer a safer, higher-level API such as that provided
 * in the `doobie.hi` package.
 *
 * `DatabaseMetaDataIO` is a free monad that must be run via an interpreter, most commonly via
 * natural transformation of its underlying algebra `DatabaseMetaDataOp` to another monad via
 * `Free#foldMap`.
 *
 * The library provides a natural transformation to `Kleisli[M, DatabaseMetaData, A]` for any
 * exception-trapping (`Catchable`) and effect-capturing (`Capture`) monad `M`. Such evidence is
 * provided for `Task`, `IO`, and stdlib `Future`; and `transK[M]` is provided as syntax.
 *
 * {{{
 * // An action to run
 * val a: DatabaseMetaDataIO[Foo] = ...
 *
 * // A JDBC object
 * val s: DatabaseMetaData = ...
 *
 * // Unfolding into a Task
 * val ta: Task[A] = a.transK[Task].run(s)
 * }}}
 *
 * @group Modules
 */
object databasemetadata extends DatabaseMetaDataIOInstances {

  /**
   * Sum type of primitive operations over a `java.sql.DatabaseMetaData`.
   * @group Algebra
   */
  sealed trait DatabaseMetaDataOp[A] {
#+scalaz
    protected def primitive[M[_]: Monad: Capture](f: DatabaseMetaData => A): Kleisli[M, DatabaseMetaData, A] =
      Kleisli((s: DatabaseMetaData) => Capture[M].apply(f(s)))
    def defaultTransK[M[_]: Monad: Catchable: Capture]: Kleisli[M, DatabaseMetaData, A]
#-scalaz
#+fs2
    protected def primitive[M[_]: Suspendable](f: DatabaseMetaData => A): Kleisli[M, DatabaseMetaData, A] =
      Kleisli((s: DatabaseMetaData) => Predef.implicitly[Suspendable[M]].delay(f(s)))
    def defaultTransK[M[_]: Catchable: Suspendable]: Kleisli[M, DatabaseMetaData, A]
#-fs2
  }

  /**
   * Module of constructors for `DatabaseMetaDataOp`. These are rarely useful outside of the implementation;
   * prefer the smart constructors provided by the `databasemetadata` module.
   * @group Algebra
   */
  object DatabaseMetaDataOp {

    // This algebra has a default interpreter
    implicit val DatabaseMetaDataKleisliTrans: KleisliTrans.Aux[DatabaseMetaDataOp, DatabaseMetaData] =
      new KleisliTrans[DatabaseMetaDataOp] {
        type J = DatabaseMetaData
#+scalaz
        def interpK[M[_]: Monad: Catchable: Capture]: DatabaseMetaDataOp ~> Kleisli[M, DatabaseMetaData, ?] =
#-scalaz
#+fs2
        def interpK[M[_]: Catchable: Suspendable]: DatabaseMetaDataOp ~> Kleisli[M, DatabaseMetaData, ?] =
#-fs2
          new (DatabaseMetaDataOp ~> Kleisli[M, DatabaseMetaData, ?]) {
            def apply[A](op: DatabaseMetaDataOp[A]): Kleisli[M, DatabaseMetaData, A] =
              op.defaultTransK[M]
          }
      }

    // Lifting
    case class Lift[Op[_], A, J](j: J, action: F[Op, A], mod: KleisliTrans.Aux[Op, J]) extends DatabaseMetaDataOp[A] {
#+scalaz
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = Kleisli(_ => mod.transK[M].apply(action).run(j))
#-scalaz
#+fs2
      override def defaultTransK[M[_]: Catchable: Suspendable] = Kleisli(_ => mod.transK[M].apply(action).run(j))
#-fs2
    }

    // Combinators
    case class Attempt[A](action: DatabaseMetaDataIO[A]) extends DatabaseMetaDataOp[Throwable \/ A] {
#+scalaz
      override def defaultTransK[M[_]: Monad: Catchable: Capture] =
#-scalaz
#+fs2
      override def defaultTransK[M[_]: Catchable: Suspendable] =
#-fs2
        Predef.implicitly[Catchable[Kleisli[M, DatabaseMetaData, ?]]].attempt(action.transK[M])
    }
    case class Pure[A](a: () => A) extends DatabaseMetaDataOp[A] {
#+scalaz
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_ => a())
#-scalaz
#+fs2
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_ => a())
#-fs2
    }
    case class Raw[A](f: DatabaseMetaData => A) extends DatabaseMetaDataOp[A] {
#+scalaz
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(f)
#-scalaz
#+fs2
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(f)
#-fs2
    }

    // Primitive Operations
#+scalaz
    case object AllProceduresAreCallable extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.allProceduresAreCallable())
    }
    case object AllTablesAreSelectable extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.allTablesAreSelectable())
    }
    case object AutoCommitFailureClosesAllResultSets extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.autoCommitFailureClosesAllResultSets())
    }
    case object DataDefinitionCausesTransactionCommit extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.dataDefinitionCausesTransactionCommit())
    }
    case object DataDefinitionIgnoredInTransactions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.dataDefinitionIgnoredInTransactions())
    }
    case class  DeletesAreDetected(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.deletesAreDetected(a))
    }
    case object DoesMaxRowSizeIncludeBlobs extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.doesMaxRowSizeIncludeBlobs())
    }
    case object GeneratedKeyAlwaysReturned extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.generatedKeyAlwaysReturned())
    }
    case class  GetAttributes(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getAttributes(a, b, c, d))
    }
    case class  GetBestRowIdentifier(a: String, b: String, c: String, d: Int, e: Boolean) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getBestRowIdentifier(a, b, c, d, e))
    }
    case object GetCatalogSeparator extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getCatalogSeparator())
    }
    case object GetCatalogTerm extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getCatalogTerm())
    }
    case object GetCatalogs extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getCatalogs())
    }
    case object GetClientInfoProperties extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getClientInfoProperties())
    }
    case class  GetColumnPrivileges(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getColumnPrivileges(a, b, c, d))
    }
    case class  GetColumns(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getColumns(a, b, c, d))
    }
    case object GetConnection extends DatabaseMetaDataOp[Connection] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getConnection())
    }
    case class  GetCrossReference(a: String, b: String, c: String, d: String, e: String, f: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getCrossReference(a, b, c, d, e, f))
    }
    case object GetDatabaseMajorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDatabaseMajorVersion())
    }
    case object GetDatabaseMinorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDatabaseMinorVersion())
    }
    case object GetDatabaseProductName extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDatabaseProductName())
    }
    case object GetDatabaseProductVersion extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDatabaseProductVersion())
    }
    case object GetDefaultTransactionIsolation extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDefaultTransactionIsolation())
    }
    case object GetDriverMajorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDriverMajorVersion())
    }
    case object GetDriverMinorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDriverMinorVersion())
    }
    case object GetDriverName extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDriverName())
    }
    case object GetDriverVersion extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getDriverVersion())
    }
    case class  GetExportedKeys(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getExportedKeys(a, b, c))
    }
    case object GetExtraNameCharacters extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getExtraNameCharacters())
    }
    case class  GetFunctionColumns(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getFunctionColumns(a, b, c, d))
    }
    case class  GetFunctions(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getFunctions(a, b, c))
    }
    case object GetIdentifierQuoteString extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getIdentifierQuoteString())
    }
    case class  GetImportedKeys(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getImportedKeys(a, b, c))
    }
    case class  GetIndexInfo(a: String, b: String, c: String, d: Boolean, e: Boolean) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getIndexInfo(a, b, c, d, e))
    }
    case object GetJDBCMajorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getJDBCMajorVersion())
    }
    case object GetJDBCMinorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getJDBCMinorVersion())
    }
    case object GetMaxBinaryLiteralLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxBinaryLiteralLength())
    }
    case object GetMaxCatalogNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxCatalogNameLength())
    }
    case object GetMaxCharLiteralLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxCharLiteralLength())
    }
    case object GetMaxColumnNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxColumnNameLength())
    }
    case object GetMaxColumnsInGroupBy extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxColumnsInGroupBy())
    }
    case object GetMaxColumnsInIndex extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxColumnsInIndex())
    }
    case object GetMaxColumnsInOrderBy extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxColumnsInOrderBy())
    }
    case object GetMaxColumnsInSelect extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxColumnsInSelect())
    }
    case object GetMaxColumnsInTable extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxColumnsInTable())
    }
    case object GetMaxConnections extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxConnections())
    }
    case object GetMaxCursorNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxCursorNameLength())
    }
    case object GetMaxIndexLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxIndexLength())
    }
    case object GetMaxLogicalLobSize extends DatabaseMetaDataOp[Long] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxLogicalLobSize())
    }
    case object GetMaxProcedureNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxProcedureNameLength())
    }
    case object GetMaxRowSize extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxRowSize())
    }
    case object GetMaxSchemaNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxSchemaNameLength())
    }
    case object GetMaxStatementLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxStatementLength())
    }
    case object GetMaxStatements extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxStatements())
    }
    case object GetMaxTableNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxTableNameLength())
    }
    case object GetMaxTablesInSelect extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxTablesInSelect())
    }
    case object GetMaxUserNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getMaxUserNameLength())
    }
    case object GetNumericFunctions extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getNumericFunctions())
    }
    case class  GetPrimaryKeys(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getPrimaryKeys(a, b, c))
    }
    case class  GetProcedureColumns(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getProcedureColumns(a, b, c, d))
    }
    case object GetProcedureTerm extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getProcedureTerm())
    }
    case class  GetProcedures(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getProcedures(a, b, c))
    }
    case class  GetPseudoColumns(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getPseudoColumns(a, b, c, d))
    }
    case object GetResultSetHoldability extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getResultSetHoldability())
    }
    case object GetRowIdLifetime extends DatabaseMetaDataOp[RowIdLifetime] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getRowIdLifetime())
    }
    case object GetSQLKeywords extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSQLKeywords())
    }
    case object GetSQLStateType extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSQLStateType())
    }
    case object GetSchemaTerm extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSchemaTerm())
    }
    case object GetSchemas extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSchemas())
    }
    case class  GetSchemas1(a: String, b: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSchemas(a, b))
    }
    case object GetSearchStringEscape extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSearchStringEscape())
    }
    case object GetStringFunctions extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getStringFunctions())
    }
    case class  GetSuperTables(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSuperTables(a, b, c))
    }
    case class  GetSuperTypes(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSuperTypes(a, b, c))
    }
    case object GetSystemFunctions extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getSystemFunctions())
    }
    case class  GetTablePrivileges(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getTablePrivileges(a, b, c))
    }
    case object GetTableTypes extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getTableTypes())
    }
    case class  GetTables(a: String, b: String, c: String, d: Array[String]) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getTables(a, b, c, d))
    }
    case object GetTimeDateFunctions extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getTimeDateFunctions())
    }
    case object GetTypeInfo extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getTypeInfo())
    }
    case class  GetUDTs(a: String, b: String, c: String, d: Array[Int]) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getUDTs(a, b, c, d))
    }
    case object GetURL extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getURL())
    }
    case object GetUserName extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getUserName())
    }
    case class  GetVersionColumns(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.getVersionColumns(a, b, c))
    }
    case class  InsertsAreDetected(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.insertsAreDetected(a))
    }
    case object IsCatalogAtStart extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.isCatalogAtStart())
    }
    case object IsReadOnly extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.isReadOnly())
    }
    case class  IsWrapperFor(a: Class[_]) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.isWrapperFor(a))
    }
    case object LocatorsUpdateCopy extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.locatorsUpdateCopy())
    }
    case object NullPlusNonNullIsNull extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.nullPlusNonNullIsNull())
    }
    case object NullsAreSortedAtEnd extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.nullsAreSortedAtEnd())
    }
    case object NullsAreSortedAtStart extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.nullsAreSortedAtStart())
    }
    case object NullsAreSortedHigh extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.nullsAreSortedHigh())
    }
    case object NullsAreSortedLow extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.nullsAreSortedLow())
    }
    case class  OthersDeletesAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.othersDeletesAreVisible(a))
    }
    case class  OthersInsertsAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.othersInsertsAreVisible(a))
    }
    case class  OthersUpdatesAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.othersUpdatesAreVisible(a))
    }
    case class  OwnDeletesAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.ownDeletesAreVisible(a))
    }
    case class  OwnInsertsAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.ownInsertsAreVisible(a))
    }
    case class  OwnUpdatesAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.ownUpdatesAreVisible(a))
    }
    case object StoresLowerCaseIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.storesLowerCaseIdentifiers())
    }
    case object StoresLowerCaseQuotedIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.storesLowerCaseQuotedIdentifiers())
    }
    case object StoresMixedCaseIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.storesMixedCaseIdentifiers())
    }
    case object StoresMixedCaseQuotedIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.storesMixedCaseQuotedIdentifiers())
    }
    case object StoresUpperCaseIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.storesUpperCaseIdentifiers())
    }
    case object StoresUpperCaseQuotedIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.storesUpperCaseQuotedIdentifiers())
    }
    case object SupportsANSI92EntryLevelSQL extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsANSI92EntryLevelSQL())
    }
    case object SupportsANSI92FullSQL extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsANSI92FullSQL())
    }
    case object SupportsANSI92IntermediateSQL extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsANSI92IntermediateSQL())
    }
    case object SupportsAlterTableWithAddColumn extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsAlterTableWithAddColumn())
    }
    case object SupportsAlterTableWithDropColumn extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsAlterTableWithDropColumn())
    }
    case object SupportsBatchUpdates extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsBatchUpdates())
    }
    case object SupportsCatalogsInDataManipulation extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsCatalogsInDataManipulation())
    }
    case object SupportsCatalogsInIndexDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsCatalogsInIndexDefinitions())
    }
    case object SupportsCatalogsInPrivilegeDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsCatalogsInPrivilegeDefinitions())
    }
    case object SupportsCatalogsInProcedureCalls extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsCatalogsInProcedureCalls())
    }
    case object SupportsCatalogsInTableDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsCatalogsInTableDefinitions())
    }
    case object SupportsColumnAliasing extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsColumnAliasing())
    }
    case object SupportsConvert extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsConvert())
    }
    case class  SupportsConvert1(a: Int, b: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsConvert(a, b))
    }
    case object SupportsCoreSQLGrammar extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsCoreSQLGrammar())
    }
    case object SupportsCorrelatedSubqueries extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsCorrelatedSubqueries())
    }
    case object SupportsDataDefinitionAndDataManipulationTransactions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsDataDefinitionAndDataManipulationTransactions())
    }
    case object SupportsDataManipulationTransactionsOnly extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsDataManipulationTransactionsOnly())
    }
    case object SupportsDifferentTableCorrelationNames extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsDifferentTableCorrelationNames())
    }
    case object SupportsExpressionsInOrderBy extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsExpressionsInOrderBy())
    }
    case object SupportsExtendedSQLGrammar extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsExtendedSQLGrammar())
    }
    case object SupportsFullOuterJoins extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsFullOuterJoins())
    }
    case object SupportsGetGeneratedKeys extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsGetGeneratedKeys())
    }
    case object SupportsGroupBy extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsGroupBy())
    }
    case object SupportsGroupByBeyondSelect extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsGroupByBeyondSelect())
    }
    case object SupportsGroupByUnrelated extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsGroupByUnrelated())
    }
    case object SupportsIntegrityEnhancementFacility extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsIntegrityEnhancementFacility())
    }
    case object SupportsLikeEscapeClause extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsLikeEscapeClause())
    }
    case object SupportsLimitedOuterJoins extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsLimitedOuterJoins())
    }
    case object SupportsMinimumSQLGrammar extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsMinimumSQLGrammar())
    }
    case object SupportsMixedCaseIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsMixedCaseIdentifiers())
    }
    case object SupportsMixedCaseQuotedIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsMixedCaseQuotedIdentifiers())
    }
    case object SupportsMultipleOpenResults extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsMultipleOpenResults())
    }
    case object SupportsMultipleResultSets extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsMultipleResultSets())
    }
    case object SupportsMultipleTransactions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsMultipleTransactions())
    }
    case object SupportsNamedParameters extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsNamedParameters())
    }
    case object SupportsNonNullableColumns extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsNonNullableColumns())
    }
    case object SupportsOpenCursorsAcrossCommit extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsOpenCursorsAcrossCommit())
    }
    case object SupportsOpenCursorsAcrossRollback extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsOpenCursorsAcrossRollback())
    }
    case object SupportsOpenStatementsAcrossCommit extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsOpenStatementsAcrossCommit())
    }
    case object SupportsOpenStatementsAcrossRollback extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsOpenStatementsAcrossRollback())
    }
    case object SupportsOrderByUnrelated extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsOrderByUnrelated())
    }
    case object SupportsOuterJoins extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsOuterJoins())
    }
    case object SupportsPositionedDelete extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsPositionedDelete())
    }
    case object SupportsPositionedUpdate extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsPositionedUpdate())
    }
    case object SupportsRefCursors extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsRefCursors())
    }
    case class  SupportsResultSetConcurrency(a: Int, b: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsResultSetConcurrency(a, b))
    }
    case class  SupportsResultSetHoldability(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsResultSetHoldability(a))
    }
    case class  SupportsResultSetType(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsResultSetType(a))
    }
    case object SupportsSavepoints extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSavepoints())
    }
    case object SupportsSchemasInDataManipulation extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSchemasInDataManipulation())
    }
    case object SupportsSchemasInIndexDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSchemasInIndexDefinitions())
    }
    case object SupportsSchemasInPrivilegeDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSchemasInPrivilegeDefinitions())
    }
    case object SupportsSchemasInProcedureCalls extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSchemasInProcedureCalls())
    }
    case object SupportsSchemasInTableDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSchemasInTableDefinitions())
    }
    case object SupportsSelectForUpdate extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSelectForUpdate())
    }
    case object SupportsStatementPooling extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsStatementPooling())
    }
    case object SupportsStoredFunctionsUsingCallSyntax extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsStoredFunctionsUsingCallSyntax())
    }
    case object SupportsStoredProcedures extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsStoredProcedures())
    }
    case object SupportsSubqueriesInComparisons extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSubqueriesInComparisons())
    }
    case object SupportsSubqueriesInExists extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSubqueriesInExists())
    }
    case object SupportsSubqueriesInIns extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSubqueriesInIns())
    }
    case object SupportsSubqueriesInQuantifieds extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsSubqueriesInQuantifieds())
    }
    case object SupportsTableCorrelationNames extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsTableCorrelationNames())
    }
    case class  SupportsTransactionIsolationLevel(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsTransactionIsolationLevel(a))
    }
    case object SupportsTransactions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsTransactions())
    }
    case object SupportsUnion extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsUnion())
    }
    case object SupportsUnionAll extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.supportsUnionAll())
    }
    case class  Unwrap[T](a: Class[T]) extends DatabaseMetaDataOp[T] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.unwrap(a))
    }
    case class  UpdatesAreDetected(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.updatesAreDetected(a))
    }
    case object UsesLocalFilePerTable extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.usesLocalFilePerTable())
    }
    case object UsesLocalFiles extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Monad: Catchable: Capture] = primitive(_.usesLocalFiles())
    }
#-scalaz
#+fs2
    case object AllProceduresAreCallable extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.allProceduresAreCallable())
    }
    case object AllTablesAreSelectable extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.allTablesAreSelectable())
    }
    case object AutoCommitFailureClosesAllResultSets extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.autoCommitFailureClosesAllResultSets())
    }
    case object DataDefinitionCausesTransactionCommit extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.dataDefinitionCausesTransactionCommit())
    }
    case object DataDefinitionIgnoredInTransactions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.dataDefinitionIgnoredInTransactions())
    }
    case class  DeletesAreDetected(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.deletesAreDetected(a))
    }
    case object DoesMaxRowSizeIncludeBlobs extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.doesMaxRowSizeIncludeBlobs())
    }
    case object GeneratedKeyAlwaysReturned extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.generatedKeyAlwaysReturned())
    }
    case class  GetAttributes(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getAttributes(a, b, c, d))
    }
    case class  GetBestRowIdentifier(a: String, b: String, c: String, d: Int, e: Boolean) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getBestRowIdentifier(a, b, c, d, e))
    }
    case object GetCatalogSeparator extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getCatalogSeparator())
    }
    case object GetCatalogTerm extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getCatalogTerm())
    }
    case object GetCatalogs extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getCatalogs())
    }
    case object GetClientInfoProperties extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getClientInfoProperties())
    }
    case class  GetColumnPrivileges(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getColumnPrivileges(a, b, c, d))
    }
    case class  GetColumns(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getColumns(a, b, c, d))
    }
    case object GetConnection extends DatabaseMetaDataOp[Connection] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getConnection())
    }
    case class  GetCrossReference(a: String, b: String, c: String, d: String, e: String, f: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getCrossReference(a, b, c, d, e, f))
    }
    case object GetDatabaseMajorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDatabaseMajorVersion())
    }
    case object GetDatabaseMinorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDatabaseMinorVersion())
    }
    case object GetDatabaseProductName extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDatabaseProductName())
    }
    case object GetDatabaseProductVersion extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDatabaseProductVersion())
    }
    case object GetDefaultTransactionIsolation extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDefaultTransactionIsolation())
    }
    case object GetDriverMajorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDriverMajorVersion())
    }
    case object GetDriverMinorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDriverMinorVersion())
    }
    case object GetDriverName extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDriverName())
    }
    case object GetDriverVersion extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getDriverVersion())
    }
    case class  GetExportedKeys(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getExportedKeys(a, b, c))
    }
    case object GetExtraNameCharacters extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getExtraNameCharacters())
    }
    case class  GetFunctionColumns(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getFunctionColumns(a, b, c, d))
    }
    case class  GetFunctions(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getFunctions(a, b, c))
    }
    case object GetIdentifierQuoteString extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getIdentifierQuoteString())
    }
    case class  GetImportedKeys(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getImportedKeys(a, b, c))
    }
    case class  GetIndexInfo(a: String, b: String, c: String, d: Boolean, e: Boolean) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getIndexInfo(a, b, c, d, e))
    }
    case object GetJDBCMajorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getJDBCMajorVersion())
    }
    case object GetJDBCMinorVersion extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getJDBCMinorVersion())
    }
    case object GetMaxBinaryLiteralLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxBinaryLiteralLength())
    }
    case object GetMaxCatalogNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxCatalogNameLength())
    }
    case object GetMaxCharLiteralLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxCharLiteralLength())
    }
    case object GetMaxColumnNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxColumnNameLength())
    }
    case object GetMaxColumnsInGroupBy extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxColumnsInGroupBy())
    }
    case object GetMaxColumnsInIndex extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxColumnsInIndex())
    }
    case object GetMaxColumnsInOrderBy extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxColumnsInOrderBy())
    }
    case object GetMaxColumnsInSelect extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxColumnsInSelect())
    }
    case object GetMaxColumnsInTable extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxColumnsInTable())
    }
    case object GetMaxConnections extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxConnections())
    }
    case object GetMaxCursorNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxCursorNameLength())
    }
    case object GetMaxIndexLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxIndexLength())
    }
    case object GetMaxLogicalLobSize extends DatabaseMetaDataOp[Long] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxLogicalLobSize())
    }
    case object GetMaxProcedureNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxProcedureNameLength())
    }
    case object GetMaxRowSize extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxRowSize())
    }
    case object GetMaxSchemaNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxSchemaNameLength())
    }
    case object GetMaxStatementLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxStatementLength())
    }
    case object GetMaxStatements extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxStatements())
    }
    case object GetMaxTableNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxTableNameLength())
    }
    case object GetMaxTablesInSelect extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxTablesInSelect())
    }
    case object GetMaxUserNameLength extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getMaxUserNameLength())
    }
    case object GetNumericFunctions extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getNumericFunctions())
    }
    case class  GetPrimaryKeys(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getPrimaryKeys(a, b, c))
    }
    case class  GetProcedureColumns(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getProcedureColumns(a, b, c, d))
    }
    case object GetProcedureTerm extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getProcedureTerm())
    }
    case class  GetProcedures(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getProcedures(a, b, c))
    }
    case class  GetPseudoColumns(a: String, b: String, c: String, d: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getPseudoColumns(a, b, c, d))
    }
    case object GetResultSetHoldability extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getResultSetHoldability())
    }
    case object GetRowIdLifetime extends DatabaseMetaDataOp[RowIdLifetime] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getRowIdLifetime())
    }
    case object GetSQLKeywords extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSQLKeywords())
    }
    case object GetSQLStateType extends DatabaseMetaDataOp[Int] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSQLStateType())
    }
    case object GetSchemaTerm extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSchemaTerm())
    }
    case object GetSchemas extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSchemas())
    }
    case class  GetSchemas1(a: String, b: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSchemas(a, b))
    }
    case object GetSearchStringEscape extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSearchStringEscape())
    }
    case object GetStringFunctions extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getStringFunctions())
    }
    case class  GetSuperTables(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSuperTables(a, b, c))
    }
    case class  GetSuperTypes(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSuperTypes(a, b, c))
    }
    case object GetSystemFunctions extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getSystemFunctions())
    }
    case class  GetTablePrivileges(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getTablePrivileges(a, b, c))
    }
    case object GetTableTypes extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getTableTypes())
    }
    case class  GetTables(a: String, b: String, c: String, d: Array[String]) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getTables(a, b, c, d))
    }
    case object GetTimeDateFunctions extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getTimeDateFunctions())
    }
    case object GetTypeInfo extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getTypeInfo())
    }
    case class  GetUDTs(a: String, b: String, c: String, d: Array[Int]) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getUDTs(a, b, c, d))
    }
    case object GetURL extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getURL())
    }
    case object GetUserName extends DatabaseMetaDataOp[String] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getUserName())
    }
    case class  GetVersionColumns(a: String, b: String, c: String) extends DatabaseMetaDataOp[ResultSet] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.getVersionColumns(a, b, c))
    }
    case class  InsertsAreDetected(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.insertsAreDetected(a))
    }
    case object IsCatalogAtStart extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.isCatalogAtStart())
    }
    case object IsReadOnly extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.isReadOnly())
    }
    case class  IsWrapperFor(a: Class[_]) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.isWrapperFor(a))
    }
    case object LocatorsUpdateCopy extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.locatorsUpdateCopy())
    }
    case object NullPlusNonNullIsNull extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.nullPlusNonNullIsNull())
    }
    case object NullsAreSortedAtEnd extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.nullsAreSortedAtEnd())
    }
    case object NullsAreSortedAtStart extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.nullsAreSortedAtStart())
    }
    case object NullsAreSortedHigh extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.nullsAreSortedHigh())
    }
    case object NullsAreSortedLow extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.nullsAreSortedLow())
    }
    case class  OthersDeletesAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.othersDeletesAreVisible(a))
    }
    case class  OthersInsertsAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.othersInsertsAreVisible(a))
    }
    case class  OthersUpdatesAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.othersUpdatesAreVisible(a))
    }
    case class  OwnDeletesAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.ownDeletesAreVisible(a))
    }
    case class  OwnInsertsAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.ownInsertsAreVisible(a))
    }
    case class  OwnUpdatesAreVisible(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.ownUpdatesAreVisible(a))
    }
    case object StoresLowerCaseIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.storesLowerCaseIdentifiers())
    }
    case object StoresLowerCaseQuotedIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.storesLowerCaseQuotedIdentifiers())
    }
    case object StoresMixedCaseIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.storesMixedCaseIdentifiers())
    }
    case object StoresMixedCaseQuotedIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.storesMixedCaseQuotedIdentifiers())
    }
    case object StoresUpperCaseIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.storesUpperCaseIdentifiers())
    }
    case object StoresUpperCaseQuotedIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.storesUpperCaseQuotedIdentifiers())
    }
    case object SupportsANSI92EntryLevelSQL extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsANSI92EntryLevelSQL())
    }
    case object SupportsANSI92FullSQL extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsANSI92FullSQL())
    }
    case object SupportsANSI92IntermediateSQL extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsANSI92IntermediateSQL())
    }
    case object SupportsAlterTableWithAddColumn extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsAlterTableWithAddColumn())
    }
    case object SupportsAlterTableWithDropColumn extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsAlterTableWithDropColumn())
    }
    case object SupportsBatchUpdates extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsBatchUpdates())
    }
    case object SupportsCatalogsInDataManipulation extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsCatalogsInDataManipulation())
    }
    case object SupportsCatalogsInIndexDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsCatalogsInIndexDefinitions())
    }
    case object SupportsCatalogsInPrivilegeDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsCatalogsInPrivilegeDefinitions())
    }
    case object SupportsCatalogsInProcedureCalls extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsCatalogsInProcedureCalls())
    }
    case object SupportsCatalogsInTableDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsCatalogsInTableDefinitions())
    }
    case object SupportsColumnAliasing extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsColumnAliasing())
    }
    case object SupportsConvert extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsConvert())
    }
    case class  SupportsConvert1(a: Int, b: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsConvert(a, b))
    }
    case object SupportsCoreSQLGrammar extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsCoreSQLGrammar())
    }
    case object SupportsCorrelatedSubqueries extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsCorrelatedSubqueries())
    }
    case object SupportsDataDefinitionAndDataManipulationTransactions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsDataDefinitionAndDataManipulationTransactions())
    }
    case object SupportsDataManipulationTransactionsOnly extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsDataManipulationTransactionsOnly())
    }
    case object SupportsDifferentTableCorrelationNames extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsDifferentTableCorrelationNames())
    }
    case object SupportsExpressionsInOrderBy extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsExpressionsInOrderBy())
    }
    case object SupportsExtendedSQLGrammar extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsExtendedSQLGrammar())
    }
    case object SupportsFullOuterJoins extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsFullOuterJoins())
    }
    case object SupportsGetGeneratedKeys extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsGetGeneratedKeys())
    }
    case object SupportsGroupBy extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsGroupBy())
    }
    case object SupportsGroupByBeyondSelect extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsGroupByBeyondSelect())
    }
    case object SupportsGroupByUnrelated extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsGroupByUnrelated())
    }
    case object SupportsIntegrityEnhancementFacility extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsIntegrityEnhancementFacility())
    }
    case object SupportsLikeEscapeClause extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsLikeEscapeClause())
    }
    case object SupportsLimitedOuterJoins extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsLimitedOuterJoins())
    }
    case object SupportsMinimumSQLGrammar extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsMinimumSQLGrammar())
    }
    case object SupportsMixedCaseIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsMixedCaseIdentifiers())
    }
    case object SupportsMixedCaseQuotedIdentifiers extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsMixedCaseQuotedIdentifiers())
    }
    case object SupportsMultipleOpenResults extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsMultipleOpenResults())
    }
    case object SupportsMultipleResultSets extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsMultipleResultSets())
    }
    case object SupportsMultipleTransactions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsMultipleTransactions())
    }
    case object SupportsNamedParameters extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsNamedParameters())
    }
    case object SupportsNonNullableColumns extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsNonNullableColumns())
    }
    case object SupportsOpenCursorsAcrossCommit extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsOpenCursorsAcrossCommit())
    }
    case object SupportsOpenCursorsAcrossRollback extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsOpenCursorsAcrossRollback())
    }
    case object SupportsOpenStatementsAcrossCommit extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsOpenStatementsAcrossCommit())
    }
    case object SupportsOpenStatementsAcrossRollback extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsOpenStatementsAcrossRollback())
    }
    case object SupportsOrderByUnrelated extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsOrderByUnrelated())
    }
    case object SupportsOuterJoins extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsOuterJoins())
    }
    case object SupportsPositionedDelete extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsPositionedDelete())
    }
    case object SupportsPositionedUpdate extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsPositionedUpdate())
    }
    case object SupportsRefCursors extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsRefCursors())
    }
    case class  SupportsResultSetConcurrency(a: Int, b: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsResultSetConcurrency(a, b))
    }
    case class  SupportsResultSetHoldability(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsResultSetHoldability(a))
    }
    case class  SupportsResultSetType(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsResultSetType(a))
    }
    case object SupportsSavepoints extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSavepoints())
    }
    case object SupportsSchemasInDataManipulation extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSchemasInDataManipulation())
    }
    case object SupportsSchemasInIndexDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSchemasInIndexDefinitions())
    }
    case object SupportsSchemasInPrivilegeDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSchemasInPrivilegeDefinitions())
    }
    case object SupportsSchemasInProcedureCalls extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSchemasInProcedureCalls())
    }
    case object SupportsSchemasInTableDefinitions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSchemasInTableDefinitions())
    }
    case object SupportsSelectForUpdate extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSelectForUpdate())
    }
    case object SupportsStatementPooling extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsStatementPooling())
    }
    case object SupportsStoredFunctionsUsingCallSyntax extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsStoredFunctionsUsingCallSyntax())
    }
    case object SupportsStoredProcedures extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsStoredProcedures())
    }
    case object SupportsSubqueriesInComparisons extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSubqueriesInComparisons())
    }
    case object SupportsSubqueriesInExists extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSubqueriesInExists())
    }
    case object SupportsSubqueriesInIns extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSubqueriesInIns())
    }
    case object SupportsSubqueriesInQuantifieds extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsSubqueriesInQuantifieds())
    }
    case object SupportsTableCorrelationNames extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsTableCorrelationNames())
    }
    case class  SupportsTransactionIsolationLevel(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsTransactionIsolationLevel(a))
    }
    case object SupportsTransactions extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsTransactions())
    }
    case object SupportsUnion extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsUnion())
    }
    case object SupportsUnionAll extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.supportsUnionAll())
    }
    case class  Unwrap[T](a: Class[T]) extends DatabaseMetaDataOp[T] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.unwrap(a))
    }
    case class  UpdatesAreDetected(a: Int) extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.updatesAreDetected(a))
    }
    case object UsesLocalFilePerTable extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.usesLocalFilePerTable())
    }
    case object UsesLocalFiles extends DatabaseMetaDataOp[Boolean] {
      override def defaultTransK[M[_]: Catchable: Suspendable] = primitive(_.usesLocalFiles())
    }
#-fs2

  }
  import DatabaseMetaDataOp._ // We use these immediately

  /**
   * Free monad over a free functor of [[DatabaseMetaDataOp]]; abstractly, a computation that consumes
   * a `java.sql.DatabaseMetaData` and produces a value of type `A`.
   * @group Algebra
   */
  type DatabaseMetaDataIO[A] = F[DatabaseMetaDataOp, A]

  /**
   * Catchable instance for [[DatabaseMetaDataIO]].
   * @group Typeclass Instances
   */
  implicit val CatchableDatabaseMetaDataIO: Catchable[DatabaseMetaDataIO] =
    new Catchable[DatabaseMetaDataIO] {
#+fs2
      def pure[A](a: A): DatabaseMetaDataIO[A] = databasemetadata.delay(a)
      override def map[A, B](fa: DatabaseMetaDataIO[A])(f: A => B): DatabaseMetaDataIO[B] = fa.map(f)
      def flatMap[A, B](fa: DatabaseMetaDataIO[A])(f: A => DatabaseMetaDataIO[B]): DatabaseMetaDataIO[B] = fa.flatMap(f)
#-fs2
      def attempt[A](f: DatabaseMetaDataIO[A]): DatabaseMetaDataIO[Throwable \/ A] = databasemetadata.attempt(f)
      def fail[A](err: Throwable): DatabaseMetaDataIO[A] = databasemetadata.delay(throw err)
    }

#+scalaz
  /**
   * Capture instance for [[DatabaseMetaDataIO]].
   * @group Typeclass Instances
   */
  implicit val CaptureDatabaseMetaDataIO: Capture[DatabaseMetaDataIO] =
    new Capture[DatabaseMetaDataIO] {
      def apply[A](a: => A): DatabaseMetaDataIO[A] = databasemetadata.delay(a)
    }
#-scalaz

  /**
   * Lift a different type of program that has a default Kleisli interpreter.
   * @group Constructors (Lifting)
   */
  def lift[Op[_], A, J](j: J, action: F[Op, A])(implicit mod: KleisliTrans.Aux[Op, J]): DatabaseMetaDataIO[A] =
    F.liftF[DatabaseMetaDataOp, A](Lift(j, action, mod))

  /**
   * Lift a DatabaseMetaDataIO[A] into an exception-capturing DatabaseMetaDataIO[Throwable \/ A].
   * @group Constructors (Lifting)
   */
  def attempt[A](a: DatabaseMetaDataIO[A]): DatabaseMetaDataIO[Throwable \/ A] =
    F.liftF[DatabaseMetaDataOp, Throwable \/ A](Attempt(a))

  /**
   * Non-strict unit for capturing effects.
   * @group Constructors (Lifting)
   */
  def delay[A](a: => A): DatabaseMetaDataIO[A] =
    F.liftF(Pure(a _))

  /**
   * Backdoor for arbitrary computations on the underlying DatabaseMetaData.
   * @group Constructors (Lifting)
   */
  def raw[A](f: DatabaseMetaData => A): DatabaseMetaDataIO[A] =
    F.liftF(Raw(f))

  /**
   * @group Constructors (Primitives)
   */
  val allProceduresAreCallable: DatabaseMetaDataIO[Boolean] =
    F.liftF(AllProceduresAreCallable)

  /**
   * @group Constructors (Primitives)
   */
  val allTablesAreSelectable: DatabaseMetaDataIO[Boolean] =
    F.liftF(AllTablesAreSelectable)

  /**
   * @group Constructors (Primitives)
   */
  val autoCommitFailureClosesAllResultSets: DatabaseMetaDataIO[Boolean] =
    F.liftF(AutoCommitFailureClosesAllResultSets)

  /**
   * @group Constructors (Primitives)
   */
  val dataDefinitionCausesTransactionCommit: DatabaseMetaDataIO[Boolean] =
    F.liftF(DataDefinitionCausesTransactionCommit)

  /**
   * @group Constructors (Primitives)
   */
  val dataDefinitionIgnoredInTransactions: DatabaseMetaDataIO[Boolean] =
    F.liftF(DataDefinitionIgnoredInTransactions)

  /**
   * @group Constructors (Primitives)
   */
  def deletesAreDetected(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(DeletesAreDetected(a))

  /**
   * @group Constructors (Primitives)
   */
  val doesMaxRowSizeIncludeBlobs: DatabaseMetaDataIO[Boolean] =
    F.liftF(DoesMaxRowSizeIncludeBlobs)

  /**
   * @group Constructors (Primitives)
   */
  val generatedKeyAlwaysReturned: DatabaseMetaDataIO[Boolean] =
    F.liftF(GeneratedKeyAlwaysReturned)

  /**
   * @group Constructors (Primitives)
   */
  def getAttributes(a: String, b: String, c: String, d: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetAttributes(a, b, c, d))

  /**
   * @group Constructors (Primitives)
   */
  def getBestRowIdentifier(a: String, b: String, c: String, d: Int, e: Boolean): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetBestRowIdentifier(a, b, c, d, e))

  /**
   * @group Constructors (Primitives)
   */
  val getCatalogSeparator: DatabaseMetaDataIO[String] =
    F.liftF(GetCatalogSeparator)

  /**
   * @group Constructors (Primitives)
   */
  val getCatalogTerm: DatabaseMetaDataIO[String] =
    F.liftF(GetCatalogTerm)

  /**
   * @group Constructors (Primitives)
   */
  val getCatalogs: DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetCatalogs)

  /**
   * @group Constructors (Primitives)
   */
  val getClientInfoProperties: DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetClientInfoProperties)

  /**
   * @group Constructors (Primitives)
   */
  def getColumnPrivileges(a: String, b: String, c: String, d: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetColumnPrivileges(a, b, c, d))

  /**
   * @group Constructors (Primitives)
   */
  def getColumns(a: String, b: String, c: String, d: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetColumns(a, b, c, d))

  /**
   * @group Constructors (Primitives)
   */
  val getConnection: DatabaseMetaDataIO[Connection] =
    F.liftF(GetConnection)

  /**
   * @group Constructors (Primitives)
   */
  def getCrossReference(a: String, b: String, c: String, d: String, e: String, f: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetCrossReference(a, b, c, d, e, f))

  /**
   * @group Constructors (Primitives)
   */
  val getDatabaseMajorVersion: DatabaseMetaDataIO[Int] =
    F.liftF(GetDatabaseMajorVersion)

  /**
   * @group Constructors (Primitives)
   */
  val getDatabaseMinorVersion: DatabaseMetaDataIO[Int] =
    F.liftF(GetDatabaseMinorVersion)

  /**
   * @group Constructors (Primitives)
   */
  val getDatabaseProductName: DatabaseMetaDataIO[String] =
    F.liftF(GetDatabaseProductName)

  /**
   * @group Constructors (Primitives)
   */
  val getDatabaseProductVersion: DatabaseMetaDataIO[String] =
    F.liftF(GetDatabaseProductVersion)

  /**
   * @group Constructors (Primitives)
   */
  val getDefaultTransactionIsolation: DatabaseMetaDataIO[Int] =
    F.liftF(GetDefaultTransactionIsolation)

  /**
   * @group Constructors (Primitives)
   */
  val getDriverMajorVersion: DatabaseMetaDataIO[Int] =
    F.liftF(GetDriverMajorVersion)

  /**
   * @group Constructors (Primitives)
   */
  val getDriverMinorVersion: DatabaseMetaDataIO[Int] =
    F.liftF(GetDriverMinorVersion)

  /**
   * @group Constructors (Primitives)
   */
  val getDriverName: DatabaseMetaDataIO[String] =
    F.liftF(GetDriverName)

  /**
   * @group Constructors (Primitives)
   */
  val getDriverVersion: DatabaseMetaDataIO[String] =
    F.liftF(GetDriverVersion)

  /**
   * @group Constructors (Primitives)
   */
  def getExportedKeys(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetExportedKeys(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  val getExtraNameCharacters: DatabaseMetaDataIO[String] =
    F.liftF(GetExtraNameCharacters)

  /**
   * @group Constructors (Primitives)
   */
  def getFunctionColumns(a: String, b: String, c: String, d: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetFunctionColumns(a, b, c, d))

  /**
   * @group Constructors (Primitives)
   */
  def getFunctions(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetFunctions(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  val getIdentifierQuoteString: DatabaseMetaDataIO[String] =
    F.liftF(GetIdentifierQuoteString)

  /**
   * @group Constructors (Primitives)
   */
  def getImportedKeys(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetImportedKeys(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  def getIndexInfo(a: String, b: String, c: String, d: Boolean, e: Boolean): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetIndexInfo(a, b, c, d, e))

  /**
   * @group Constructors (Primitives)
   */
  val getJDBCMajorVersion: DatabaseMetaDataIO[Int] =
    F.liftF(GetJDBCMajorVersion)

  /**
   * @group Constructors (Primitives)
   */
  val getJDBCMinorVersion: DatabaseMetaDataIO[Int] =
    F.liftF(GetJDBCMinorVersion)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxBinaryLiteralLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxBinaryLiteralLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxCatalogNameLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxCatalogNameLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxCharLiteralLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxCharLiteralLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxColumnNameLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxColumnNameLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxColumnsInGroupBy: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxColumnsInGroupBy)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxColumnsInIndex: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxColumnsInIndex)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxColumnsInOrderBy: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxColumnsInOrderBy)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxColumnsInSelect: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxColumnsInSelect)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxColumnsInTable: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxColumnsInTable)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxConnections: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxConnections)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxCursorNameLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxCursorNameLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxIndexLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxIndexLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxLogicalLobSize: DatabaseMetaDataIO[Long] =
    F.liftF(GetMaxLogicalLobSize)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxProcedureNameLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxProcedureNameLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxRowSize: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxRowSize)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxSchemaNameLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxSchemaNameLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxStatementLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxStatementLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxStatements: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxStatements)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxTableNameLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxTableNameLength)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxTablesInSelect: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxTablesInSelect)

  /**
   * @group Constructors (Primitives)
   */
  val getMaxUserNameLength: DatabaseMetaDataIO[Int] =
    F.liftF(GetMaxUserNameLength)

  /**
   * @group Constructors (Primitives)
   */
  val getNumericFunctions: DatabaseMetaDataIO[String] =
    F.liftF(GetNumericFunctions)

  /**
   * @group Constructors (Primitives)
   */
  def getPrimaryKeys(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetPrimaryKeys(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  def getProcedureColumns(a: String, b: String, c: String, d: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetProcedureColumns(a, b, c, d))

  /**
   * @group Constructors (Primitives)
   */
  val getProcedureTerm: DatabaseMetaDataIO[String] =
    F.liftF(GetProcedureTerm)

  /**
   * @group Constructors (Primitives)
   */
  def getProcedures(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetProcedures(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  def getPseudoColumns(a: String, b: String, c: String, d: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetPseudoColumns(a, b, c, d))

  /**
   * @group Constructors (Primitives)
   */
  val getResultSetHoldability: DatabaseMetaDataIO[Int] =
    F.liftF(GetResultSetHoldability)

  /**
   * @group Constructors (Primitives)
   */
  val getRowIdLifetime: DatabaseMetaDataIO[RowIdLifetime] =
    F.liftF(GetRowIdLifetime)

  /**
   * @group Constructors (Primitives)
   */
  val getSQLKeywords: DatabaseMetaDataIO[String] =
    F.liftF(GetSQLKeywords)

  /**
   * @group Constructors (Primitives)
   */
  val getSQLStateType: DatabaseMetaDataIO[Int] =
    F.liftF(GetSQLStateType)

  /**
   * @group Constructors (Primitives)
   */
  val getSchemaTerm: DatabaseMetaDataIO[String] =
    F.liftF(GetSchemaTerm)

  /**
   * @group Constructors (Primitives)
   */
  val getSchemas: DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetSchemas)

  /**
   * @group Constructors (Primitives)
   */
  def getSchemas(a: String, b: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetSchemas1(a, b))

  /**
   * @group Constructors (Primitives)
   */
  val getSearchStringEscape: DatabaseMetaDataIO[String] =
    F.liftF(GetSearchStringEscape)

  /**
   * @group Constructors (Primitives)
   */
  val getStringFunctions: DatabaseMetaDataIO[String] =
    F.liftF(GetStringFunctions)

  /**
   * @group Constructors (Primitives)
   */
  def getSuperTables(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetSuperTables(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  def getSuperTypes(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetSuperTypes(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  val getSystemFunctions: DatabaseMetaDataIO[String] =
    F.liftF(GetSystemFunctions)

  /**
   * @group Constructors (Primitives)
   */
  def getTablePrivileges(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetTablePrivileges(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  val getTableTypes: DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetTableTypes)

  /**
   * @group Constructors (Primitives)
   */
  def getTables(a: String, b: String, c: String, d: Array[String]): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetTables(a, b, c, d))

  /**
   * @group Constructors (Primitives)
   */
  val getTimeDateFunctions: DatabaseMetaDataIO[String] =
    F.liftF(GetTimeDateFunctions)

  /**
   * @group Constructors (Primitives)
   */
  val getTypeInfo: DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetTypeInfo)

  /**
   * @group Constructors (Primitives)
   */
  def getUDTs(a: String, b: String, c: String, d: Array[Int]): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetUDTs(a, b, c, d))

  /**
   * @group Constructors (Primitives)
   */
  val getURL: DatabaseMetaDataIO[String] =
    F.liftF(GetURL)

  /**
   * @group Constructors (Primitives)
   */
  val getUserName: DatabaseMetaDataIO[String] =
    F.liftF(GetUserName)

  /**
   * @group Constructors (Primitives)
   */
  def getVersionColumns(a: String, b: String, c: String): DatabaseMetaDataIO[ResultSet] =
    F.liftF(GetVersionColumns(a, b, c))

  /**
   * @group Constructors (Primitives)
   */
  def insertsAreDetected(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(InsertsAreDetected(a))

  /**
   * @group Constructors (Primitives)
   */
  val isCatalogAtStart: DatabaseMetaDataIO[Boolean] =
    F.liftF(IsCatalogAtStart)

  /**
   * @group Constructors (Primitives)
   */
  val isReadOnly: DatabaseMetaDataIO[Boolean] =
    F.liftF(IsReadOnly)

  /**
   * @group Constructors (Primitives)
   */
  def isWrapperFor(a: Class[_]): DatabaseMetaDataIO[Boolean] =
    F.liftF(IsWrapperFor(a))

  /**
   * @group Constructors (Primitives)
   */
  val locatorsUpdateCopy: DatabaseMetaDataIO[Boolean] =
    F.liftF(LocatorsUpdateCopy)

  /**
   * @group Constructors (Primitives)
   */
  val nullPlusNonNullIsNull: DatabaseMetaDataIO[Boolean] =
    F.liftF(NullPlusNonNullIsNull)

  /**
   * @group Constructors (Primitives)
   */
  val nullsAreSortedAtEnd: DatabaseMetaDataIO[Boolean] =
    F.liftF(NullsAreSortedAtEnd)

  /**
   * @group Constructors (Primitives)
   */
  val nullsAreSortedAtStart: DatabaseMetaDataIO[Boolean] =
    F.liftF(NullsAreSortedAtStart)

  /**
   * @group Constructors (Primitives)
   */
  val nullsAreSortedHigh: DatabaseMetaDataIO[Boolean] =
    F.liftF(NullsAreSortedHigh)

  /**
   * @group Constructors (Primitives)
   */
  val nullsAreSortedLow: DatabaseMetaDataIO[Boolean] =
    F.liftF(NullsAreSortedLow)

  /**
   * @group Constructors (Primitives)
   */
  def othersDeletesAreVisible(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(OthersDeletesAreVisible(a))

  /**
   * @group Constructors (Primitives)
   */
  def othersInsertsAreVisible(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(OthersInsertsAreVisible(a))

  /**
   * @group Constructors (Primitives)
   */
  def othersUpdatesAreVisible(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(OthersUpdatesAreVisible(a))

  /**
   * @group Constructors (Primitives)
   */
  def ownDeletesAreVisible(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(OwnDeletesAreVisible(a))

  /**
   * @group Constructors (Primitives)
   */
  def ownInsertsAreVisible(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(OwnInsertsAreVisible(a))

  /**
   * @group Constructors (Primitives)
   */
  def ownUpdatesAreVisible(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(OwnUpdatesAreVisible(a))

  /**
   * @group Constructors (Primitives)
   */
  val storesLowerCaseIdentifiers: DatabaseMetaDataIO[Boolean] =
    F.liftF(StoresLowerCaseIdentifiers)

  /**
   * @group Constructors (Primitives)
   */
  val storesLowerCaseQuotedIdentifiers: DatabaseMetaDataIO[Boolean] =
    F.liftF(StoresLowerCaseQuotedIdentifiers)

  /**
   * @group Constructors (Primitives)
   */
  val storesMixedCaseIdentifiers: DatabaseMetaDataIO[Boolean] =
    F.liftF(StoresMixedCaseIdentifiers)

  /**
   * @group Constructors (Primitives)
   */
  val storesMixedCaseQuotedIdentifiers: DatabaseMetaDataIO[Boolean] =
    F.liftF(StoresMixedCaseQuotedIdentifiers)

  /**
   * @group Constructors (Primitives)
   */
  val storesUpperCaseIdentifiers: DatabaseMetaDataIO[Boolean] =
    F.liftF(StoresUpperCaseIdentifiers)

  /**
   * @group Constructors (Primitives)
   */
  val storesUpperCaseQuotedIdentifiers: DatabaseMetaDataIO[Boolean] =
    F.liftF(StoresUpperCaseQuotedIdentifiers)

  /**
   * @group Constructors (Primitives)
   */
  val supportsANSI92EntryLevelSQL: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsANSI92EntryLevelSQL)

  /**
   * @group Constructors (Primitives)
   */
  val supportsANSI92FullSQL: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsANSI92FullSQL)

  /**
   * @group Constructors (Primitives)
   */
  val supportsANSI92IntermediateSQL: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsANSI92IntermediateSQL)

  /**
   * @group Constructors (Primitives)
   */
  val supportsAlterTableWithAddColumn: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsAlterTableWithAddColumn)

  /**
   * @group Constructors (Primitives)
   */
  val supportsAlterTableWithDropColumn: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsAlterTableWithDropColumn)

  /**
   * @group Constructors (Primitives)
   */
  val supportsBatchUpdates: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsBatchUpdates)

  /**
   * @group Constructors (Primitives)
   */
  val supportsCatalogsInDataManipulation: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsCatalogsInDataManipulation)

  /**
   * @group Constructors (Primitives)
   */
  val supportsCatalogsInIndexDefinitions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsCatalogsInIndexDefinitions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsCatalogsInPrivilegeDefinitions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsCatalogsInPrivilegeDefinitions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsCatalogsInProcedureCalls: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsCatalogsInProcedureCalls)

  /**
   * @group Constructors (Primitives)
   */
  val supportsCatalogsInTableDefinitions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsCatalogsInTableDefinitions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsColumnAliasing: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsColumnAliasing)

  /**
   * @group Constructors (Primitives)
   */
  val supportsConvert: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsConvert)

  /**
   * @group Constructors (Primitives)
   */
  def supportsConvert(a: Int, b: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsConvert1(a, b))

  /**
   * @group Constructors (Primitives)
   */
  val supportsCoreSQLGrammar: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsCoreSQLGrammar)

  /**
   * @group Constructors (Primitives)
   */
  val supportsCorrelatedSubqueries: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsCorrelatedSubqueries)

  /**
   * @group Constructors (Primitives)
   */
  val supportsDataDefinitionAndDataManipulationTransactions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsDataDefinitionAndDataManipulationTransactions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsDataManipulationTransactionsOnly: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsDataManipulationTransactionsOnly)

  /**
   * @group Constructors (Primitives)
   */
  val supportsDifferentTableCorrelationNames: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsDifferentTableCorrelationNames)

  /**
   * @group Constructors (Primitives)
   */
  val supportsExpressionsInOrderBy: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsExpressionsInOrderBy)

  /**
   * @group Constructors (Primitives)
   */
  val supportsExtendedSQLGrammar: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsExtendedSQLGrammar)

  /**
   * @group Constructors (Primitives)
   */
  val supportsFullOuterJoins: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsFullOuterJoins)

  /**
   * @group Constructors (Primitives)
   */
  val supportsGetGeneratedKeys: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsGetGeneratedKeys)

  /**
   * @group Constructors (Primitives)
   */
  val supportsGroupBy: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsGroupBy)

  /**
   * @group Constructors (Primitives)
   */
  val supportsGroupByBeyondSelect: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsGroupByBeyondSelect)

  /**
   * @group Constructors (Primitives)
   */
  val supportsGroupByUnrelated: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsGroupByUnrelated)

  /**
   * @group Constructors (Primitives)
   */
  val supportsIntegrityEnhancementFacility: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsIntegrityEnhancementFacility)

  /**
   * @group Constructors (Primitives)
   */
  val supportsLikeEscapeClause: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsLikeEscapeClause)

  /**
   * @group Constructors (Primitives)
   */
  val supportsLimitedOuterJoins: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsLimitedOuterJoins)

  /**
   * @group Constructors (Primitives)
   */
  val supportsMinimumSQLGrammar: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsMinimumSQLGrammar)

  /**
   * @group Constructors (Primitives)
   */
  val supportsMixedCaseIdentifiers: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsMixedCaseIdentifiers)

  /**
   * @group Constructors (Primitives)
   */
  val supportsMixedCaseQuotedIdentifiers: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsMixedCaseQuotedIdentifiers)

  /**
   * @group Constructors (Primitives)
   */
  val supportsMultipleOpenResults: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsMultipleOpenResults)

  /**
   * @group Constructors (Primitives)
   */
  val supportsMultipleResultSets: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsMultipleResultSets)

  /**
   * @group Constructors (Primitives)
   */
  val supportsMultipleTransactions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsMultipleTransactions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsNamedParameters: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsNamedParameters)

  /**
   * @group Constructors (Primitives)
   */
  val supportsNonNullableColumns: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsNonNullableColumns)

  /**
   * @group Constructors (Primitives)
   */
  val supportsOpenCursorsAcrossCommit: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsOpenCursorsAcrossCommit)

  /**
   * @group Constructors (Primitives)
   */
  val supportsOpenCursorsAcrossRollback: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsOpenCursorsAcrossRollback)

  /**
   * @group Constructors (Primitives)
   */
  val supportsOpenStatementsAcrossCommit: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsOpenStatementsAcrossCommit)

  /**
   * @group Constructors (Primitives)
   */
  val supportsOpenStatementsAcrossRollback: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsOpenStatementsAcrossRollback)

  /**
   * @group Constructors (Primitives)
   */
  val supportsOrderByUnrelated: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsOrderByUnrelated)

  /**
   * @group Constructors (Primitives)
   */
  val supportsOuterJoins: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsOuterJoins)

  /**
   * @group Constructors (Primitives)
   */
  val supportsPositionedDelete: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsPositionedDelete)

  /**
   * @group Constructors (Primitives)
   */
  val supportsPositionedUpdate: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsPositionedUpdate)

  /**
   * @group Constructors (Primitives)
   */
  val supportsRefCursors: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsRefCursors)

  /**
   * @group Constructors (Primitives)
   */
  def supportsResultSetConcurrency(a: Int, b: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsResultSetConcurrency(a, b))

  /**
   * @group Constructors (Primitives)
   */
  def supportsResultSetHoldability(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsResultSetHoldability(a))

  /**
   * @group Constructors (Primitives)
   */
  def supportsResultSetType(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsResultSetType(a))

  /**
   * @group Constructors (Primitives)
   */
  val supportsSavepoints: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSavepoints)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSchemasInDataManipulation: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSchemasInDataManipulation)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSchemasInIndexDefinitions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSchemasInIndexDefinitions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSchemasInPrivilegeDefinitions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSchemasInPrivilegeDefinitions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSchemasInProcedureCalls: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSchemasInProcedureCalls)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSchemasInTableDefinitions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSchemasInTableDefinitions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSelectForUpdate: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSelectForUpdate)

  /**
   * @group Constructors (Primitives)
   */
  val supportsStatementPooling: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsStatementPooling)

  /**
   * @group Constructors (Primitives)
   */
  val supportsStoredFunctionsUsingCallSyntax: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsStoredFunctionsUsingCallSyntax)

  /**
   * @group Constructors (Primitives)
   */
  val supportsStoredProcedures: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsStoredProcedures)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSubqueriesInComparisons: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSubqueriesInComparisons)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSubqueriesInExists: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSubqueriesInExists)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSubqueriesInIns: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSubqueriesInIns)

  /**
   * @group Constructors (Primitives)
   */
  val supportsSubqueriesInQuantifieds: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsSubqueriesInQuantifieds)

  /**
   * @group Constructors (Primitives)
   */
  val supportsTableCorrelationNames: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsTableCorrelationNames)

  /**
   * @group Constructors (Primitives)
   */
  def supportsTransactionIsolationLevel(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsTransactionIsolationLevel(a))

  /**
   * @group Constructors (Primitives)
   */
  val supportsTransactions: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsTransactions)

  /**
   * @group Constructors (Primitives)
   */
  val supportsUnion: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsUnion)

  /**
   * @group Constructors (Primitives)
   */
  val supportsUnionAll: DatabaseMetaDataIO[Boolean] =
    F.liftF(SupportsUnionAll)

  /**
   * @group Constructors (Primitives)
   */
  def unwrap[T](a: Class[T]): DatabaseMetaDataIO[T] =
    F.liftF(Unwrap(a))

  /**
   * @group Constructors (Primitives)
   */
  def updatesAreDetected(a: Int): DatabaseMetaDataIO[Boolean] =
    F.liftF(UpdatesAreDetected(a))

  /**
   * @group Constructors (Primitives)
   */
  val usesLocalFilePerTable: DatabaseMetaDataIO[Boolean] =
    F.liftF(UsesLocalFilePerTable)

  /**
   * @group Constructors (Primitives)
   */
  val usesLocalFiles: DatabaseMetaDataIO[Boolean] =
    F.liftF(UsesLocalFiles)

 /**
  * Natural transformation from `DatabaseMetaDataOp` to `Kleisli` for the given `M`, consuming a `java.sql.DatabaseMetaData`.
  * @group Algebra
  */
#+scalaz
  def interpK[M[_]: Monad: Catchable: Capture]: DatabaseMetaDataOp ~> Kleisli[M, DatabaseMetaData, ?] =
   DatabaseMetaDataOp.DatabaseMetaDataKleisliTrans.interpK
#-scalaz
#+fs2
  def interpK[M[_]: Catchable: Suspendable]: DatabaseMetaDataOp ~> Kleisli[M, DatabaseMetaData, ?] =
   DatabaseMetaDataOp.DatabaseMetaDataKleisliTrans.interpK
#-fs2

 /**
  * Natural transformation from `DatabaseMetaDataIO` to `Kleisli` for the given `M`, consuming a `java.sql.DatabaseMetaData`.
  * @group Algebra
  */
#+scalaz
  def transK[M[_]: Monad: Catchable: Capture]: DatabaseMetaDataIO ~> Kleisli[M, DatabaseMetaData, ?] =
   DatabaseMetaDataOp.DatabaseMetaDataKleisliTrans.transK
#-scalaz
#+fs2
  def transK[M[_]: Catchable: Suspendable]: DatabaseMetaDataIO ~> Kleisli[M, DatabaseMetaData, ?] =
   DatabaseMetaDataOp.DatabaseMetaDataKleisliTrans.transK
#-fs2

 /**
  * Natural transformation from `DatabaseMetaDataIO` to `M`, given a `java.sql.DatabaseMetaData`.
  * @group Algebra
  */
#+scalaz
 def trans[M[_]: Monad: Catchable: Capture](c: DatabaseMetaData): DatabaseMetaDataIO ~> M =
#-scalaz
#+fs2
 def trans[M[_]: Catchable: Suspendable](c: DatabaseMetaData): DatabaseMetaDataIO ~> M =
#-fs2
   DatabaseMetaDataOp.DatabaseMetaDataKleisliTrans.trans[M](c)

  /**
   * Syntax for `DatabaseMetaDataIO`.
   * @group Algebra
   */
  implicit class DatabaseMetaDataIOOps[A](ma: DatabaseMetaDataIO[A]) {
#+scalaz
    def transK[M[_]: Monad: Catchable: Capture]: Kleisli[M, DatabaseMetaData, A] =
#-scalaz
#+fs2
    def transK[M[_]: Catchable: Suspendable]: Kleisli[M, DatabaseMetaData, A] =
#-fs2
      DatabaseMetaDataOp.DatabaseMetaDataKleisliTrans.transK[M].apply(ma)
  }

}

private[free] trait DatabaseMetaDataIOInstances {
#+fs2
  /**
   * Suspendable instance for [[DatabaseMetaDataIO]].
   * @group Typeclass Instances
   */
  implicit val SuspendableDatabaseMetaDataIO: Suspendable[DatabaseMetaDataIO] =
    new Suspendable[DatabaseMetaDataIO] {
      def pure[A](a: A): DatabaseMetaDataIO[A] = databasemetadata.delay(a)
      override def map[A, B](fa: DatabaseMetaDataIO[A])(f: A => B): DatabaseMetaDataIO[B] = fa.map(f)
      def flatMap[A, B](fa: DatabaseMetaDataIO[A])(f: A => DatabaseMetaDataIO[B]): DatabaseMetaDataIO[B] = fa.flatMap(f)
      def suspend[A](fa: => DatabaseMetaDataIO[A]): DatabaseMetaDataIO[A] = F.suspend(fa)
      override def delay[A](a: => A): DatabaseMetaDataIO[A] = databasemetadata.delay(a)
    }
#-fs2
}

