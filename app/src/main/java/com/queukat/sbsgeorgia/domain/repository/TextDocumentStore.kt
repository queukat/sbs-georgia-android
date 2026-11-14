package com.queukat.sbsgeorgia.domain.repository

/** File I/O is implemented by a platform adapter, never by domain code. */
interface TextDocumentStore {
    suspend fun writeText(uriString: String, content: String)

    suspend fun readText(uriString: String): String
}
