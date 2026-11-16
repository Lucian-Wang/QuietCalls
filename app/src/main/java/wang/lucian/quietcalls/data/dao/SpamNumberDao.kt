package wang.lucian.quietcalls.data.dao

import androidx.room.Dao
import androidx.room.Query
import wang.lucian.quietcalls.data.entity.SpamNumberEntity

@Dao
interface SpamNumberDao {
    @Query("SELECT * FROM spam_numbers WHERE phone_number = :phoneNumber LIMIT 1")
    fun search(phoneNumber: String): SpamNumberEntity?

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun getRowCount(): Long
}
