package io.udash.rest

import io.udash.rpc.serialization.URLEncoder

trait RESTConverters {
  val framework: UdashRESTFramework

  // for ServerREST

  def rawToHeaderArgument(raw: framework.RawValue): String = stripQuotes(raw)
  def rawToQueryArgument(raw: framework.RawValue): String = stripQuotes(raw)
  def rawToURLPart(raw: framework.RawValue): String = URLEncoder.encode(stripQuotes(raw))

  private def stripQuotes(s: String): String =
    s.stripPrefix("\"").stripSuffix("\"")

  // for ExposesREST

  def headerArgumentToRaw(raw: String, isStringArg: Boolean): framework.RawValue = rawArg(raw, isStringArg)
  def queryArgumentToRaw(raw: String, isStringArg: Boolean): framework.RawValue  = rawArg(raw, isStringArg)
  def urlPartToRaw(raw: String, isStringArg: Boolean): framework.RawValue =
    rawArg(URLEncoder.decode(raw), isStringArg)

  private def rawArg(raw: String, isStringArg: Boolean): framework.RawValue =
    if (isStringArg) s""""$raw""""
    else raw
}