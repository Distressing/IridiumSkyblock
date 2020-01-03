package com.iridium.iridiumskyblock.commands;

import com.iridium.iridiumskyblock.IridiumSkyblock;
import com.iridium.iridiumskyblock.timings.TimingsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class DebugInfoCommand extends Command {

    public DebugInfoCommand() {
        super(Arrays.asList("debuginfo"), "Detailed plugin report", "admin", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Bukkit.getScheduler().runTaskAsynchronously(IridiumSkyblock.getInstance(), new Runnable() {
            @Override
            public void run() {
                try {
                    sender.sendMessage(IridiumSkyblock.timingsManager.Report());
                }catch (IOException ex){
                    System.out.println(ex.getMessage());
                }
            }
        });
    }

    @Override
    public List<String> TabComplete(CommandSender cs, org.bukkit.command.Command cmd, String s, String[] args) {
        return null;
    }
}
