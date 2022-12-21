package es.unizar.urlshortener.core

class InvalidUrlException(val url: String) : Exception("[$url] does not follow a supported schema")

class RedirectionNotFound(val key: String) : Exception("[$key] is not known")
class UrlNotReachable(val url: String) : Exception(" Url: [$url] not reachable")
class NoLeftRedirections(val url: String) : Exception(" Url: [$url] too many redirections, try later")
class UrlAlreadyExists(val url: String) : Exception(" Url: [$url] already exists")