package dev.imabad.mceventsuite.bot.modules.meet;

import dev.imabad.mceventsuite.core.modules.redis.RedisModule;
import lombok.Getter;

import java.util.UUID;

@Getter
public class GreetSpot {
    private RedisModule redisModule;

    private double x;
    private double y;
    private double z;

    private UUID currentUser;
    private UUID pastUser;
    private long timeReached;

    public GreetSpot(RedisModule redisModule, double x, double y, double z){
        this.redisModule = redisModule;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void updateCurrentUser(UUID uuid) {
        if(this.currentUser != null)
            this.pastUser = this.currentUser;

        this.currentUser = uuid;
        if(uuid != null)
            this.timeReached = System.currentTimeMillis();
    }
}
