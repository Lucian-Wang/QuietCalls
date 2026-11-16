package wang.lucian.quietcalls.data

import androidx.room.Database
import androidx.room.RoomDatabase
import wang.lucian.quietcalls.data.dao.MastDao
import wang.lucian.quietcalls.data.dao.SpamNumberDao
import wang.lucian.quietcalls.data.entity.MetadataEntity
import wang.lucian.quietcalls.data.entity.SpamNumberEntity

@Database(
    entities = [MetadataEntity::class, SpamNumberEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MastDatabase : RoomDatabase() {
    abstract fun mastDao(): MastDao
    abstract fun spamNumberDao(): SpamNumberDao
}
