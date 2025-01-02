package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.dsl.SchemaConfigurationDSL
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.sse.*
import io.ktor.util.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.forEach

private val mapper = jacksonObjectMapper()

class GraphQL(val schema: Schema) {

    class Configuration : SchemaConfigurationDSL() {
        fun schema(block: SchemaBuilder.() -> Unit) {
            schemaBlock = block
        }

        /**
         * This adds support for opening the graphql route within the browser
         */
        var playground: Boolean = false

        var endpoint: String = "/graphql"
        var subscriptionEndpoint: String = "/graphql-subscribe"

        fun context(block: ContextBuilder.(ApplicationCall) -> Unit) {
            contextSetup = block
        }

        fun wrap(block: Route.(next: Route.() -> Unit) -> Unit) {
            wrapWith = block
        }

        internal var contextSetup: (ContextBuilder.(ApplicationCall) -> Unit)? = null
        internal var wrapWith: (Route.(next: Route.() -> Unit) -> Unit)? = null
        internal var schemaBlock: (SchemaBuilder.() -> Unit)? = null

    }


    companion object Feature : Plugin<Application, Configuration, GraphQL> {
        override val key = AttributeKey<GraphQL>("KGraphQL")

        private val rootFeature = FeatureInstance("KGraphQL")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQL {
            return rootFeature.install(pipeline, configure)
        }
    }

    class FeatureInstance(featureKey: String = "KGraphQL") : Plugin<Application, Configuration, GraphQL> {

        override val key = AttributeKey<GraphQL>(featureKey)

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQL {
            val config = Configuration().apply(configure)
            val schema = KGraphQL.schema {
                configuration = config
                config.schemaBlock?.invoke(this)
            }

            val routing: Routing.() -> Unit = {
                val routing: Route.() -> Unit = {
                    route(config.endpoint) {
                        post {
                            val bodyAsText = call.receiveText()
                            val request = mapper.readValue<GraphqlRequest>(bodyAsText)
                            val ctx = context {
                                config.contextSetup?.invoke(this, call)
                            }
                            val result =
                                schema.execute(
                                    request.query,
                                    request.variables.toString(),
                                    ctx,
                                    operationName = request.operationName
                                )
                            call.respondText(result, contentType = ContentType.Application.Json)
                        }
                        if (config.playground) get {
                            @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                            val playgroundHtml =
                                KtorGraphQLConfiguration::class.java.classLoader.getResource("playground.html")
                                    .readBytes()
                            call.respondBytes(playgroundHtml, contentType = ContentType.Text.Html)
                        }
                    }

                    plugin(SSE)
                    ssePost(config.subscriptionEndpoint) {
                        val bodyAsText = call.receiveText()
                        val request = mapper.readValue<GraphqlRequest>(bodyAsText)
                        val ctx = context {
                            config.contextSetup?.invoke(this, call)
                        }

                        val result = try {
                            schema.executeSubscription(
                                request.query,
                                request.variables.toString(),
                                ctx,
                                operationName = request.operationName
                            )
                        } catch (e: GraphQLError) {
                            send(e.serialize())
                            return@ssePost
                        }

                        result.collect {
                            send(ServerSentEvent(it))
                            println("still works $it ")
                        }

                    }
                }

                config.wrapWith?.invoke(this, routing) ?: routing(this)
            }

            pipeline.pluginOrNull(RoutingRoot)?.apply(routing) ?: pipeline.install(RoutingRoot, routing)

            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                try {
                    coroutineScope {
                        proceed()
                    }
                } catch (e: Throwable) {
                    if (e is GraphQLError) {
                        context.respond(HttpStatusCode.OK, e.serialize())
                    } else throw e
                }
            }
            return GraphQL(schema)
        }

        private fun GraphQLError.serialize(): String {
            val objectNode: ObjectNode = JsonNodeFactory.instance.objectNode().apply {
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
                    }
                }
            }

            return objectNode.toString()
        }
    }
}

private fun Route.ssePost(path: String, handler: suspend ServerSSESession.() -> Unit) {
    plugin(SSE)

    route(path, HttpMethod.Post) {
        sse(handler)
    }
}