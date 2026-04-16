package features.property

object PropertyCache {
    private var properties = listOf<Property>()

    fun refresh(repo: PropertyRepository) {
        properties = repo.findAll()
    }

    fun getAll() = properties
    fun getFromCode(code: String) = properties.find { it.code == code }
    fun getFromName(name: String) = properties.find { it.name == name }
}