// !USE_EXPERIMENTAL: kotlin.RequiresOptIn
// !DIAGNOSTICS: -UNUSED_PARAMETER
// !WITH_NEW_INFERENCE

import kotlin.experimental.ExperimentalTypeInference

class TypeDefinition<KotlinType : Any> {
    fun parse(parser: (serializedValue: String) -> KotlinType?): Unit = TODO()
    fun serialize(parser: (value: KotlinType) -> Any?): Unit = TODO()
}

@OptIn(ExperimentalTypeInference::class)
fun <KotlinType : Any> defineType(@BuilderInference definition: TypeDefinition<KotlinType>.() -> Unit): Unit = TODO()

fun main() {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER, TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER{OI}!>defineType<!> {
        parse { it.toInt() }
        serialize { <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>it<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE{OI}, DEBUG_INFO_MISSING_UNRESOLVED!>toString<!>() }
    }
}
