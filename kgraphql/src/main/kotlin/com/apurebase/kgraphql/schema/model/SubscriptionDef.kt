package com.apurebase.kgraphql.schema.model

import com.apurebase.kgraphql.Context
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KType

class SubscriptionDef<R>(
    name: String,
    resolver: FunctionWrapper<R>,
    override val description: String?,
    override val isDeprecated: Boolean,
    override val deprecationReason: String?,
    accessRule: ((Nothing?, Context) -> Exception?)? = null,
    inputValues: List<InputValueDef<*>> = emptyList(),
    explicitReturnType: KType? = null
) : BaseOperationDef<Nothing, R>(name, resolver, inputValues, accessRule, explicitReturnType), DescribedDef

class SubscriptionFlowDef<R>(
    name: String,
    resolver: FunctionWrapper<Flow<R>>,
    override val description: String?,
    override val isDeprecated: Boolean,
    override val deprecationReason: String?,
    accessRule: ((Nothing?, Context) -> Exception?)? = null,
    inputValues: List<InputValueDef<*>> = emptyList(),
    explicitReturnType: KType? = null
) : BaseOperationDef<Nothing, Flow<R>>(name, resolver, inputValues, accessRule, explicitReturnType), DescribedDef
