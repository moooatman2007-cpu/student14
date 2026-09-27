package com.example.data.repository

import com.example.core.model.Group
import com.example.core.model.GroupDay
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun observeGroups(teacherId: String): Flow<List<Group>>
    fun observeActiveGroups(teacherId: String): Flow<List<Group>>
    fun observeGroupsByGrade(teacherId: String, gradeId: String): Flow<List<Group>>
    suspend fun getGroupById(teacherId: String, groupId: String): Group?
    suspend fun createGroup(group: Group): Result<Group>
    suspend fun updateGroup(group: Group): Result<Group>
    suspend fun deleteGroup(teacherId: String, groupId: String): Result<Unit>
    suspend fun deactivateGroup(teacherId: String, groupId: String): Result<Unit>
    suspend fun refreshGroups(teacherId: String): Result<Unit>
    suspend fun assignStudentToGroup(teacherId: String, studentId: String, groupId: String?): Result<Unit>
    
    // Group Days
    fun observeGroupDays(teacherId: String, groupId: String): Flow<List<GroupDay>>
    fun observeAllGroupDays(teacherId: String): Flow<List<GroupDay>>
    suspend fun replaceGroupDays(teacherId: String, groupId: String, days: List<String>): Result<Unit>
}
