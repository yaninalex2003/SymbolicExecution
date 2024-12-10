package engine

import ast.Statement

data class State(
    val memory: SymbolicMemory,
    val pathCondition: List<String>,
    var nextStatements: List<Statement>,
    var result: String? = null
) {
    override fun toString() = "{\n$memory\npc = ${pathCondition.joinToString(" & ")}\nresult = $result\n}"
}
