package com.iridium.iridiumskyblock.timings;

public class WatchRecord {
    private String Justification;
    private Long NanoTime;
    public Boolean sinceLastCP;

    public WatchRecord(Long NanoTime, String Justification, Boolean sinceLastCP){
        this.NanoTime = NanoTime;
        this.Justification = Justification;
        this.sinceLastCP = sinceLastCP;
    }

    public String getJustification(){return Justification;}

    public Long getNanoTime(){return NanoTime;}
}
