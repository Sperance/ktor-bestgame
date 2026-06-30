package features.characterMongo

import application.enums.EnumStatHelper
import application.enums.EnumStatType
import application.model.Stat
import base.exception.ExceptionForCode
import base.repository.BaseRepositoryMongo
import com.mongodb.kotlin.client.coroutine.ClientSession
import extensions.CONST_USER_MAX_CHARACTERS
import features.property.PropertyCache
import features.userMongo.UserRepositoryMongo

object CharacterMongoRepository : BaseRepositoryMongo<CharacterMongo>(
    collectionName = "CharacterMongo",
    entityClass = CharacterMongo::class
) {
    init {
        initialize()
    }

    override suspend fun validateBeforeInsert(entity: CharacterMongo) {
        val findedUser = UserRepositoryMongo.findById(entity.userId)
        if (findedUser == null) throw ExceptionForCode("Не найден пользователь с ID ${entity.userId}", "CMR_VALIDATEINSERT_USER")
        if (findedUser.countCharacters >= CONST_USER_MAX_CHARACTERS) throw ExceptionForCode("У пользователя уже есть максимальное количество персонажей", "CMR_VALIDATEINSERT_MAX_CHARACTERS")

        if (entity.params.isEmpty())
            entity.params = getStockParams()
    }

    override suspend fun validateAfterInsert(entity: CharacterMongo, session: ClientSession) {
        val findedUser = UserRepositoryMongo.findById(entity.userId)
        if (findedUser == null) throw ExceptionForCode("Не найден пользователь с ID ${entity.userId}", "CMR_VALIDATEINSERT_USER")
        findedUser.countCharacters++
        if (findedUser.countCharacters > CONST_USER_MAX_CHARACTERS) throw ExceptionForCode("У пользователя уже есть максимальное количество персонажей", "CMR_VALIDATEINSERT_MAX_CHARACTERS")
        UserRepositoryMongo.update(findedUser, session)
    }

    private fun getStockParams(): MutableSet<Stat> {
        val stock = mutableSetOf<Stat>()
        stock.add(Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_HEALTH), EnumStatType.STOCK,100.0))
        stock.add(Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_STRENGTH), EnumStatType.STOCK,1.0))
        stock.add(Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_AGILITY), EnumStatType.STOCK,1.0))
        stock.add(Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INTELLECT), EnumStatType.STOCK,1.0))
        stock.add(Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INVENTORY_SIZE), EnumStatType.STOCK,10.0))
        stock.add(Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_CRITICAL_DAMAGE), EnumStatType.STOCK,200.0))
        stock.add(Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_ATTACK_SPEED), EnumStatType.STOCK,1.5))
        return stock
    }
}