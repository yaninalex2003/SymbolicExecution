import ast.ASTBuilder
import engine.Runner
import generated.mygrammarLexer
import generated.mygrammarParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.io.FileInputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Provide two arguments: <inputPath> <outputPath>")
        return
    }
    val inputPath = Paths.get(args[0])
    val outputPath = Paths.get(args[1])
    if (!Files.exists(inputPath)) {
        System.err.println("File not found: $inputPath")
        exitProcess(1)
    }

    FileInputStream(inputPath.toFile()).use { istream ->
        val input = CharStreams.fromStream(istream)
        val lexer = mygrammarLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = mygrammarParser(tokens)

        val tree: ParseTree = parser.function()
        if (parser.numberOfSyntaxErrors > 0) {
            System.err.println("fatal: encountered syntax errors")
            exitProcess(1)
        }

        val builder = ASTBuilder.create()
        ParseTreeWalker().walk(builder, tree)

        val f = builder.getFunction()

        val finishStates = Runner.run(f)

        PrintWriter(outputPath.toFile()).use { writer ->
            finishStates.forEach {
                writer.println(it)
            }
        }
    }
}
