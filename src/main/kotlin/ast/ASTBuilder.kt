package ast

import generated.mygrammarBaseListener
import generated.mygrammarParser
import org.antlr.v4.runtime.tree.TerminalNode
import java.util.*

abstract class ASTBuilder : mygrammarBaseListener() {
    abstract fun getFunction(): Function

    abstract fun hasErrors(): Boolean

    companion object {
        fun create(): ASTBuilder {
            return ASTBuilderImpl()
        }
    }
}

private class ASTBuilderImpl : ASTBuilder() {
    private val function: Function = Function(mutableListOf(), mutableListOf(), null, null)
    private val expressionStack: MutableList<Expression> = mutableListOf()
    private val statementStack: MutableList<Statement> = mutableListOf()
    private val parameters: MutableMap<String, Int> = mutableMapOf()
    private var errorCount: Int = 0

    override fun getFunction(): Function {
        return function
    }

    override fun hasErrors(): Boolean {
        return errorCount > 0
    }

    override fun enterFunction(ctx: mygrammarParser.FunctionContext) {
        function.returnType = getType(ctx.type())
    }

    override fun enterParamdecl(ctx: mygrammarParser.ParamdeclContext) {
        val parameter = Parameter(
            name = ctx.NAME().text,
            type = getType(ctx.type())
        )
        function.parameters.add(parameter)
        val index = function.parameters.size - 1
        val new = parameters.putIfAbsent(parameter.name, index) == null
        if (!new) {
            reportError("parameter ${parameter.name} redeclared")
        }
    }

    override fun exitBody(ctx: mygrammarParser.BodyContext) {
        val bodySize = ctx.statement().size
        if (statementStack.size != bodySize) {
            throw RuntimeException(
                "expected function body of $bodySize statements, but found ${statementStack.size} " +
                        "statements in the statement stack"
            )
        }
        for (i in 0 until bodySize) {
            function.body.add(popStmt())
        }
        function.body.reverse()
    }

    override fun exitReturnstmt(ctx: mygrammarParser.ReturnstmtContext) {
        if (expressionStack.isEmpty()) {
            throw RuntimeException("expression stack is empty at the return statement")
        }
        val returnExpr = popExpr()
        function.returnValue = returnExpr
        if (function.returnValue?.type != function.returnType) {
            reportError(
                "return value of type ${function.returnValue?.type} does not match with " +
                        "expected return type ${function.returnType}"
            )
        }
        if (expressionStack.isNotEmpty()) {
            throw RuntimeException("unexpected leftover expressions in the stack after return statement")
        }
    }

    override fun exitIfstmt(ctx: mygrammarParser.IfstmtContext) {
        val thenBodySize = ctx.thenbody().statement().size
        val elseBodySize = ctx.elsebody().statement().size
        if (statementStack.size < thenBodySize + elseBodySize) {
            throw RuntimeException("statement stack size is not enough to assemble an IfStmt, expected at least ${thenBodySize + elseBodySize}")
        }
        if (expressionStack.isEmpty()) {
            throw RuntimeException("expected a condition expression in the expression stack for IfStmt, but expression stack is empty")
        }
        val condition = popExpr()
        val thenBody = mutableListOf<Statement>()
        val elseBody = mutableListOf<Statement>()
        for (i in 0 until elseBodySize) {
            elseBody.add(popStmt())
        }
        elseBody.reverse()
        for (i in 0 until thenBodySize) {
            thenBody.add(popStmt())
        }
        thenBody.reverse()
        if (condition.type != Type.T_BOOL) {
            reportError("expected bool for if condition expression, but found ${condition.type}")
            return pushStmt(ErrorStatement)
        }
        pushStmt(IfStmt(condition, thenBody, elseBody))
    }

    override fun exitAssign(ctx: mygrammarParser.AssignContext) {
        if (expressionStack.isEmpty()) {
            throw RuntimeException("expression stack is empty at assign")
        }
        val rhs = popExpr()
        val varName = ctx.NAME().text
        val parameterIndex = parameters[varName]
        if (parameterIndex == null) {
            reportError("unresolved reference to $varName in assignment lhs")
            return pushStmt(ErrorStatement)
        }
        val parameterType = function.parameters[parameterIndex].type
        if (parameterType != rhs.type) {
            reportError("expected type $parameterType of assignment rhs, found ${rhs.type}")
            return pushStmt(ErrorStatement)
        }
        pushStmt(Assignment(varName, rhs))
    }

    private fun handleBinOp(
        binopKind: BinOpKind,
        argType: Type,
        retType: Type
    ) {
        if (expressionStack.size < 2) {
            reportError("Not enough operands on the stack for binary operation")
            return pushErrorExpr(retType)
        }
        val rhs = popExpr()
        val lhs = popExpr()
        if (lhs.type != argType) {
            reportError("expected $argType type for lhs of $binopKind binary operator, found ${lhs.type}")
            pushExpr(lhs)
            pushExpr(rhs)
            return pushErrorExpr(retType)
        }
        if (rhs.type != argType) {
            reportError("expected $argType type for rhs of $binopKind binary operator, found ${rhs.type}")
            pushExpr(lhs)
            pushExpr(rhs)
            return pushErrorExpr(retType)
        }
        pushExpr(BinOp(binopKind, lhs, rhs, retType))
        return
    }

    override fun exitBinoplorexpr(ctx: mygrammarParser.BinoplorexprContext) {
        checkExpressionStackForBinop()
        handleBinOp(BinOpKind.BO_LOr, Type.T_BOOL, Type.T_BOOL)
    }

    override fun exitBinoplandexpr(ctx: mygrammarParser.BinoplandexprContext) {
        checkExpressionStackForBinop()
        handleBinOp(BinOpKind.BO_LAnd, Type.T_BOOL, Type.T_BOOL)
    }

    override fun exitIntcompareexpr(ctx: mygrammarParser.IntcompareexprContext) {
        checkExpressionStackForBinop()
        if (ctx.BINOP_LT() != null) {
            handleBinOp(BinOpKind.BO_Lt, Type.T_INT, Type.T_BOOL)
        } else if (ctx.BINOP_GT() != null) {
            handleBinOp(BinOpKind.BO_Gt, Type.T_INT, Type.T_BOOL)
        }
    }

    override fun exitUnopnegateexpr(ctx: mygrammarParser.UnopnegateexprContext) {
        if (expressionStack.isEmpty()) {
            throw RuntimeException("an empty expression stack for unary negation operator")
        }
        val subExpr = popExpr()
        if (subExpr.type != Type.T_BOOL) {
            reportError("expected bool for unary ! operator, but found ${subExpr.type}")
            return pushErrorExpr(Type.T_BOOL)
        }
        pushExpr(UnOp(UnOpKind.UO_Neg, subExpr, Type.T_BOOL))
    }

    override fun enterBoolliteral(ctx: mygrammarParser.BoolliteralContext) {
        when {
            ctx.FALSE() != null -> pushExpr(BoolConstant(false))
            ctx.TRUE() != null -> pushExpr(BoolConstant(true))
            else -> error("invalid bool literal produced by ANTLR")
        }
    }

    override fun exitBinopintexpr(ctx: mygrammarParser.BinopintexprContext) {
        checkExpressionStackForBinop()
        if (ctx.BINOP_ADD() != null) {
            handleBinOp(BinOpKind.BO_Add, Type.T_INT, Type.T_INT)
        } else if (ctx.BINOP_SUB() != null) {
            handleBinOp(BinOpKind.BO_Sub, Type.T_INT, Type.T_INT)
        }
    }

    override fun enterIntliteral(ctx: mygrammarParser.IntliteralContext) {
        val value = parseInt(ctx.NUMBER())
        if (value.isPresent) {
            pushExpr(IntConstant(value.get()))
        } else {
            pushErrorExpr(Type.T_INT)
        }
    }

    override fun enterVarrefexpr(ctx: mygrammarParser.VarrefexprContext) {
        val name = ctx.text
        val parameterIndex = parameters[name]
        if (parameterIndex == null) {
            reportError("unresolved reference to $name")
            return pushErrorExpr(Type.T_INT)
        }
        pushExpr(VarRef(name, function.parameters[parameterIndex].type))
    }

    private fun getType(typeCtx: mygrammarParser.TypeContext): Type {
        return if (typeCtx.BOOL() != null) Type.T_BOOL else Type.T_INT
    }

    private fun reportError(details: String) {
        errorCount++
        System.err.println("semantics error: $details")
    }

    private fun parseInt(node: TerminalNode): Optional<Long> {
        return try {
            Optional.of(node.text.toLong())
        } catch (e: NumberFormatException) {
            Optional.empty()
        }
    }

    private fun pushExpr(expression: Expression) {
        expressionStack.add(expression)
    }

    private fun popExpr(): Expression {
        if (expressionStack.isEmpty()) {
            errorCount++
            System.err.println("Expression stack underflow")
            return ErrorExpression(Type.T_INT) // Return error expression on underflow
        }
        return expressionStack.removeLast()
    }

    private fun pushErrorExpr(type: Type) {
        pushExpr(ErrorExpression(type))
    }

    private fun pushStmt(statement: Statement) {
        statementStack.add(statement)
    }

    private fun popStmt(): Statement {
        if (statementStack.isEmpty()) {
            errorCount++
            System.err.println("Statement stack underflow")
            return ErrorStatement
        }
        return statementStack.removeLast()
    }

    private fun checkExpressionStackForBinop() {
        if (expressionStack.size < 2) {
            throw RuntimeException(
                "need at least two expressions in the stack for a binary operator"
            )
        }
    }
}
