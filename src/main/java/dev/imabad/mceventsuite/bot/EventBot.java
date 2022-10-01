package dev.imabad.mceventsuite.bot;

import dev.imabad.mceventsuite.bot.modules.meet.MeetModule;
import dev.imabad.mceventsuite.core.EventCore;
import dev.imabad.mceventsuite.core.modules.mysql.events.MySQLLoadedEvent;
import dev.imabad.mceventsuite.core.modules.redis.RedisModule;
import dev.imabad.mceventsuite.core.modules.redis.events.RedisConnectionEvent;

import java.io.File;
import java.util.Arrays;

public class EventBot {
    public static void main(String[] args) {
        new EventBot();
    }

    public EventBot() {
        new EventCore(new File("./"));
        System.out.println("event core initialised");

        EventCore.getInstance().getEventRegistry().registerListener(RedisConnectionEvent.class, redisConnectionEvent -> {
            EventCore.getInstance().getModuleRegistry().addAndEnableModule(new MeetModule());
            System.out.println("meet module enabled");
        });
        System.out.println("listener registered");

        EventCore.getInstance().getModuleRegistry().addAndEnableModule(new RedisModule());
        System.out.println("enabled redis");
    }
}
