package com.datastax.driver.spark.writer

import java.io.IOException

import com.datastax.driver.core.{BatchStatement, PreparedStatement}
import com.datastax.driver.spark.connector.{Schema, TableDef, CassandraConnector}

import org.apache.log4j.Logger
import org.apache.spark.TaskContext

import scala.collection._
import scala.reflect.ClassTag

/** Writes RDD data into given Cassandra table.
  * Individual column values are extracted from RDD objects using given [[RowWriter]]
  * Then, data are inserted into Cassandra with batches of CQL INSERT statements.
  * Each RDD partition is processed by a single thread. */
class TableWriter[T] private (
    connector: CassandraConnector,
    tableDef: TableDef,
    rowWriter: RowWriter[T],
    maxBatchSizeInBytes: Int,
    maxBatchSizeInRows: Option[Int],
    parallelismLevel: Int) extends Serializable {

  import com.datastax.driver.spark.writer.TableWriter._


  val keyspaceName = tableDef.keyspaceName
  val tableName = tableDef.tableName
  val columnNames = rowWriter.columnNames

  val queryStr: String = {
    val columnSpec = columnNames.map(quote).mkString(", ")
    val valueSpec = columnNames.map(_ => "?").mkString(", ")
    s"INSERT INTO ${quote(keyspaceName)}.${quote(tableName)} ($columnSpec) VALUES ($valueSpec)"
  }

  private def quote(name: String) =
    "\"" + name + "\""

  private def createBatch(data: Seq[T], stmt: PreparedStatement): BatchStatement = {
    val batchStmt = new BatchStatement(BatchStatement.Type.UNLOGGED)
    for (row <- data)
      batchStmt.add(rowWriter.bind(row, stmt))
    batchStmt
  }

  /** Writes `MeasuredInsertsCount` rows to Cassandra and returns the maximum size of the row */
  private def measureMaxInsertSize(data: Iterator[T], stmt: PreparedStatement, queryExecutor: QueryExecutor): Int = {
    logger.info(s"Writing $MeasuredInsertsCount rows to $keyspaceName.$tableName and measuring maximum serialized row size...")
    var maxInsertSize = 1
    for (row <- data.take(MeasuredInsertsCount)) {
      val insert = rowWriter.bind(row, stmt)
      queryExecutor.executeAsync(insert)
      val size = rowWriter.estimateSizeInBytes(row)
      if (size > maxInsertSize)
        maxInsertSize = size
    }
    logger.info(s"Maximum serialized row size: " + maxInsertSize + " B")
    maxInsertSize
  }

  /** Returns either configured batch size or, if not set, determines the optimal batch size by writing a
    * small number of rows and estimating their size. */
  private def optimumBatchSize(data: Iterator[T], stmt: PreparedStatement, queryExecutor: QueryExecutor): Int = {
    maxBatchSizeInRows match {
      case Some(size) =>
        size
      case None =>
        val maxInsertSize = measureMaxInsertSize(data, stmt, queryExecutor)
        math.max(1, maxBatchSizeInBytes / (maxInsertSize * 2))  // additional margin for data larger than usual
    }
  }

  private def writeBatched(data: Iterator[T], stmt: PreparedStatement, queryExecutor: QueryExecutor, batchSize: Int) {
    for (batch <- data.grouped(batchSize)) {
      val batchStmt = createBatch(batch, stmt)
      queryExecutor.executeAsync(batchStmt)
    }
  }

  private def writeUnbatched(data: Iterator[T], stmt: PreparedStatement, queryExecutor: QueryExecutor) {
    for (row <- data)
      queryExecutor.executeAsync(rowWriter.bind(row, stmt))
  }

  /** Main entry point */
  def write(taskContext: TaskContext, data: Iterator[T]) {
    var rowCount = 0
    val countedData = data.map { item => rowCount += 1; item }


    connector.withSessionDo { session =>
      logger.info(s"Connected to Cassandra cluster ${session.getCluster.getClusterName}")
      val startTime = System.currentTimeMillis()
      val stmt = session.prepare(queryStr)
      val queryExecutor = new QueryExecutor(session, parallelismLevel)
      val batchSize = optimumBatchSize(countedData, stmt, queryExecutor)

      logger.info(s"Writing data partition to $keyspaceName.$tableName in batches of $batchSize rows each.")
      batchSize match {
        case 1 => writeUnbatched(countedData, stmt, queryExecutor)
        case _ => writeBatched(countedData, stmt, queryExecutor, batchSize)
      }

      queryExecutor.waitForCurrentlyExecutingTasks()

      if (queryExecutor.failureCount > 0)
        throw new IOException(s"Failed to write ${queryExecutor.failureCount} batches to $keyspaceName.$tableName.")

      val endTime = System.currentTimeMillis()
      val duration = (endTime - startTime) / 1000.0
      logger.info(f"Successfully wrote $rowCount rows in ${queryExecutor.successCount} batches to $keyspaceName.$tableName in $duration%.3f s.")
    }
  }

}

object TableWriter {

  val logger = Logger.getLogger(classOf[TableWriter[_]])

  val DefaultParallelismLevel = 5
  val MeasuredInsertsCount = 128
  val DefaultBatchSizeInBytes = 64 * 1024

  def apply[T : ClassTag : RowWriterFactory](
      connector: CassandraConnector,
      keyspaceName: String,
      tableName: String,
      columnNames: Option[Seq[String]] = None,
      batchSizeInBytes: Int = DefaultBatchSizeInBytes,
      batchSizeInRows: Option[Int] = None, 
      parallelismLevel: Int = DefaultParallelismLevel) =
  {
    val tableDef = new Schema(connector, Some(keyspaceName), Some(tableName)).tables.head
    val selectedColumns = columnNames.getOrElse(tableDef.allColumns.map(_.columnName).toSeq)
    val rowWriter = implicitly[RowWriterFactory[T]].rowWriter(tableDef, selectedColumns)
    new TableWriter[T](connector, tableDef, rowWriter, batchSizeInBytes, batchSizeInRows, parallelismLevel)
  }
}
