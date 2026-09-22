package config

import features.data.redemptionCodes.RedemptionCodes
import features.data.redemptionCodes.RedemptionItem
import features.data.redemptionCodes.RedemptionKind

/**
 * Промокоды, с которыми сервер поднимается на пустой базе.
 *
 * Отдельный объект, а не список внутри [DatabaseSeeder], по той же причине, что и
 * остальные сидеры: это чистые данные, и их правила проверяются тестом без Mongo.
 * Правила те же, что у [features.data.redemptionCodes.RedemptionCodesRepository.validateBeforeInsert],
 * потому что сидер идёт через тот же insertMany - пустой подарок уронил бы старт.
 *
 * Награда здесь только опыт и золото: код заводится раньше, чем у справочников
 * появляются стабильные идентификаторы, а ссылаться на документ, которого может не
 * быть, значит обещать игроку то, чего сервер не выдаст.
 */
object RedemptionSeeder {

    fun seed(): List<RedemptionCodes> = listOf(
        RedemptionCodes("ALFA_BETA_GAMMA", listOf(
            RedemptionItem(RedemptionKind.EXPERIENCE, amount = 500.0),
            RedemptionItem(RedemptionKind.GOLD, amount = 100.0)
        ), "")
    )
}
