@file:Suppress("UNCHECKED_CAST")

package com.apurebase.kgraphql.schema.dsl.operations

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.GraphQlErrorInfo
import com.apurebase.kgraphql.schema.dsl.LimitedAccessItemDSL
import com.apurebase.kgraphql.schema.dsl.ResolverDSL
import com.apurebase.kgraphql.schema.model.*
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.KType


abstract class AbstractOperationDSL(
    val name: String,
) : LimitedAccessItemDSL<Nothing>(),
    ResolverDSL.Target {

    protected val inputValues = mutableListOf<InputValueDef<*>>()

    internal var functionWrapper: FunctionWrapper<*>? = null

    var explicitReturnType: KType? = null

    private var accessPropertiesRule: AccessPropertiesRule<*>? = null

    private fun <T> resolver(function: FunctionWrapper<T>): ResolverDSL {

        try {
            require(function.hasReturnType()) {
                "Resolver for '$name' has no return value"
            }
        } catch (e: Throwable) {
            if ("KotlinReflectionInternalError" !in e.toString()) {
                throw e
            }
        }

        functionWrapper = function.apply {
            if (accessPropertiesRule != null) checkAccess = accessPropertiesRule as AccessPropertiesRule<Any?>
        }
        return ResolverDSL(this)
    }

    fun <T> KFunction<T>.toResolver() = resolver(FunctionWrapper.on(this))

    fun <T> resolver(function: suspend () -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R> resolver(function: suspend (R) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E> resolver(function: suspend (R, E) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W> resolver(function: suspend (R, E, W) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q> resolver(function: suspend (R, E, W, Q) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A> resolver(function: suspend (R, E, W, Q, A) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S> resolver(function: suspend (R, E, W, Q, A, S) -> T) =
        resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B> resolver(function: suspend (R, E, W, Q, A, S, B) -> T) =
        resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B, U> resolver(function: suspend (R, E, W, Q, A, S, B, U) -> T) =
        resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B, U, C> resolver(function: suspend (R, E, W, Q, A, S, B, U, C) -> T) =
        resolver(FunctionWrapper.on(function))

    fun accessRule(rule: (Context) -> Exception?) {
        val accessRuleAdapter: (Nothing?, Context) -> Exception? = { _, ctx -> rule(ctx) }
        this.accessRuleBlock = accessRuleAdapter
    }

    override fun addInputValues(inputValues: Collection<InputValueDef<*>>) {
        this.inputValues.addAll(inputValues)
    }

    override fun setReturnType(type: KType) {
        explicitReturnType = type
    }

    override fun setAccessProperties(accessPropertiesRule: AccessPropertiesRule<Any?>) {
        this.accessPropertiesRule = accessPropertiesRule
    }

    fun <T> accessProperties(f: (context: Context, item: T) -> Map<KProperty<*>, GraphQlErrorInfo?>) {
        this.setAccessProperties(object : AccessPropertiesRule<Any?> {
            override fun access(context: Context, item: Any?): Map<KProperty<*>, GraphQlErrorInfo?>{
                return f(context, item as T)
            }
        })
    }
}

abstract class AbstractOperationFlowDSL(
    val name: String,
) : LimitedAccessItemDSL<Nothing>(), ResolverDSL.Target {

    protected val inputValues = mutableListOf<InputValueDef<*>>()

    internal var functionWrapper: FunctionWrapper<Flow<*>>? = null

    var explicitReturnType: KType? = null

    private var accessPropertiesAbstractFlow: AccessPropertiesRule<Any?>? = null

    private fun resolver(function: FunctionWrapper<Flow<*>>): ResolverDSL {

        try {
            require(function.hasReturnType()) {
                "Resolver for '$name' has no return value"
            }
        } catch (e: Throwable) {
            if ("KotlinReflectionInternalError" !in e.toString()) {
                throw e
            }
        }

        functionWrapper = function.apply {
            this.checkAccess = accessPropertiesAbstractFlow
        }

        return ResolverDSL(this)
    }

//    fun <T> KFunction<T>.toResolver() = resolver(FunctionWrapper.on(this))

    fun <T> resolver(function: suspend () -> Flow<T>) = resolver(FunctionWrapper.on(function))

    fun <T, R> resolver(function: suspend (R) -> Flow<T>) = resolver(FunctionWrapper.on(function))

    fun <T, R, E> resolver(function: suspend (R, E) -> Flow<T>) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W> resolver(function: suspend (R, E, W) -> Flow<T>) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q> resolver(function: suspend (R, E, W, Q) -> Flow<T>) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A> resolver(function: suspend (R, E, W, Q, A) -> Flow<T>) =
        resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S> resolver(function: suspend (R, E, W, Q, A, S) -> Flow<T>) =
        resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B> resolver(function: suspend (R, E, W, Q, A, S, B) -> Flow<T>) =
        resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B, U> resolver(function: suspend (R, E, W, Q, A, S, B, U) -> Flow<T>) =
        resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B, U, C> resolver(function: suspend (R, E, W, Q, A, S, B, U, C) -> Flow<T>) =
        resolver(FunctionWrapper.on(function))

    fun accessRule(rule: (Context) -> Exception?) {
        val accessRuleAdapter: (Nothing?, Context) -> Exception? = { _, ctx -> rule(ctx) }
        this.accessRuleBlock = accessRuleAdapter
    }

    override fun addInputValues(inputValues: Collection<InputValueDef<*>>) {
        this.inputValues.addAll(inputValues)
    }

    override fun setReturnType(type: KType) {
        explicitReturnType = type
    }

    override fun setAccessProperties(accessPropertiesRule: AccessPropertiesRule<Any?>) {
        this.accessPropertiesAbstractFlow = accessPropertiesRule
    }

    fun <T> accessProperties(f: (context: Context, item: T) -> Map<KProperty<*>, GraphQlErrorInfo?>) {
        this.setAccessProperties(object : AccessPropertiesRule<Any?> {
            override fun access(context: Context, item: Any?): Map<KProperty<*>, GraphQlErrorInfo?> {
                return  f(context, item as T)
            }
        })
    }
}
