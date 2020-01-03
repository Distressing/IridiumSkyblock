package com.iridium.iridiumskyblock.timings;

import java.util.ArrayList;
import java.util.HashSet;

public class StopWatch {

    public Boolean isReportable = false;
    private Long startNano;
    private Long TimeTaken = 0L;
    private String WatchName;
    private ArrayList<WatchRecord> Records = new ArrayList<>();
    private Long LastCPNanos;

    public StopWatch(String WatchName){
        this.WatchName = WatchName;
    }

    public void Start(){
        isReportable = false;
        startNano = System.nanoTime();
        LastCPNanos = System.nanoTime();
    }

    public void Checkpoint(String CheckpointName, boolean SinceLastCP){
        LastCPNanos = SinceLastCP ? (System.nanoTime() - LastCPNanos) : System.nanoTime() - startNano;
        Records.add(new WatchRecord(LastCPNanos,CheckpointName, SinceLastCP));
    }

    public void Stop(){
        isReportable = true;
        TimeTaken = System.nanoTime() - startNano;
    }

    public void Reset(){
        isReportable = false;
        Records.clear();
        TimeTaken = 0L;
    }

    public Long getTimeTaken(){return TimeTaken;}

    public String getWatchName(){return WatchName;}

    public ArrayList<WatchRecord> getWatchRecords() {return Records;}

}
