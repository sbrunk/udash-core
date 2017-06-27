package io.udash.rest.server

import javax.servlet.http.HttpServletRequest

import com.avsystem.commons.rpc.MetadataAnnotation
import io.udash.rest.{UdashRESTFramework, _}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Base trait for anything that exposes REST interface.
  */
abstract class ExposesREST[ServerRPCType : UdashRESTFramework#ValidServerREST](localRest: ServerRPCType) {
  val framework: UdashRESTFramework
  import framework._

  /**
    * This allows the RPC implementation to be wrapped in raw RPC which will translate raw calls coming from network
    * into calls on actual RPC implementation.
    */
  protected def localRpcAsRaw: AsRawRPC[ServerRPCType]

  protected lazy val rawLocalRpc = localRpcAsRaw.asRaw(localRest)

  protected val rpcMetadata: RPCMetadata[ServerRPCType]

  /** Transform `String` from HTTP request header into `RawValue`. */
  def headerArgumentToRaw(raw: String, isStringArg: Boolean): RawValue
  /** Transform `String` from HTTP request query argument into `RawValue`. */
  def queryArgumentToRaw(raw: String, isStringArg: Boolean): RawValue
  /** Transform `String` from URL part into `RawValue`. */
  def urlPartToRaw(raw: String, isStringArg: Boolean): RawValue

  def handleRestCall(getterChain: List[RawInvocation], invocation: RawInvocation)(implicit ec: ExecutionContext): Future[String] = {
    try {
      val receiver = rawLocalRpc.resolveGetterChain(getterChain)
      receiver.call(invocation.rpcName, invocation.argLists).map(rawToString)
    } catch {
      case ex: Exception =>
        Future.failed(ex)
    }
  }

  def parseHttpRequest(req: HttpServletRequest, httpMethod: Class[_ <: RESTMethod]): (List[RawInvocation], RawInvocation) = {
    val invocations = List.newBuilder[RawInvocation]
    val path: Array[String] = Option(req.getPathInfo).map(_.stripPrefix("/").split("/")).getOrElse(Array.empty[String])
    val bodyContent = req.getReader.lines().toArray.mkString("\n")
    lazy val bodyValues = read[Map[String, framework.RawValue]](stringToRaw(bodyContent))(bodyValuesReader)

    def findRestParamName(annotations: Seq[MetadataAnnotation]): Option[String] =
      annotations.find(_.isInstanceOf[RESTParamName]).map(_.asInstanceOf[RESTParamName].restName)

    def parseInvocations(path: Seq[String], metadata: RPCMetadata[_]): Unit = {
      if (path.isEmpty) throw ExposesREST.NotFound(req.getPathInfo)

      val methodName = path.head
      var nextParts = path.tail

      if (!metadata.signatures.contains(methodName))
        throw ExposesREST.NotFound(req.getPathInfo)

      val methodMetadata = metadata.signatures(methodName)

      val args: List[List[RawValue]] = methodMetadata.paramMetadata.map { argsList =>
        argsList.map { arg =>
          val argTypeAnnotations = arg.annotations.filter(_.isInstanceOf[ArgumentType])
          argTypeAnnotations.headOption match {
            case Some(_: Header) =>
              val argName = findRestParamName(arg.annotations).getOrElse(arg.name)
              val headerValue = req.getHeader(argName)
              if (headerValue == null) throw ExposesREST.MissingHeader(argName)
              headerArgumentToRaw(headerValue, arg.typeMetadata == framework.SimplifiedType.StringType)
            case Some(_: Query) =>
              val argName = findRestParamName(arg.annotations).getOrElse(arg.name)
              val param = req.getParameter(argName)
              if (param == null) throw ExposesREST.MissingQueryArgument(argName)
              queryArgumentToRaw(param, arg.typeMetadata == framework.SimplifiedType.StringType)
            case Some(_: URLPart) =>
              if (nextParts.isEmpty) throw ExposesREST.MissingURLPart(arg.name)
              val v = nextParts.head
              nextParts = nextParts.tail
              urlPartToRaw(v, arg.typeMetadata == framework.SimplifiedType.StringType)
            case Some(_: BodyValue) =>
              val argName = findRestParamName(arg.annotations).getOrElse(arg.name)
              if (!bodyValues.contains(argName)) throw ExposesREST.MissingBodyValue(arg.name)
              bodyValues(argName)
            case Some(_: Body) =>
              if (bodyContent.isEmpty) throw ExposesREST.MissingBody(arg.name)
              stringToRaw(bodyContent)
            case _ =>
              throw new RuntimeException(s"Missing `${arg.name}` (REST name: `${findRestParamName(arg.annotations)}`) parameter type annotations! ($argTypeAnnotations)")
          }
        }
      }

      invocations += RawInvocation(methodName, args)

      if (metadata.getterResults.contains(methodName))
        parseInvocations(nextParts, metadata.getterResults(methodName))
      else if (!methodMetadata.annotations.exists(a => a.getClass == httpMethod))
        throw new ExposesREST.MethodNotAllowed()
    }

    parseInvocations(path, rpcMetadata)

    val result = invocations.result().reverse
    (result.tail, result.head)
  }
}

object ExposesREST {
  case class NotFound(path: String) extends RuntimeException(s"Resource `$path` not found.")
  class MethodNotAllowed extends RuntimeException("Method not allowed.")

  abstract class BadRequestException(msg: String) extends RuntimeException(msg)
  case class MissingHeader(name: String) extends BadRequestException(s"Header `$name` not found.")
  case class MissingQueryArgument(name: String) extends BadRequestException(s"Query argument `$name` not found.")
  case class MissingURLPart(name: String) extends BadRequestException(s"URL argument `$name` not found.")
  case class MissingBody(name: String) extends BadRequestException(s"Body argument `$name` not found.")
  case class MissingBodyValue(name: String) extends BadRequestException(s"Body argument value `$name` not found.")
}
