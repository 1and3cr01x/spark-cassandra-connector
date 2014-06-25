package com.datastax.driver.spark.mapper

import com.datastax.driver.core.Row

/** Transforms a Cassandra Java driver `Row` into something else.
  * We always need a `RowTransformer` because the original row class is not serializable. */
trait RowTransformer[T] extends Serializable {
  def transform(row: Row, columnNames: Array[String]): T

  /** List of columns this row transformer is going to read.
    * Useful to avoid fetching the columns that are not needed. */
  def columnNames: Option[Seq[String]]

  /** The number of columns that need to be fetched from C*. */
  def columnCount: Option[Int]
}

