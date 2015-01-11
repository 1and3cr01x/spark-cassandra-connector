package com.datastax.driver.scala.core.io

import java.util.Date

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import org.joda.time.{DateTime, Duration => JodaDuration}

sealed trait WriteOption[+T]

sealed trait TTLOption extends WriteOption[Int]

sealed trait TimestampOption extends WriteOption[Long]

case class StaticWriteOption[T](value: T) extends WriteOption[T]

case class PerRowWriteOption[T](placeholder: String) extends WriteOption[T]

object TTLOption {

  case object auto extends TTLOption

  def forever: TTLOption = new StaticWriteOption[Int](0) with TTLOption

  def constant(ttl: Int): TTLOption = {
    require(ttl > 0, "Explicitly specified TTL must be greater than zero.")
    new StaticWriteOption[Int](ttl) with TTLOption
  }

  def constant(ttl: JodaDuration): TTLOption = constant(ttl.getStandardSeconds.toInt)

  def constant(ttl: Duration): TTLOption = if (ttl.isFinite()) constant(ttl.toSeconds.toInt) else forever

  def perRow(placeholder: String): TTLOption =
    new PerRowWriteOption[Int](placeholder) with TTLOption

}

object TimestampOption {

  case object auto extends TimestampOption

  def constant(microseconds: Long): TimestampOption = {
    require(microseconds > 0, "Explicitly specified time must be greater than zero.")
    new StaticWriteOption[Long](microseconds) with TimestampOption
  }

  def constant(timestamp: Date): TimestampOption = constant(timestamp.getTime * 1000L)

  def constant(timestamp: DateTime): TimestampOption = constant(timestamp.getMillis * 1000L)

  def perRow(placeholder: String): TimestampOption =
    new PerRowWriteOption[Long](placeholder) with TimestampOption
}