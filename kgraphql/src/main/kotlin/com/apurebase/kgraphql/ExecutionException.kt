package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.model.ast.ASTNode

class ExecutionException(
    message: String,
    node: ASTNode? = null,
    cause: Throwable? = null,
    extensions: Map<String, String>? = null,
) : GraphQLError(
    message,
    nodes = node?.let(::listOf),
    originalError = cause,
    extensions = extensions,
) {
    constructor(
        message: String,
        node: Execution,
        cause: Throwable? = null,
        extensions: Map<String, String>? = null,
    ) : this(message, node.selectionNode, cause, extensions)
}

