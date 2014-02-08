package scrumpoker.server

import org.mashupbots.socko.infrastructure.Logger

/**
 * Twitter's algorithm for generating unique tweet Ids which approximately sort on time across a cluster where the locks may be out of sync
 *
 * Use the system properties CLUSTER_WORKER_ID and CLUSTER_DATACENTRE_ID to configure the jvm to generate unique values in the cluster
 *
 * Taken from https://github.com/twitter/snowflake/ which is licensed under the Apache License Version 2.0
 */
trait SnowflakeIds extends Logger {
  private[this] var lastTimestamp = -1L
  private[this] var sequence: Long = 0L
  private[this] val twepoch = 1288834974657L
  private[this] val workerIdBits = 5L
  private[this] val datacenterIdBits = 5L
  val maxWorkerId = -1L ^ (-1L << workerIdBits)
  val maxDatacenterId = -1L ^ (-1L << datacenterIdBits)
  private[this] val sequenceBits = 12L

  private[this] val workerIdShift = sequenceBits
  private[this] val datacenterIdShift = sequenceBits + workerIdBits
  private[this] val timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits
  private[this] val sequenceMask = -1L ^ (-1L << sequenceBits)

  private[this] val workerId = scala.util.Properties.envOrElse("CLUSTER_WORKER_ID", "1").toLong
  private[this] val datacenterId = scala.util.Properties.envOrElse("CLUSTER_DATACENTRE_ID", "1").toLong

  protected def tilNextMillis(lastTimestamp: Long): Long = {
    var timestamp = timeGen()
    while (timestamp <= lastTimestamp) {
      timestamp = timeGen()
    }
    timestamp
  }

  protected def timeGen(): Long = System.currentTimeMillis()

  /**
   * @see https://github.com/twitter/snowflake/blob/master/src/main/scala/com/twitter/service/snowflake/IdWorker.scala
   * @return A unique id based on twitter's tweet id algorithm 'snowflake'
   */
  def nextId(): Long = synchronized {
    var timestamp = timeGen()

    if (timestamp < lastTimestamp) {
      log.error("clock is moving backwards.  Rejecting requests until %d.", lastTimestamp);
      throw new RuntimeException("Clock moved backwards.  Refusing to generate id for %d milliseconds".format(
        lastTimestamp - timestamp))
    }

    if (lastTimestamp == timestamp) {
      sequence = (sequence + 1) & sequenceMask
      if (sequence == 0) {
        timestamp = tilNextMillis(lastTimestamp)
      }
    } else {
      sequence = 0
    }

    lastTimestamp = timestamp
    ((timestamp - twepoch) << timestampLeftShift) |
      (datacenterId << datacenterIdShift) |
      (workerId << workerIdShift) |
      sequence
  }
}
