package com.geeksville.mavlink

import com.geeksville.akka.InstrumentedActor
import org.mavlink.messages.MAVLinkMessage
import org.mavlink.messages.ardupilotmega._
import LogIncomingMavlink._
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import com.geeksville.logback.Logging
import com.geeksville.util.Throttled

/**
 * Output a mission planner compatible tlog file
 *
 * File format seems to be time in usec as a long (big endian), followed by packet.
 */
class LogBinaryMavlink(out: OutputStream) extends InstrumentedActor {

  val messageThrottle = new Throttled(60 * 1000)
  var oldNumPacket = 0L
  var numPacket = 0L

  private val buf = ByteBuffer.allocate(8)
  buf.order(ByteOrder.BIG_ENDIAN)

  override def postStop() {
    log.info("Closing log file...")
    out.close()
    super.postStop()
  }

  def onReceive = {
    case msg: MAVLinkMessage ⇒
      // def str = "Rcv" + msg.sysId + ": " + msg
      //log.debug("Binary write: " + msg)
      numPacket += 1

      messageThrottle { dt =>
        val numSec = dt / 1000.0

        val mPerSec = (numPacket - oldNumPacket) / numSec
        oldNumPacket = numPacket

        log.info("msg write per sec %s".format(mPerSec))
      }

      // Time in usecs
      val time = System.currentTimeMillis * 1000
      buf.clear()
      buf.putLong(time)
      out.write(buf.array)

      // Payload
      out.write(msg.encode)
  }
}

object LogBinaryMavlink extends Logging {

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")

  /// Allocate a filename in the spooldir
  def getFilename(spoolDir: File = new File("logs")) = {
    if (!spoolDir.exists)
      spoolDir.mkdirs()

    val fname = dateFormat.format(new Date) + ".tlog"
    new File(spoolDir, fname)
  }

  // Create a new log file 
  def create(file: File = getFilename()) = {
    logger.info("Logging to " + file.getAbsolutePath)
    val out = new BufferedOutputStream(new FileOutputStream(file, true), 8192)
    new LogBinaryMavlink(out)
  }
}