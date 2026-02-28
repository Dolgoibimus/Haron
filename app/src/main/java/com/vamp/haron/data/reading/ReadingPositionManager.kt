package com.vamp.haron.data.reading

import com.vamp.haron.data.db.dao.ReadingPositionDao
import com.vamp.haron.data.db.entity.ReadingPositionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ReadingPositionManager {
    private var dao: ReadingPositionDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(dao: ReadingPositionDao) {
        this.dao = dao
    }

    suspend fun save(filePath: String, position: Int, positionExtra: Long = 0L) {
        dao?.upsert(ReadingPositionEntity(filePath, position, positionExtra))
    }

    fun saveAsync(filePath: String, position: Int, positionExtra: Long = 0L) {
        val d = dao ?: return
        scope.launch {
            d.upsert(ReadingPositionEntity(filePath, position, positionExtra))
        }
    }

    suspend fun get(filePath: String): ReadingPositionEntity? {
        return dao?.get(filePath)
    }
}
