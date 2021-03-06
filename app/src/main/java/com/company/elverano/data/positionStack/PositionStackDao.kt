package com.company.elverano.data.positionStack

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.jetbrains.annotations.NotNull

@Dao
interface PositionStackDao {
    @NotNull
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPositionStack(positionStackResponse: PositionStackResponse)

    @NotNull
    @Query("SELECT * FROM position_stack_response")
    suspend fun getPositionStack(): PositionStackResponse?

    @Query("DELETE FROM position_stack_response")
    suspend fun deletePositionStack()
}