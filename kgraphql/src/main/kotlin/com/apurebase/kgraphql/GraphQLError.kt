package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.model.ast.ASTNode
import com.apurebase.kgraphql.schema.model.ast.Location.Companion.getLocation
import com.apurebase.kgraphql.schema.model.ast.Source
import com.fasterxml.jackson.databind.node.JsonNodeFactory

open class GraphQLError(

    /**
     * A message describing the Error for debugging purposes.
     */
    message: String,

    /**
     * An array of GraphQL AST Nodes corresponding to this error.
     */
    val nodes: List<ASTNode>? = null,

    /**
     * The source GraphQL document for the first location of this error.
     *
     * Note that if this Error represents more than one node, the source may not
     * represent nodes after the first node.
     */
    val source: Source? = null,

    /**
     * An array of character offsets within the source GraphQL document
     * which correspond to this error.
     */
    val positions: List<Int>? = null,

    /**
     * The original error thrown from a field resolver during execution.
     */
    val originalError: Throwable? = null,

    /**
     * GraphQl [error extensions](https://spec.graphql.org/October2021/#sec-Errors)
     */
    val extensions: Map<String, String>? = null,
) : Exception(message) {

    constructor(message: String, node: ASTNode?) : this(message, nodes = node?.let(::listOf))

    /**
     * An array of { line, column } locations within the source GraphQL document
     * that correspond to this error.
     *
     * Errors during validation often contain multiple locations, for example to
     * point out two things with the same name. Errors during execution include a
     * single location, the field that produced the error.
     */
    val locations: List<Source.LocationSource>? by lazy {
        if (positions != null && source != null) {
            positions.map { pos -> getLocation(source, pos) }
        } else nodes?.mapNotNull { node ->
            node.loc?.let { getLocation(it.source, it.start) }
        }
    }

    fun prettyPrint(): String {
        var output = message ?: ""

        if (nodes != null) {
            for (node in nodes) {
                if (node.loc != null) {
                    output += "\n\n" + node.loc!!.printLocation()
                }
            }
        } else if (source != null && locations != null) {
            for (location in locations!!) {
                output += "\n\n" + source.print(location)
            }
        }

        return output
    }
}

data class GraphQlErrorInfo(val message: String? = null, val extensions: Map<String, String>? = null)

fun GraphQLError.serialize(): String {
    val objectNode = JsonNodeFactory.instance.objectNode().apply {
        putArray("errors").apply {
            addObject().apply {
                put("message", message)
                putArray("locations").apply {
                    locations?.forEach {
                        addObject().apply {
                            put("line", it.line)
                            put("column", it.column)
                        }
                    }
                }
                putArray("path").apply {
                    // TODO: Build this path. https://spec.graphql.org/June2018/#example-90475
                }
                if (extensions != null) {
                    putObject("extensions").apply {
                        extensions.forEach {
                            put(it.key, it.value)
                        }
                    }
                }
            }
        }
    }

    return objectNode.toString()
}