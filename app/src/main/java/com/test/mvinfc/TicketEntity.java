package com.test.mvinfc;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tickets")
public class TicketEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String txnId;
    public String route;
    public String source;
    public String destination;
    public String fare;
    public String conductorId;
    public String timestamp;
    public String ticketNo;       // filled after sync
    public boolean synced;        // false = pending sync
}
