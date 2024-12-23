package com.apurebase.kgraphql

import com.fasterxml.jackson.databind.JsonNode


data class GraphqlRequest(
    val operationName: String? = null,
    val variables: JsonNode? = null,
    val query: String
)
