package engine

class SymbolicMemory {
    private val memory = mutableMapOf<String, String>()

    fun read(name: String): String {
        return memory[name] ?: throw RuntimeException("Variable $name not initialized")
    }

    fun write(name: String, value: String) {
        memory[name] = value
    }

    fun clone(): SymbolicMemory {
        val newSymbolicMemory = SymbolicMemory()
        memory.forEach { (k, v) -> newSymbolicMemory.write(k, v) }
        return newSymbolicMemory
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for ((name, value) in memory) {
            sb.append("$name = $value\n")
        }
        return sb.toString()
    }
}
