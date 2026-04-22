package features.property

import application.enums.EnumStatHelper

object PropertyCache {
    private var properties = listOf<Property>()

    fun refresh(repo: PropertyRepository) {
        properties = repo.findAll()
    }

    fun getAll() = properties
    fun getIdFromEnum(enum: EnumStatHelper) = properties.find { it.code == enum.name }!!.id
}