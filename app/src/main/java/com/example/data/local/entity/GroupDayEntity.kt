package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "group_days",
    primaryKeys = ["group_id", "day_of_week"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["group_id"])
    ]
)
data class GroupDayEntity(
    @ColumnInfo(name = "teacher_id")
    val teacherId: String,

    @ColumnInfo(name = "group_id")
    val groupId: String,

    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: String
)
