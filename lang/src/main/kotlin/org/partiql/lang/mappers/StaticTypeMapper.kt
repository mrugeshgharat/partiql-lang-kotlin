package org.partiql.lang.mappers

import com.amazon.ionelement.api.AnyElement
import org.partiql.ionschema.model.IonSchemaModel
import org.partiql.ionschema.model.toIsl
import org.partiql.lang.eval.ExprValueType
import org.partiql.lang.types.AnyOfType
import org.partiql.lang.types.AnyType
import org.partiql.lang.types.BagType
import org.partiql.lang.types.DecimalType
import org.partiql.lang.types.IntType
import org.partiql.lang.types.ListType
import org.partiql.lang.types.NumberConstraint
import org.partiql.lang.types.SexpType
import org.partiql.lang.types.StaticType
import org.partiql.lang.types.StringType
import org.partiql.lang.types.StructType
import org.partiql.lang.util.toIntExact
import kotlin.reflect.KClass

internal typealias TypeDefMap = Map<String, IonSchemaModel.TypeDefinition>

// FIXME: Duplicated from StaticType because of - https://github.com/partiql/partiql-lang-kotlin/issues/515
internal fun isOptional(type: StaticType) = type.typeDomain.contains(ExprValueType.MISSING)
internal fun isNullable(type: StaticType) = type.typeDomain.contains(ExprValueType.NULL)
internal fun asOptional(type: StaticType) = when {
    isOptional(type) -> type
    else -> StaticType.unionOf(type, StaticType.MISSING).flatten()
}
internal fun asNullable(type: StaticType) = when {
    isNullable(type) -> type
    else -> StaticType.unionOf(type, StaticType.NULL).flatten()
}

/**
 * This class is responsible for mapping Ion Schema type definition(s) to PartiQL StaticType(s)
 * using an instance of IonSchemaModel.Schema (schema model generated by Ion Schema Library)
 *
 * Assumptions:
 *
 * 1. Name is required for top-level types
 * 2. Name is not allowed within inline types
 * 3. In case of duplicate type names, only the last one in top-to-bottom schema processing order is considered.
 *    Ideally, duplicate type names should throw an error in Ion Schema library.
 *    https://github.com/partiql/partiql-isl-kotlin/issues/5
*/
class StaticTypeMapper(schema: IonSchemaModel.Schema) {

    private val typeDefLookup = object : IonSchemaModel.VisitorFold<TypeDefMap>() {
        // Type definitions with missing name are ignored for now.
        override fun visitTypeDefinition(node: IonSchemaModel.TypeDefinition, accumulator: TypeDefMap): TypeDefMap =
            node.name?.text?.let { accumulator + mapOf(it to node) } ?: accumulator
    }.walkSchema(schema, mapOf())

    /**
     * This method returns a single StaticType corresponding to source ISL top-level type of the name [typeName]
     * StaticType does not contain a "name" attribute. The name information is preserved only within ISL stored inside the `metas` field for StaticType
     */
    fun toStaticType(typeName: String): StaticType {
        val typeDef = typeDefLookup[typeName] ?: throw TypeNotFoundException(typeName)
        return typeDef.toStaticType(null)
    }

    /**
     * This method converts an IonSchemaModel.TypeDefinition to StaticType. There are a few things that can change here:
     *
     * 1. The onus of parsing type constraint and identifying the core type can be moved into Ion Schema Library. The
     *    library can use the core type to return appropriate instances of type definition (e.g. StructType, ListType)
     * 2. Ion Schema library can provide user-friendly APIs that abstract away the complexities of parsing constraints
     *    on a type definition.
     */
    private fun IonSchemaModel.TypeDefinition.toStaticType(previousTopLevelTypeName: String?): StaticType {
        if (previousTopLevelTypeName == this.name?.text) {
            // We're in a type definition that is recursive.
            // Ideally we'd provide the reference to a (by deferring its initialization) value of self
            // However, since we don't have the ability in StaticType to handle deferred values,
            //   we return StaticType.ANY.
            return StaticType.ANY
        }

        val currentTopLevelTypeName = this.name?.text ?: previousTopLevelTypeName

        // Check recursively for other top-level type definitions
        // And add them to metas as a list of ISL type definitions
        val topLevelTypeDefinitions = this.getTopLevelTypes(listOf())
        val metas = mapOf(ISL_META_KEY to (topLevelTypeDefinitions + this).distinct())

        // Find the type constraint
        // If type constraint is not present, check for presence of AnyOf constraint
        // If neither Type nor AnyOf constraint is found, return ANY.
        val typeConstraint = getTypeConstraint() ?: getAnyOfConstraint() ?: return AnyType(metas)

        // Get core ISL type
        val coreType = when (typeConstraint) {
            is IonSchemaModel.Constraint.TypeConstraint -> typeConstraint.type.toStaticType(currentTopLevelTypeName)
            is IonSchemaModel.Constraint.AnyOf -> return AnyOfType(typeConstraint.types.map { it.toStaticType(currentTopLevelTypeName) }.toSet(), metas)
            else -> error("This block should be unreachable")
        }

        // Create StaticType based on core ISL type
        return when (coreType) {
            is StringType -> StringType(getStringLengthConstraint(), metas)
            is IntType -> IntType(getIntRangeConstraint(), metas)
            is DecimalType -> DecimalType(getPrecisionScaleConstraint(), metas)
            is ListType -> ListType(getElement(currentTopLevelTypeName), metas)
            is SexpType -> SexpType(getElement(currentTopLevelTypeName), metas)
            is BagType -> BagType(getElement(currentTopLevelTypeName), metas)
            is StructType -> StructType(getFields(currentTopLevelTypeName), contentClosed = isClosedContent(), metas = metas)
            else -> coreType.withMetas(metas)
        }
    }

    private fun IonSchemaModel.TypeReference.toStaticType(topLevelTypeName: String?): StaticType =
        when (this) {
            is IonSchemaModel.TypeReference.NamedType ->
                when (name.text) {
                    // TODO: Consider replacing raw strings with a kotlin type
                    "bool" -> StaticType.BOOL
                    "int" -> StaticType.INT
                    "float" -> StaticType.FLOAT
                    "decimal" -> StaticType.DECIMAL
                    "timestamp" -> StaticType.TIMESTAMP
                    "symbol" -> StaticType.SYMBOL
                    "string" -> StaticType.STRING
                    "clob" -> StaticType.CLOB
                    "blob" -> StaticType.BLOB
                    "bag" -> StaticType.BAG
                    "missing" -> StaticType.MISSING
                    "list" -> StaticType.LIST
                    "sexp" -> StaticType.SEXP
                    "struct" -> StaticType.STRUCT
                    "any" -> StaticType.ANY
                    "\$null" -> StaticType.NULL
                    else ->
                        // Not one of the default named types, so let's try to resolve it.
                        typeDefLookup[name.text]?.toStaticType(topLevelTypeName) ?: throw TypeNotFoundException(name.text)
                }.let {
                    when {
                        nullable.booleanValue -> {
                            // Create union type with null
                            val topLevelTypes = this.getTopLevelTypes(listOf())
                            asNullable(it).let { nullableType ->
                                if (topLevelTypes.isEmpty()) {
                                    // Named type is not a top-level type
                                    // Type references that are named core types (e.g. int, string), do not have a type definition
                                    // Hence, metas does not need to be associated here
                                    nullableType
                                } else {
                                    nullableType.withMetas(mapOf(ISL_META_KEY to topLevelTypes))
                                }
                            }
                        }
                        else -> it
                    }
                }

            is IonSchemaModel.TypeReference.InlineType -> {
                // "name" must not be present in inline types
                if (type.name != null) {
                    error("name \"${type.name!!.text}\" is not allowed in inline type definition: ${type.toIsl(isInline = true)}")
                } else {
                    val inlineType = type.toStaticType(topLevelTypeName)
                    when {
                        nullable.booleanValue -> {
                            val topLevelTypes = this.getTopLevelTypes(listOf())
                            asNullable(inlineType).withMetas(mapOf(ISL_META_KEY to topLevelTypes + this.type))
                                // We need to call flatten() here because the inlineType itself could be an AnyOfType
                                .flatten()
                        }
                        else -> inlineType
                    }
                }
            }
            is IonSchemaModel.TypeReference.ImportedType -> TODO("Imports are not supported yet")
        }

    /**
     * Returns type constraint
     */
    private fun IonSchemaModel.TypeDefinition.getTypeConstraint(): IonSchemaModel.Constraint.TypeConstraint? =
        constraints.getConstraint(IonSchemaModel.Constraint.TypeConstraint::class)

    /**
     * Returns any_of constraint
     */
    private fun IonSchemaModel.TypeDefinition.getAnyOfConstraint(): IonSchemaModel.Constraint.AnyOf? =
        constraints.getConstraint(IonSchemaModel.Constraint.AnyOf::class)

    /**
     * Returns element type, or `ANY` if element constraint is not present
     */
    private fun IonSchemaModel.TypeDefinition.getElement(topLevelTypeName: String?): StaticType =
        constraints.getConstraint(IonSchemaModel.Constraint.Element::class)?.type?.toStaticType(topLevelTypeName) ?: StaticType.ANY

    private fun IonSchemaModel.TypeDefinition.getStringLengthConstraint(): StringType.StringLengthConstraint =
        when (val constraint = constraints.getConstraint(IonSchemaModel.Constraint.CodepointLength::class)?.rule) {
            null -> StringType.StringLengthConstraint.Unconstrained
            else -> constraint.toStringLengthConstraint()
        }

    private fun IonSchemaModel.TypeDefinition.getIntRangeConstraint(): IntType.IntRangeConstraint =
        when (val spec = constraints.getConstraint(IonSchemaModel.Constraint.ValidValues::class)?.spec) {
            null -> IntType.IntRangeConstraint.UNCONSTRAINED
            is IonSchemaModel.ValidValuesSpec.OneOfValidValues -> error("One of valid values constraint is not supported for integers")
            is IonSchemaModel.ValidValuesSpec.RangeOfValidValues -> when (val range = spec.range) {
                is IonSchemaModel.ValuesRange.NumRange -> {
                    val min = when (val min = range.range.min) {
                        is IonSchemaModel.NumberExtent.Min -> TODO("min symbol for integer range is not supported yet.")
                        is IonSchemaModel.NumberExtent.Max -> error("Integer's min value cannot be 'max'")
                        is IonSchemaModel.NumberExtent.Inclusive -> min.value.longValue
                        is IonSchemaModel.NumberExtent.Exclusive -> TODO("Exclusive ranges for integers are not supported yet")
                    }
                    val max = when (val max = range.range.max) {
                        is IonSchemaModel.NumberExtent.Min -> error("Integer's max value cannot be 'min'")
                        is IonSchemaModel.NumberExtent.Max -> TODO("min symbol for integer range is not supported yet.")
                        is IonSchemaModel.NumberExtent.Inclusive -> max.value.longValue
                        is IonSchemaModel.NumberExtent.Exclusive -> TODO("Exclusive ranges for integers are not supported yet")
                    }

                    when {
                        min == Short.MIN_VALUE.toLong() && max == Short.MAX_VALUE.toLong() -> IntType.IntRangeConstraint.SHORT
                        min == Int.MIN_VALUE.toLong() && max == Int.MAX_VALUE.toLong() -> IntType.IntRangeConstraint.INT4
                        min == Long.MIN_VALUE && max == Long.MAX_VALUE -> IntType.IntRangeConstraint.LONG
                        else -> error("Invalid range for integers $min-$max")
                    }
                }
                else -> error("Invalid range for integers. spec: $spec")
            }
        }

    private fun IonSchemaModel.NumberRule.toStringLengthConstraint(): StringType.StringLengthConstraint {
        return when (this) {
            is IonSchemaModel.NumberRule.EqualsNumber -> StringType.StringLengthConstraint.Constrained(
                NumberConstraint.Equals(this.value.toInt())
            )
            is IonSchemaModel.NumberRule.EqualsRange -> {
                when (val min = this.range.min) {
                    is IonSchemaModel.NumberExtent.Max -> error("Min value of a range cannot be max")
                    is IonSchemaModel.NumberExtent.Inclusive -> if (min.value.toInt() != 0) {
                        // This is a range that is not supported in [StringType].
                        // Instead of throwing an error, we map to an un-constrained string.
                        // If the static type generated with this is used as a target type in CAST
                        //   the [TypedOpParameter] should contain the validation predicates that check
                        //   for appropriate length.
                        // TODO: Add ranged length constraints in StaticType
                        return StringType.StringLengthConstraint.Unconstrained
                    }
                    is IonSchemaModel.NumberExtent.Exclusive -> if ((min.value.toInt() + 1) != 0) {
                        // Same as above
                        return StringType.StringLengthConstraint.Unconstrained
                    }
                    is IonSchemaModel.NumberExtent.Min -> {}
                }

                when (val max = this.range.max) {
                    is IonSchemaModel.NumberExtent.Min -> error("Max value of a range cannot be min")
                    is IonSchemaModel.NumberExtent.Max -> StringType.StringLengthConstraint.Unconstrained
                    is IonSchemaModel.NumberExtent.Inclusive -> StringType.StringLengthConstraint.Constrained(
                        NumberConstraint.UpTo(max.value.toInt())
                    )
                    is IonSchemaModel.NumberExtent.Exclusive -> StringType.StringLengthConstraint.Constrained(
                        NumberConstraint.UpTo(max.value.toInt() - 1)
                    )
                }
            }
        }
    }

    private fun IonSchemaModel.TypeDefinition.getPrecisionScaleConstraint(): DecimalType.PrecisionScaleConstraint {
        val precision = when (val rule = constraints.getConstraint(IonSchemaModel.Constraint.Precision::class)?.rule) {
            null -> null
            // Only certain decimal precisions can map to [DecimalType] without errors:
            // - Exact precisions of 1
            // - Precision ranges starting from 1 (inclusive), 0 (exclusive), or min
            is IonSchemaModel.NumberRule.EqualsNumber -> when (val exactPrecision = rule.value.toInt()) {
                1 -> exactPrecision
                else -> error(
                    "Exact decimal precision of $exactPrecision can't map to DecimalType. " +
                        "Only exact precisions of 1 are allowed."
                )
            }
            is IonSchemaModel.NumberRule.EqualsRange -> {
                when (val minPrecision = rule.range.min) {
                    is IonSchemaModel.NumberExtent.Inclusive -> {
                        val minPrecisionValue = minPrecision.value.toInt()
                        if (minPrecisionValue != 1) {
                            error(
                                "Inclusive precision range min of $minPrecisionValue can't map to DecimalType. " +
                                    "Only inclusive precision range mins of 1 are allowed."
                            )
                        }
                    }
                    is IonSchemaModel.NumberExtent.Exclusive -> {
                        val minPrecisionValue = minPrecision.value.toInt()
                        if (minPrecisionValue != 0) {
                            error(
                                "Exclusive precision range min of $minPrecisionValue can't map to DecimalType. " +
                                    "Only exclusive precision range mins of 0 are allowed"
                            )
                        }
                    }
                    is IonSchemaModel.NumberExtent.Max -> error("Min value of a range cannot be 'max'")
                    is IonSchemaModel.NumberExtent.Min -> { } // decimal precision range min == 1
                }
                when (val maxPrecision = rule.range.max) {
                    is IonSchemaModel.NumberExtent.Inclusive -> maxPrecision.value.toInt()
                    is IonSchemaModel.NumberExtent.Exclusive -> maxPrecision.value.toInt() - 1
                    is IonSchemaModel.NumberExtent.Min -> error("Max value of a range cannot be 'min'")
                    is IonSchemaModel.NumberExtent.Max -> return DecimalType.PrecisionScaleConstraint.Unconstrained
                }
            }
        }

        val scale = when (val rule = constraints.getConstraint(IonSchemaModel.Constraint.Scale::class)?.rule) {
            null -> null
            is IonSchemaModel.NumberRule.EqualsNumber -> rule.value.toInt()
            is IonSchemaModel.NumberRule.EqualsRange -> return DecimalType.PrecisionScaleConstraint.Unconstrained
        }

        return if (precision == null) {
            if (scale == null) {
                DecimalType.PrecisionScaleConstraint.Unconstrained
            } else {
                error("Precision needs be set when scale is set")
            }
        } else {
            if (scale == null) {
                DecimalType.PrecisionScaleConstraint.Constrained(precision)
            } else {
                DecimalType.PrecisionScaleConstraint.Constrained(precision, scale)
            }
        }
    }

    private fun AnyElement.toInt(): Int = this.longValue.toIntExact()

    /**
     * Returns fields map for Struct Type, if present. Otherwise, returns an empty map
     */
    private fun IonSchemaModel.TypeDefinition.getFields(topLevelTypeName: String?): Map<String, StaticType> =
        constraints.getConstraint(IonSchemaModel.Constraint.Fields::class)?.fields?.map {
            it.name.text to it.getStaticType(topLevelTypeName)
        }?.toMap() ?: mapOf()

    /**
     * Returns StaticType for a field type reference
     */
    private fun IonSchemaModel.Field.getStaticType(topLevelTypeName: String?): StaticType =
        when {
            type is IonSchemaModel.TypeReference.InlineType &&
                (type as IonSchemaModel.TypeReference.InlineType).type.isRequired() -> type.toStaticType(topLevelTypeName)
            else -> when (val st = type.toStaticType(topLevelTypeName)) {
                is AnyOfType -> st.copy(types = st.types + StaticType.MISSING, metas = st.metas)
                else -> StaticType.unionOf(st, StaticType.MISSING, metas = st.metas)
            }
        }

    /**
     * Recursively accumulates all top-level types present in the type definition
     */
    private fun IonSchemaModel.TypeDefinition.getTopLevelTypes(accumulator: List<IonSchemaModel.TypeDefinition>): List<IonSchemaModel.TypeDefinition> {
        var current = accumulator
        current = current + this.constraints.items.flatMap { constraint ->
            when (constraint) {
                is IonSchemaModel.Constraint.TypeConstraint -> constraint.type.getTopLevelTypes(current)
                is IonSchemaModel.Constraint.AnyOf -> constraint.types.flatMap { it.getTopLevelTypes(current) }
                is IonSchemaModel.Constraint.Element -> constraint.type.getTopLevelTypes(current)
                is IonSchemaModel.Constraint.Fields -> constraint.fields.flatMap { it.type.getTopLevelTypes(current) }
                else -> listOf()
            }
        }
        return current.distinct()
    }

    /**
     * Recursively accumulates all top-level types present in the type reference
     */
    private fun IonSchemaModel.TypeReference.getTopLevelTypes(accumulator: List<IonSchemaModel.TypeDefinition>): List<IonSchemaModel.TypeDefinition> {
        var current = accumulator
        current = current + when (this) {
            is IonSchemaModel.TypeReference.NamedType -> listOfNotNull(typeDefLookup[name.text])
            is IonSchemaModel.TypeReference.InlineType -> this.type.getTopLevelTypes(current)
            is IonSchemaModel.TypeReference.ImportedType -> TODO("Imports are not supported yet")
        }
        return current
    }

    /**
     * Returns true if type definition has an occurs constraint as 'required'
     */
    private fun IonSchemaModel.TypeDefinition.isRequired() =
        constraints.getConstraint(IonSchemaModel.Constraint.Occurs::class)?.spec is IonSchemaModel.OccursSpec.OccursRequired

    /**
     * Returns true if type definition does not allow open content.
     */
    private fun IonSchemaModel.TypeDefinition.isClosedContent(): Boolean =
        when (constraints.getConstraint(IonSchemaModel.Constraint.ClosedContent::class)) {
            null -> false
            else -> true
        }

    private inline fun <reified T : IonSchemaModel.Constraint> IonSchemaModel.ConstraintList.getConstraint(kClass: KClass<T>): T? {
        items.forEach {
            if (kClass.isInstance(it)) {
                return it as T
            }
        }
        return null
    }
}