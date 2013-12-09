package in.bharathwrites.dao

import java.sql._
import scala.Some
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.driver.PostgresDriver.simple.Database.threadLocalSession
import slick.jdbc.meta.MTable
import in.bharathwrites.config.Configuration
import in.bharathwrites.domain._
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.event.LoggingReceive
import akka.event.slf4j.SLF4JLogging

object BlogDAO {
  trait DataAccessRequest
  case class Get(id: Long) extends DataAccessRequest
  case class Search(params: BlogSearchParameters) extends DataAccessRequest

  trait DataAccessResponse
  case class GetBlog(blog: Either[Failure, Blog]) extends DataAccessResponse

  def props() = Props(classOf[BlogDAO])
}

class BlogDAO extends Actor with Configuration with SLF4JLogging {
  import BlogDAO._

  def actorRefFactory = context

  def receive = normal

  val normal: Receive = LoggingReceive {
    case Get(id: Long) => {
      log.debug("Retrieving blog with id %d".format(id))
      sender ! GetBlog(get(id))
    }

    case Search(params: BlogSearchParameters) => {
    }
    
  }

  // init Database instance
  private val db = Database.forURL(url = "jdbc:postgresql://%s:%d/%s".format(dbHost, dbPort, dbName),
    user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")

  // create tables if not exist
  db.withSession {
    if (MTable.getTables(blogsTableName).list().isEmpty) {
      Blogs.ddl.create
    }
  }

  def get(id: Long): Either[Failure, Blog] = {
    try {
      db.withSession {
        Blogs.findById(id).firstOption match {
          case Some(blog: Blog) =>
            Right(blog)
          case _ =>
            Left(notFoundError(id))
        }
      }
    } catch {
      case e: SQLException =>
        Left(databaseError(e))
    }
  }

  def search(params: BlogSearchParameters): Either[Failure, List[Blog]] = {
    implicit val typeMapper = Blogs.dateTypeMapper

    try {
      db.withSession {
        val query = for {
          blog <- Blogs if {
            Seq(
              params.title.map(blog.title is _),
              params.date.map(blog.date is _)).flatten match {
                case Nil => ConstColumn.TRUE
                case seq => seq.reduce(_ && _)
              }
          }
        } yield blog

        Right(query.run.toList)
      }
    } catch {
      case e: SQLException =>
        Left(databaseError(e))
    }
  }

  protected def databaseError(e: SQLException) =
    Failure("%d: %s".format(e.getErrorCode, e.getMessage), FailureType.DatabaseFailure)

  protected def notFoundError(blogId: Long) =
    Failure("Blog with id=%d does not exist".format(blogId), FailureType.NotFound)

  /*def create(blog: Blog): Either[Failure, Blog] = {
    try {
      val id = db.withSession {
        Blogs returning Blogs.id insert blog
      }
      Right(blog.copy(id = Some(id)))
    } catch {
      case e: SQLException =>
        Left(databaseError(e))
    }
  }

  def update(id: Long, blog: Blog): Either[Failure, Blog] = {
    try
      db.withSession {
        Blogs.where(_.id === id) update blog.copy(id = Some(id)) match {
          case 0 => Left(notFoundError(id))
          case _ => Right(blog.copy(id = Some(id)))
        }
      }
    catch {
      case e: SQLException =>
        Left(databaseError(e))
    }
  }

  def delete(id: Long): Either[Failure, Blog] = {
    try {
      db.withTransaction {
        val query = Blogs.where(_.id === id)
        val blogs = query.run.asInstanceOf[List[Blog]]
        blogs.size match {
          case 0 =>
            Left(notFoundError(id))
          case _ => {
            query.delete
            Right(blogs.head)
          }
        }
      }
    } catch {
      case e: SQLException =>
        Left(databaseError(e))
    }
  }*/

}