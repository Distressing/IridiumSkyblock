package com.iridium.iridiumskyblock.timings;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;

public class TimingsManager {

    private ArrayList<StopWatch> StopWatches = new ArrayList<>();
    private int CoreCount;

    public TimingsManager(){
        CoreCount = getOperatingSystemMXBean().getAvailableProcessors();
    }

    public String Report() throws IOException {
        StringBuilder reportstring = new StringBuilder();

        reportstring.append("Available processors : ").append(CoreCount).append("\n");

        for(StopWatch stopWatch : StopWatches){
            if(stopWatch.isReportable) {
                reportstring.append(stopWatch.getWatchName()).append(" : ").append(stopWatch.getTimeTaken()).append("\n");
                for(WatchRecord watchRecord: stopWatch.getWatchRecords()){
                    reportstring.append("     ");
                    reportstring.append(watchRecord.getJustification()).append(" : ").append(watchRecord.getNanoTime());
                    if(watchRecord.sinceLastCP) reportstring.append(" Since last Checkpoint");
                    reportstring.append("\n");
                }
            }
        }
        Bukkit.broadcastMessage(reportstring.toString());

        reportstring.append("Automatically generated report.");

        return HasteBin.post(reportstring.toString(), false);
    }

    public void Watch(StopWatch stopWatch){
        StopWatches.add(stopWatch);
    }
}
