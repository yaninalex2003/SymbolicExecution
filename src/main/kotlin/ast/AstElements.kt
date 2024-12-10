package ast

enum class Type {
    T_BOOL,
    T_INT;

    override fun toString(): String =
        when (this) {
            T_BOOL -> "T_BOOL"
            T_INT -> "T_INT"
        }
}

data class Parameter(val name: String, val type: Type) {
    override fun toString(): String = "$name: ${type}"
}

sealed class Expression(val type: Type)

class ErrorExpression(type: Type) : Expression(type)

class VarRef(val identifier: String, type: Type) : Expression(type)

class IntConstant(val value: Long) : Expression(Type.T_INT)

class BoolConstant(val value: Boolean) : Expression(Type.T_BOOL)

enum class UnOpKind {
    UO_Neg;

    override fun toString(): String =
        when (this) {
            UO_Neg -> "!"
        }
}

class UnOp(val kind: UnOpKind, val subExpr: Expression, type: Type) : Expression(type)

enum class BinOpKind {
    BO_Add,
    BO_Sub,
    BO_Lt,
    BO_Gt,
    BO_LAnd,
    BO_LOr;

    override fun toString(): String =
        when (this) {
            BO_Add -> "+"
            BO_Sub -> "-"
            BO_Lt -> "<"
            BO_Gt -> ">"
            BO_LAnd -> "&"
            BO_LOr -> "|"
        }
}

class BinOp(val kind: BinOpKind, val lhs: Expression, val rhs: Expression, type: Type) : Expression(type)

sealed class Statement

data object ErrorStatement : Statement()

data class Assignment(val name: String, val value: Expression) : Statement()

data class IfStmt(
    val condition: Expression,
    val thenBlock: List<Statement>,
    val elseBlock: List<Statement>
) : Statement()

data class ReturnStmt(
    val returnExpr: Expression
) : Statement()

data class Function(
    val parameters: MutableList<Parameter>,
    val body: MutableList<Statement>,
    var returnType: Type?,
    var returnValue: Expression?
)
