package com.github.pgutkowski.kgraphql

import com.github.pgutkowski.kgraphql.schema.Schema
import com.github.pgutkowski.kgraphql.schema.SchemaBuilder
import com.github.pgutkowski.kgraphql.server.NettyServer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure


class KGraphQL {
    companion object {
        fun newSchema() : SchemaBuilder {
            return SchemaBuilder()
        }

        fun setupServer(schema: Schema) = NettyServer.run(schema)
    }
}

fun <T : Any> KClass<T>.typeName() = this.simpleName!!

fun KType.typeName() = this.jvmErasure.typeName()