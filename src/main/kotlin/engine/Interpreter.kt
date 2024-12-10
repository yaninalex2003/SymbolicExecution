package engine

import ast.Assignment
import ast.BinOp
import ast.BoolConstant
import ast.ErrorExpression
import ast.Expression
import ast.IfStmt
import ast.IntConstant
import ast.ReturnStmt
import ast.UnOp
import ast.VarRef

class Interpreter {
    fun execute(state: State): List<State> {
        val curStatement = state.nextStatements.firstOrNull() ?: return emptyList()
        state.nextStatements = state.nextStatements.drop(1)
        when (curStatement) {
            is IfStmt -> {
                val symCondition = Evaluator(state.memory).evaluate(curStatement.condition)

                val trueMemory = state.memory.clone()
                val trueState = State(
                    trueMemory,
                    state.pathCondition + listOf(symCondition),
                    curStatement.thenBlock + state.nextStatements
                )

                val falseMemory = state.memory.clone()
                val falseState = State(
                    falseMemory,
                    state.pathCondition + listOf("!($symCondition)"),
                    curStatement.elseBlock + state.nextStatements
                )
                return listOf(trueState, falseState)

            }

            is Assignment -> {
                val symValue = Evaluator(state.memory).evaluate(curStatement.value)
                state.memory.write(curStatement.name, "(${symValue})")
                return listOf(state)

            }

            is ReturnStmt -> {
                val symValue = Evaluator(state.memory).evaluate(curStatement.returnExpr)
                state.result = symValue
                return listOf(state)
            }

            else -> return emptyList()
        }
    }

    private class Evaluator(val memory: SymbolicMemory) {
        fun evaluate(expr: Expression): String {
            return when (expr) {
                is BinOp -> {
                    val result = StringBuilder()

                    var rightExpr = expr
                    while (rightExpr is BinOp) {
                        result.append(evaluate(rightExpr.lhs))
                        result.append(" ${rightExpr.kind} ")
                        rightExpr = rightExpr.rhs
                    }

                    result.append(evaluate(rightExpr))
                    return result.toString()
                }

                is BoolConstant -> expr.value.toString()
                is ErrorExpression -> throw RuntimeException("ErrorExpression")
                is IntConstant -> expr.value.toString()
                is UnOp -> "${expr.kind}(${evaluate(expr.subExpr)})"
                is VarRef -> memory.read(expr.identifier)
            }
        }
    }
}
