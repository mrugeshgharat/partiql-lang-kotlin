package org.partiql.lang.graph

import com.amazon.ion.IonSystem
import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.Schema
import org.partiql.lang.partiqlisl.getResourceAuthority
import java.io.File

class GraphValidationException(message: String) : RuntimeException(message)
object ExternalGraphReader {

    private val ion: IonSystem = IonSystemBuilder.standard().build()
    private val iss = IonSchemaSystemBuilder.standard()
        .addAuthority(getResourceAuthority(ion))
        .withIonSystem(ion)
        .build()
    private val graphSchema: Schema = iss.loadSchema("graph.isl")
    private val graphType = graphSchema.getType("graph")!!

    fun validate(graphIon: IonValue) {
        val violations = graphType.validate(graphIon)
        if (!violations.isValid())
            throw GraphValidationException("Ion data did not validate as a graph.isl graph: \n$violations")
    }

    fun validate(graphStr: String) {
        val graphIon = ion.singleValue(graphStr)
        validate(graphIon)
    }

    fun validate(graphFile: File) {
        val graphStr = graphFile.readText()
        validate(graphStr)
    }
}
