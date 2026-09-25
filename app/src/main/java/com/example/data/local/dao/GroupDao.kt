package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE teacher_id = :teacherId")
    fun observeGroupsByTeacher(teacherId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE teacher_id = :teacherId AND active = 1")
    fun observeActiveGroupsByTeacher(teacherId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE teacher_id = :teacherId AND grade_id = :gradeId")
    fun observeGroupsByGrade(teacherId: String, gradeId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun observeGroupById(groupId: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)
}
