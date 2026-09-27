package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.GroupDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDayDao {
    @Query("SELECT * FROM group_days WHERE group_id = :groupId")
    fun observeGroupDays(groupId: String): Flow<List<GroupDayEntity>>

    @Query("DELETE FROM group_days WHERE group_id = :groupId")
    suspend fun deleteGroupDays(groupId: String)

    @Query("SELECT * FROM group_days WHERE teacher_id = :teacherId")
    fun observeGroupDaysByTeacher(teacherId: String): Flow<List<GroupDayEntity>>

    @Query("SELECT * FROM group_days WHERE teacher_id = :teacherId")
    suspend fun getGroupDaysByTeacher(teacherId: String): List<GroupDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupDays(groupDays: List<GroupDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupDay(groupDay: GroupDayEntity)

    @Query("DELETE FROM group_days WHERE teacher_id = :teacherId")
    suspend fun deleteGroupDaysByTeacher(teacherId: String)
}
