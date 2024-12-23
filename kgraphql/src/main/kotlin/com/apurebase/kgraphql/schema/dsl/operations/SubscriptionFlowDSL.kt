package com.apurebase.kgraphql.schema.dsl.operations

import com.apurebase.kgraphql.schema.model.SubscriptionFlowDef


class SubscriptionFlowDSL(name: String) : AbstractOperationFlowDSL(name) {
    internal fun toKQLSubscription(): SubscriptionFlowDef<out Any?> {
        val function =
            functionWrapper ?: throw IllegalArgumentException("resolver has to be specified for query [$name]")

        return SubscriptionFlowDef(
            name = name,
            resolver = function,
            description = description,
            isDeprecated = isDeprecated,
            deprecationReason = deprecationReason,
            inputValues = inputValues,
            accessRule = accessRuleBlock
        )
    }
}