package com.apurebase.kgraphql.schema.execution

import com.apurebase.deferredJson.deferredJsonBuilder
import com.apurebase.kgraphql.*
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.execution.FlowExecutor.ExecutionContext
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.FunctionWrapper.ArityZero
import com.apurebase.kgraphql.schema.model.QueryDef
import com.apurebase.kgraphql.schema.model.TypeDef
import com.apurebase.kgraphql.schema.scalar.serializeScalar
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import com.apurebase.kgraphql.toMapAsync
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import nidomiro.kdataloader.DataLoader
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1


@Suppress("UNCHECKED_CAST") // For valid structure there is no risk of ClassCastException
class ParallelRequestExecutor(val schema: DefaultSchema) : RequestExecutor {

    inner class ExecutionContext(
        val variables: Variables,
        val requestContext: Context,
    )

    private val argumentsHandler = ArgumentsHandler(schema)

    private val jsonNodeFactory = JsonNodeFactory.instance

    private val dispatcher = schema.configuration.coroutineDispatcher


    private val objectWriter = schema.configuration.objectMapper.writer().let {
        if (schema.configuration.useDefaultPrettyPrinter) {
            it.withDefaultPrettyPrinter()
        } else {
            it
        }
    }

    private val objectWriterFlow = schema.configuration.objectMapper.writer()

    override suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context): String =
        coroutineScope {
            val root = jsonNodeFactory.objectNode()
            val data = root.putObject("data")

            val exceptions = mutableListOf<GraphQLError>()

            val resultMap = plan.toMapAsync(dispatcher) { it: Execution.Node ->
                val ctx = ExecutionContext(Variables(schema, variables, it.variables), context)
                if (determineInclude(ctx, it)) writeOperation(
                    isSubscription = plan.isSubscription,
                    ctx = ctx,
                    node = it,
                    operation = it.field as Field.Function<*, *>,
                    exceptions,
                ) else null
            }

            for (operation in plan) {
                if (resultMap[operation] != null) { // Remove all by skip/include directives
                    data.set<JsonNode>(operation.aliasOrKey, resultMap[operation])
                }
            }

            if (exceptions.isNotEmpty()) {
                root.apply {
                    putArray("errors").apply {
                        exceptions.forEach { e ->
                            addObject().apply {
                                put("message", e.message)
                                putArray("locations").apply {
                                    e.locations?.forEach {
                                        addObject().apply {
                                            put("line", it.line)
                                            put("column", it.column)
                                        }
                                    }
                                }
                                putArray("path").apply {
                                    // TODO: Build this path. https://spec.graphql.org/June2018/#example-90475
                                }
                                if (!e.extensions.isNullOrEmpty()) {
                                    putObject("extensions").apply {
                                        e.extensions.forEach { put(it.key, it.value) }
                                    }
                                }
                            }
                        }
                    }
                }

            }

            objectWriter.writeValueAsString(root)
        }


    suspend fun suspendExecuteFlow(plan: ExecutionPlan, variables: VariablesJson, context: Context): Flow<String> {
        //subscribe to all flows
        val flowList: ArrayList<Flow<Pair<Execution.Node, *>>> = arrayListOf()

        // collect all flows
        plan.forEach { node ->
            val ctx = ExecutionContext(Variables(schema, variables, node.variables), context)
            if (determineInclude(ctx, node)) {
                getFlowOperation(
                    ctx = ctx,
                    node = node,
                    operation = node.field as Field.Function<*, Flow<*>>,
                )?.let { flow ->
                    flowList.add(flow.map { node to it })
                }
            }
        }

        return combine(flowList) { combined: Array<Pair<Execution.Node, *>> ->
            coroutineScope {
                val root = jsonNodeFactory.objectNode()
                val data = root.putObject("data")

                val exceptions = mutableListOf<GraphQLError>()

                for (it in combined) {
                    val (node, res) = it

                    val ctx = ExecutionContext(Variables(schema, variables, node.variables), context)
                    val result = if (determineInclude(ctx, node)) writeFlowResult(
                        ctx = ctx,
                        node = node,
                        result = res,
                        accessProperties = (node.field as Field.Function<*, *>).checkAccess?.access(
                            ctx.requestContext,
                            res
                        ),
                        exceptions = exceptions,
                    ) else null

                    if (result != null) { // Remove all by skip/include directives
                        data.set<JsonNode>(node.aliasOrKey, result)
                    }
                }
                if (exceptions.isNotEmpty()) {
                    root.apply {
                        putArray("errors").apply {
                            exceptions.forEach { e ->
                                addObject().apply {
                                    put("message", e.message)
                                    putArray("locations").apply {
                                        e.locations?.forEach {
                                            addObject().apply {
                                                put("line", it.line)
                                                put("column", it.column)
                                            }
                                        }
                                    }
                                    putArray("path").apply {
                                        // TODO: Build this path. https://spec.graphql.org/June2018/#example-90475
                                    }
                                    if (!e.extensions.isNullOrEmpty()) {
                                        putObject("extensions").apply {
                                            e.extensions.forEach { put(it.key, it.value) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }

                objectWriterFlow.writeValueAsString(root)
            }
        }
    }

    private suspend fun <T> writeOperation(
        isSubscription: Boolean,
        ctx: ExecutionContext,
        node: Execution.Node,
        operation: FunctionWrapper<T>,
        exceptions: MutableList<GraphQLError>,
    ): JsonNode {
        node.field.checkAccess(null, ctx.requestContext)
        val operationResult: T? = operation.invoke(
            isSubscription = isSubscription,
            children = node.children,
            funName = node.field.name,
            receiver = null,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        val accessProperties: Map<KProperty<*>, GraphQlErrorInfo?>? =
            if (operationResult != null) operation.checkAccess?.access(
                ctx.requestContext,
                operationResult
            ) else null



        return createNode(ctx, operationResult, node, node.field.returnType, exceptions, accessProperties)
    }

    private suspend fun <T> writeFlowResult(
        ctx: ExecutionContext,
        node: Execution.Node,
        result: T?,
        accessProperties: Map<KProperty<*>, GraphQlErrorInfo?>?,
        exceptions: MutableList<GraphQLError>,
    ): JsonNode {
        node.field.checkAccess(null, ctx.requestContext)
        val operationResult: T? = result
        return createNode(ctx, operationResult, node, node.field.returnType, exceptions, accessProperties)
    }

    private suspend fun <T> getFlowOperation(
        ctx: ExecutionContext,
        node: Execution.Node,
        operation: FunctionWrapper<Flow<T>>,
    ): Flow<T>? {
        node.field.checkAccess(null, ctx.requestContext)
        val operationResult: Flow<T>? = operation.invoke(
            isSubscription = false,
            children = node.children,
            funName = node.field.name,
            receiver = null,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        return operationResult
    }

    private suspend fun <T> createUnionOperationNode(
        ctx: ExecutionContext,
        parent: T,
        node: Execution.Union,
        unionProperty: Field.Union<T>,
        exceptions: MutableList<GraphQLError>,
    ): JsonNode {
        node.field.checkAccess(parent, ctx.requestContext)

        val operationResult: Any? = unionProperty.invoke(
            funName = unionProperty.name,
            receiver = parent,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        val returnType = unionProperty.returnType.possibleTypes.find { it.isInstance(operationResult) }

        if (returnType == null && !unionProperty.nullable) {
            val expectedOneOf = unionProperty.type.possibleTypes!!.joinToString { it.name.toString() }
            throw ExecutionException(
                "Unexpected type of union property value, expected one of: [$expectedOneOf]." +
                        " value was $operationResult", node
            )
        }

        return createNode(ctx, operationResult, node, returnType ?: unionProperty.returnType, exceptions)
    }

    private suspend fun <T> createNode(
        ctx: ExecutionContext,
        value: T?,
        node: Execution.Node,
        returnType: Type,
        exceptions: MutableList<GraphQLError>,
        accessProperties: Map<KProperty<*>, GraphQlErrorInfo?>? = null,
    ): JsonNode {
        if (value == null) {
            return createNullNode(node, returnType)
        }
        val unboxed = schema.configuration.genericTypeResolver.unbox(value)
        if (unboxed !== value) {
            return createNode(ctx, unboxed, node, returnType, exceptions, accessProperties)
        }

        return when {
            //check value, not returnType, because this method can be invoked with element value
            value is Collection<*> || value is Array<*> -> {
                val values: Collection<*> = when (value) {
                    is Array<*> -> value.toList()
                    else -> value as Collection<*>
                }
                if (returnType.isList()) {
                    val valuesMap = values.toMapAsync(dispatcher) {
                        createNode(ctx, it, node, returnType.unwrapList(), exceptions, accessProperties)
                    }
                    values.fold(jsonNodeFactory.arrayNode(values.size)) { array, v ->
                        array.add(valuesMap[v])
                    }
                } else {
                    throw ExecutionException("Invalid collection value for non collection property", node)
                }
            }

            value is String -> jsonNodeFactory.textNode(value)
            value is Int -> jsonNodeFactory.numberNode(value)
            value is Float -> jsonNodeFactory.numberNode(value)
            value is Double -> jsonNodeFactory.numberNode(value)
            value is Boolean -> jsonNodeFactory.booleanNode(value)
            value is Long -> jsonNodeFactory.numberNode(value)
            //big decimal etc?

            node.children.isNotEmpty() -> {
                createObjectNode(ctx, value, node, returnType, exceptions, accessProperties)
            }

            node is Execution.Union -> {
                createObjectNode(ctx, value, node.memberExecution(returnType), returnType, exceptions)
            }

            else -> createSimpleValueNode(returnType, value, node)
        }
    }

    private fun <T> createSimpleValueNode(returnType: Type, value: T, node: Execution.Node): JsonNode {
        return when (val unwrapped = returnType.unwrapped()) {
            is Type.Scalar<*> -> {
                serializeScalar(jsonNodeFactory, unwrapped, value, node)
            }

            is Type.Enum<*> -> {
                jsonNodeFactory.textNode(value.toString())
            }

            is TypeDef.Object<*> -> throw ExecutionException(
                "Cannot handle object return type, schema structure exception",
                node
            )

            else -> throw ExecutionException("Invalid Type:  ${returnType.name}", node)
        }
    }

    private fun createNullNode(node: Execution.Node, returnType: Type): NullNode {
        if (returnType !is Type.NonNull) {
            return jsonNodeFactory.nullNode()
        } else {
            throw ExecutionException("null result for non-nullable operation ${node.field}", node)
        }
    }

    private suspend fun <T> createObjectNode(
        ctx: ExecutionContext,
        value: T,
        node: Execution.Node,
        type: Type,
        exceptions: MutableList<GraphQLError>,
        accessProperties: Map<KProperty<*>, GraphQlErrorInfo?>? = null,
    ): ObjectNode {
        val objectNode = jsonNodeFactory.objectNode()
        for (child in node.children) {
            when (child) {
                is Execution.Fragment -> objectNode.setAll<JsonNode>(handleFragment(ctx, value, child, exceptions))
                else -> {
                    val (key, jsonNode) = handleProperty(
                        ctx,
                        value,
                        child,
                        type,
                        node.children.size,
                        exceptions,
                        accessProperties
                    )
                    objectNode.merge(key, jsonNode)
                }
            }
        }
        return objectNode
    }

    private suspend fun <T> handleProperty(
        ctx: ExecutionContext,
        value: T,
        child: Execution,
        type: Type,
        childrenSize: Int,
        exceptions: MutableList<GraphQLError>,
        accessProperties: Map<KProperty<*>, GraphQlErrorInfo?>? = null,
    ): Pair<String, JsonNode?> {
        when (child) {
            //Union is subclass of Node so check it first
            is Execution.Union -> {
                val field = type.unwrapped()[child.key]
                    ?: throw IllegalStateException("Execution unit ${child.key} is not contained by operation return type")
                if (field is Field.Union<*>) {
                    return child.aliasOrKey to createUnionOperationNode(
                        ctx,
                        value,
                        child,
                        field as Field.Union<T>,
                        exceptions
                    )
                } else {
                    throw ExecutionException("Unexpected non-union field for union execution node", child)
                }
            }

            is Execution.Node -> {
                val field = type.unwrapped()[child.key]
                    ?: throw IllegalStateException("Execution unit ${child.key} is not contained by operation return type")
                return child.aliasOrKey to createPropertyNode(
                    ctx,
                    value,
                    child,
                    field,
                    childrenSize,
                    exceptions,
                    accessProperties
                )
            }

            else -> {
                throw UnsupportedOperationException("Handling containers is not implemented yet")
            }
        }
    }

    private suspend fun <T> handleFragment(
        ctx: ExecutionContext,
        value: T,
        container: Execution.Fragment,
        exceptions: MutableList<GraphQLError>,
    ): Map<String, JsonNode?> {
        val expectedType = container.condition.type
        val include = determineInclude(ctx, container)

        if (include) {
            if (expectedType.kind == TypeKind.OBJECT || expectedType.kind == TypeKind.INTERFACE) {
                if (expectedType.isInstance(value)) {
                    return container.elements.flatMap { child ->
                        when (child) {
                            is Execution.Fragment -> handleFragment(ctx, value, child, exceptions).toList()
                            // TODO: Should not be 1
                            else -> listOf(handleProperty(ctx, value, child, expectedType, 1, exceptions))
                        }
                    }.fold(mutableMapOf()) { map, entry -> map.merge(entry.first, entry.second) }
                }
            } else if (expectedType.kind == TypeKind.UNION) return handleFragment(
                ctx,
                value,
                container.elements.first { expectedType.name == expectedType.name } as Execution.Fragment,
                exceptions
            ) else {
                throw IllegalStateException("fragments can be specified on object types, interfaces, and unions")
            }
        }
        //not included, or type condition is not matched
        return emptyMap()
    }

    private suspend fun <T> createPropertyNode(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field,
        parentTimes: Int,
        exceptions: MutableList<GraphQLError>,
        accessProperties: Map<KProperty<*>, GraphQlErrorInfo?>? = null,
    ): JsonNode? {
        val include = determineInclude(ctx, node)
        node.field.checkAccess(parentValue, ctx.requestContext)

        if (include) {
            when (field) {
                is Field.Kotlin<*, *> -> {
                    val rawValue = try {
                        (field.kProperty as KProperty1<T, *>).get(parentValue)
                    } catch (e: IllegalArgumentException) {
                        throw ExecutionException(
                            "Couldn't retrieve '${field.kProperty.name}' from class ${parentValue}}",
                            node,
                            e
                        )
                    }
                    val value: Any? = field.transformation?.invoke(
                        funName = field.name,
                        receiver = rawValue,
                        inputValues = field.arguments,
                        args = node.arguments,
                        executionNode = node,
                        ctx = ctx
                    ) ?: rawValue


                    val accessDeniedException = accessProperties?.get(field.kProperty)
                    if (accessDeniedException == null) {
                        return createNode(ctx, value, node, field.returnType, exceptions, accessProperties)
                    } else {
                        exceptions += ExecutionException(
                            accessDeniedException.message ?: "Access to property '${node.key}' is denied",
                            node,
                            extensions = accessDeniedException.extensions
                        )
                        return createNullNode(node, field.returnType)
                    }
                }

                is Field.Function<*, *> -> {
                    return handleFunctionProperty(ctx, parentValue, node, field, exceptions)
                }

                is Field.DataLoader<*, *, *> -> {
                    return handleDataProperty(ctx, parentValue, node, field, exceptions)
                }

                else -> {
                    throw Exception("Unexpected field type: $field, should be Field.Kotlin or Field.Function")
                }
            }
        } else {
            return null
        }
    }

    private suspend fun <T> handleDataProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.DataLoader<*, *, *>,
        exceptions: MutableList<GraphQLError>,
    ): JsonNode {
        val preparedValue = field.kql.prepare.invoke(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        // as this isn't the DataLoaderPreparedRequestExecutor. We'll use this instant workaround instead.
        val loader = field.loader.constructNew(null) as DataLoader<Any?, Any?>
        val value = loader.loadAsync(preparedValue)
        loader.dispatch()

        return createNode(ctx, value.await(), node, field.returnType, exceptions)
    }

    private suspend fun <T> handleFunctionProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.Function<*, *>,
        exceptions: MutableList<GraphQLError>,
    ): JsonNode {
        val result = field.invoke(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )
        return createNode(ctx, result, node, field.returnType, exceptions)
    }

    private suspend fun determineInclude(ctx: ExecutionContext, executionNode: Execution): Boolean {
        if (executionNode.directives?.isEmpty() == true) return true
        return executionNode.directives?.map { (directive, arguments) ->
            directive.execution.invoke(
                funName = directive.name,
                inputValues = directive.arguments,
                receiver = null,
                args = arguments,
                executionNode = executionNode,
                ctx = ctx
            )?.include
                ?: throw ExecutionException("Illegal directive implementation returning null result", executionNode)
        }?.reduce { acc, b -> acc && b } ?: true
    }

    internal suspend fun <T> FunctionWrapper<T>.invoke(
        isSubscription: Boolean = false,
        children: Collection<Execution> = emptyList(),
        funName: String,
        receiver: Any?,
        inputValues: List<InputValue<*>>,
        args: ArgumentNodes?,
        executionNode: Execution,
        ctx: ExecutionContext,
    ): T? {
        val transformedArgs = argumentsHandler.transformArguments(
            funName,
            inputValues,
            args,
            ctx.variables,
            executionNode,
            ctx.requestContext
        )
        //exceptions are not caught on purpose to pass up business logic errors
        return try {
            when {
                hasReceiver -> invoke(receiver, *transformedArgs.toTypedArray())
                isSubscription -> {
                    val subscriptionArgs = children.map { (it as Execution.Node).aliasOrKey }
                    invoke(transformedArgs, subscriptionArgs, objectWriter)
                }

                else -> invoke(*transformedArgs.toTypedArray())
            }
        } catch (e: Throwable) {
            if (schema.configuration.wrapErrors && e !is GraphQLError) {
                throw GraphQLError(e.message ?: "", nodes = listOf(executionNode.selectionNode), originalError = e)
            } else throw e
        }
    }

}
