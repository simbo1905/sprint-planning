package scrumpoker

import org.mashupbots.socko.handlers.StaticContentHandlerConfig
import java.io.File
import akka.util.Timeout
import scala.concurrent.duration._

package object server {
  implicit class StringImprovements(val s: String) {
    import scala.util.control.Exception._
    def toLongOpt = catching(classOf[NumberFormatException]) opt s.toLong
  }

  val actorConfig = """
	static-pinned-dispatcher {
	  type=PinnedDispatcher
	  executor=thread-pool-executor
	}
	akka {
	  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
      event-handler-startup-timeout = 60s
	  loglevel=DEBUG
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

  val contentPath = scala.util.Properties.envOrElse("SP_HTML_CONTENT_PATH", "src/main/resources")
  val contentDir = new File(contentPath);
  val staticContentHandlerConfig = StaticContentHandlerConfig(
    rootFilePaths = Seq(contentDir.getAbsolutePath))

  implicit val timeout = Timeout(1 seconds)
}