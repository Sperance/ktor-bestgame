package features.items

object ItemsCache {
    private var items = listOf<Item>()

    fun refresh(repo: ItemsRepository) {
        items = repo.findAll()
    }

    fun getAll() = items
    fun getFromName(name: String) = items.find { it.name == name }
}