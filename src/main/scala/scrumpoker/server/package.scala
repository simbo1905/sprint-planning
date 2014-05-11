package scrumpoker

import java.io.File
import scala.concurrent.duration.DurationInt
import scala.util.control.Exception.catching
import scrumpoker.game.PollResponse
import org.mashupbots.socko.handlers.StaticContentHandlerConfig
import akka.util.Timeout
import java.io.PrintWriter
import org.mashupbots.socko.events.HttpRequestEvent
import org.mashupbots.socko.handlers.StaticFileRequest

package object server {
  implicit class StringImprovements(val s: String) {
    import scala.util.control.Exception._
    def toLongOpt = catching(classOf[NumberFormatException]) opt s.toLong
  }

  implicit class PollingResponseImprovement(val r: PollResponse) {
    def toJson = "[" + r.jsons.mkString(",") + "]"
  }

  val actorConfig = """
	static-pinned-dispatcher {
	  type=PinnedDispatcher
	  executor=thread-pool-executor
	}
	akka {
    loggers = ["akka.event.slf4j.Slf4jLogger"]
    loglevel = "INFO"
	  actor {
	    deployment {
	      /static-file-router {
	        router = round-robin
	        nr-of-instances = 25
	      }
	    }
	  }
	}
  """

  val tempFile = File.createTempFile("sprint-planning", ".js")
  val contentPath = scala.util.Properties.envOrElse("SP_HTML_CONTENT_PATH", "src/main/resources")
  val contentDir = new File(contentPath);
  val staticContentHandlerConfig = StaticContentHandlerConfig(
    rootFilePaths = Seq(contentDir.getAbsolutePath, tempFile.getParentFile.getAbsolutePath))

  def fileInFolderRequest(folderName: String, fileName: String)(implicit httpRequestEvent: HttpRequestEvent) = {
    new StaticFileRequest(httpRequestEvent, new File(new File(contentDir, folderName), fileName))
  }

  implicit val timeout = Timeout(1 seconds)

  def errorJson(t: Throwable) = {
    "{\"error\":\"" + t.toString() + "\"}"
  }

  def tsJson() = {
    "{\"mType\":\"ts\",  \"ts\":\"" + new java.util.Date().toString + "\"}"
  }

  def closeJson() = {
    "{\"mType\":\"close\",  \"ts\":\"" + new java.util.Date().toString + "\"}"
  }

  /**
   * The browser needs to know about any alternate ports which the server may have been told to use.
   * Here we write out a temporary script file which the browser will load to process the port info.
   * We use file to take advantages of gzip and 304 behaviour of the socko StaticContentHandler.
   */
  def createProcessInfoTempFile(websocketPort: Int, fallbackPort: Int) = {
    val content = s"var websocketPort = ${websocketPort}; var fallbackPort = ${fallbackPort}; // should cache\n"
    tempFile.deleteOnExit()
    Some(new PrintWriter(tempFile)).foreach { p => p.write(content); p.close }
    tempFile
  }
}