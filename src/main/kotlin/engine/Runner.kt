package engine

import ast.Function
import ast.ReturnStmt

object Runner {
    fun run(f: Function): List<State> {
        val interpreter = Interpreter()

        val symbolicMemory = SymbolicMemory()
        f.parameters.forEach { param ->
            symbolicMemory.write(param.name, "'${param.name}'")
        }

        val topStatementsInBody = f.body + listOfNotNull(f.returnValue?.let { ReturnStmt(it) })
        val initialState = State(symbolicMemory, emptyList(), topStatementsInBody)


        var states = listOf(initialState)

        val finishStates = mutableListOf<State>()

        while (states.isNotEmpty()) {
            val currentState = states.first()
            states = states.drop(1)

            if (currentState.result != null) {
                finishStates.add(currentState)
            }

            val nextStates = interpreter.execute(currentState)
            states = nextStates + states
        }
        return finishStates
    }
}
