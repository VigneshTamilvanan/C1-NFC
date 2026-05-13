package com.test.mvinfc;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TicketDao {
    @Insert
    long insert(TicketEntity ticket);

    @Update
    void update(TicketEntity ticket);

    @Query("SELECT * FROM tickets WHERE synced = 0")
    List<TicketEntity> getPending();

    @Query("SELECT COUNT(*) FROM tickets WHERE synced = 0")
    int getPendingCount();
}
