package com.example.data.repository

import com.example.core.model.Group
import com.example.core.model.GroupDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class MockGroupRepository(
    initialGroups: List<Group> = emptyList(),
    initialGroupDays: List<GroupDay> = emptyList()
) : GroupRepository {

    private val _groups = MutableStateFlow<List<Group>>(initialGroups)
    private val _groupDays = MutableStateFlow<List<GroupDay>>(initialGroupDays)

    fun setGroups(groups: List<Group>) {
        _groups.value = groups
    }

    override fun observeGroups(teacherId: String): Flow<List<Group>> {
        return _groups.map { list -> list.filter { it.teacherId == teacherId } }
    }

    override fun observeActiveGroups(teacherId: String): Flow<List<Group>> {
        return _groups.map { list -> list.filter { it.teacherId == teacherId && it.active } }
    }

    override fun observeGroupsByGrade(teacherId: String, gradeId: String): Flow<List<Group>> {
        return _groups.map { list -> list.filter { it.teacherId == teacherId && it.gradeId == gradeId } }
    }

    override suspend fun getGroupById(teacherId: String, groupId: String): Group? {
        return _groups.value.find { it.id == groupId && it.teacherId == teacherId }
    }

    override suspend fun createGroup(group: Group): Result<Group> {
        _groups.update { it + group }
        return Result.success(group)
    }

    override suspend fun updateGroup(group: Group): Result<Group> {
        _groups.update { list ->
            list.map { if (it.id == group.id && it.teacherId == group.teacherId) group else it }
        }
        return Result.success(group)
    }

    override suspend fun deleteGroup(teacherId: String, groupId: String): Result<Unit> {
        _groups.update { list ->
            list.filterNot { it.id == groupId && it.teacherId == teacherId }
        }
        _groupDays.update { list ->
            list.filterNot { it.groupId == groupId && it.teacherId == teacherId }
        }
        return Result.success(Unit)
    }

    override suspend fun deactivateGroup(teacherId: String, groupId: String): Result<Unit> {
        _groups.update { list ->
            list.map { if (it.id == groupId && it.teacherId == teacherId) it.copy(active = false) else it }
        }
        return Result.success(Unit)
    }

    override suspend fun refreshGroups(teacherId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun assignStudentToGroup(teacherId: String, studentId: String, groupId: String?): Result<Unit> {
        if (groupId != null) {
            val group = getGroupById(teacherId, groupId)
                ?: return Result.failure(Exception("Group not found or cross-tenant"))
        }
        return Result.success(Unit)
    }

    override fun observeGroupDays(teacherId: String, groupId: String): Flow<List<GroupDay>> {
        return _groupDays.map { list -> list.filter { it.teacherId == teacherId && it.groupId == groupId } }
    }

    override fun observeAllGroupDays(teacherId: String): Flow<List<GroupDay>> {
        return _groupDays.map { list -> list.filter { it.teacherId == teacherId } }
    }

    override suspend fun replaceGroupDays(teacherId: String, groupId: String, days: List<String>): Result<Unit> {
        _groupDays.update { list ->
            list.filterNot { it.groupId == groupId && it.teacherId == teacherId } +
                    days.map { GroupDay(teacherId = teacherId, groupId = groupId, dayOfWeek = it) }
        }
        return Result.success(Unit)
    }
}
