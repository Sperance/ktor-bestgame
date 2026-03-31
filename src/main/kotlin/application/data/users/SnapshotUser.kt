package application.data.users

import application.data.BaseDTO
import application.data.characters.SnapshotCharacter
import kotlinx.serialization.Serializable

@Serializable
class SnapshotUser(
    val _id: Long = 0,
    var _name: String,
    var _email: String
) : BaseDTO() {

    fun getCharacters(dao: DAOusers): List<SnapshotCharacter> {
        return dao.findById(_id)?.getCharacters()?.map { it.toSnapshot() }?:listOf()
    }

    override fun toString(): String {
        return "SnapshotUser(_id=$_id, _name='$_name', _email='$_email')"
    }
}