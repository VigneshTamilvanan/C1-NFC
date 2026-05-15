package com.test.mvinfc;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ScannedQrDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ScannedQrEntity entity);

    @Query("SELECT COUNT(*) FROM scanned_qr WHERE ticketId = :ticketId")
    int exists(String ticketId);

    @Query("SELECT COUNT(*) FROM scanned_qr WHERE ticketId = :ticketId AND scannedAt >= :since")
    int existsSince(String ticketId, String since);
}
