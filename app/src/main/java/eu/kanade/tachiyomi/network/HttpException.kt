package eu.kanade.tachiyomi.network

class HttpException(val code: Int) : Exception("HTTP error $code")
