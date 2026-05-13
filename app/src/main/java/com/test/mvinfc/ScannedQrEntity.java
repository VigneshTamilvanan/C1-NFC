package com.test.mvinfc;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "scanned_qr")
public class ScannedQrEntity {
    @PrimaryKey
    @NonNull
    public String ticketId;
    public String scannedAt;
}
