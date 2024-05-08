package felis.language

class DelegatingLanguageAdapter : LanguageAdapter, Iterable<LanguageAdapter> {
    private val children: MutableList<LanguageAdapter> = mutableListOf()

    fun registerAdapter(adapter: LanguageAdapter) = this.children.add(adapter)
    override fun <T> createInstance(specifier: String, clazz: Class<out T>): Result<T> = this
        .asSequence()
        .map { it.createInstance(specifier, clazz) }
        .fold(Result.failure(LanguageAdapterException(specifier))) { prev, curr ->
            if (curr.isSuccess) {
                curr
            } else {
                prev.exceptionOrNull()?.addSuppressed(curr.exceptionOrNull())
                prev
            }
        }

    override fun iterator(): Iterator<LanguageAdapter> = this.children.iterator()
}