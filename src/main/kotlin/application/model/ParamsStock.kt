package application.model

import kotlinx.serialization.Serializable

@Serializable
data class ParamsStock(
    var param: Long,
    var value: Double,
) {
    fun copy(): ParamsStock {
        return ParamsStock(
            param = this.param,
            value = this.value,
        )
    }
}