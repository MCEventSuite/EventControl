package dev.imabad.mceventsuite.bot.modules.meet;

import dev.imabad.mceventsuite.core.modules.redis.RedisChannel;
import dev.imabad.mceventsuite.core.modules.redis.RedisModule;
import dev.imabad.mceventsuite.core.modules.redis.messages.meet.PlayerMoveQueueMessage;
import dev.imabad.mceventsuite.core.modules.redis.messages.meet.ServerAnnounceMeetState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

@Getter
@Slf4j
public class GreetSession {
    private final RedisModule redisModule;

    private final String name;
    private final String displayName;
    private final List<GreetSpot> greetSpots;

    private int sessionTime;
    private int meetTime;

    @Setter
    private long nextTrigger;

    private long pauseTime;

    private long endsTime;
    private long startTime;

    @Setter
    private boolean forceProgression;

    private final LinkedList<UUID> playersInQueue;

    public GreetSession(RedisModule redisModule, String name, String displayName, int meetTime, int sessionTime) {
        this.redisModule = redisModule;
        this.name = name;
        this.displayName = displayName;
        this.meetTime = meetTime;
        this.sessionTime = sessionTime;
        this.endsTime = -1;
        this.greetSpots = new LinkedList<>();
        this.playersInQueue = new LinkedList<>();
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.endsTime = this.startTime + ((long) this.sessionTime * 60 * 1000);
        this.redisModule.publishMessage(RedisChannel.GLOBAL, new ServerAnnounceMeetState(this.name, true, this.endsTime));
    }

    public void addGreetSpot(double x, double y, double z) {
        this.greetSpots.add(new GreetSpot(redisModule, x, y, z));
    }

    public void progressQueue() {
        for (int i = 0; i < this.greetSpots.size(); i++) {
            final GreetSpot current = this.greetSpots.get(i);
            log.info("I am Spot {}, current is {} and past is {}", i, current.getCurrentUser() == null ? "null" : current.getCurrentUser(),
                    current.getPastUser() == null ? "null" : current.getPastUser());

            if (i == 0) {
                current.updateCurrentUser(this.playersInQueue.poll());
                if (current.getCurrentUser() != null) {
                    log.info("GreetSpot {} updated with user {}", i, current.getCurrentUser());
                    redisModule.publishMessage(RedisChannel.GLOBAL,
                            new PlayerMoveQueueMessage(name, current.getCurrentUser(), false, i + 1, this.meetTime,
                                    new PlayerMoveQueueMessage.Location(current.getX(), current.getY(), current.getZ())));
                } else {
                    log.info("GreetSpot {} updated with null!", i);
                }
            }

            if (this.greetSpots.size() > i + 1) {
                final GreetSpot next = this.greetSpots.get(i + 1);
                next.updateCurrentUser(current.getPastUser());
                log.info("Updating Greet Spot {} with user {}", i + 1, current.getPastUser() == null ? "null" : current.getPastUser());
                if (current.getPastUser() != null) {
                    redisModule.publishMessage(RedisChannel.GLOBAL,
                            new PlayerMoveQueueMessage(name, current.getPastUser(), false, i + 2, this.meetTime,
                                    new PlayerMoveQueueMessage.Location(next.getX(), next.getY(), next.getZ())));
                    current.setPastUser(null);
                }
            } else if (current.getPastUser() != null) {
                log.info("Kicking {} from the queue, as they are at the end.", current.getPastUser());
                redisModule.publishMessage(RedisChannel.GLOBAL,
                        new PlayerMoveQueueMessage(name, current.getPastUser(), false, -1, 0, null));
                current.setPastUser(null);
            }
        }

        for (int i = 0; i < this.playersInQueue.size(); i++) {
            redisModule.publishMessage(RedisChannel.GLOBAL,
                    new PlayerMoveQueueMessage(name, this.playersInQueue.get(i), true, i + 1,
                            (i + 1) * this.meetTime, null));
        }
    }

    public boolean addPlayer(UUID uuid) {
        if (this.isPlayerInSession(uuid))
            return false;
        this.playersInQueue.add(uuid);
        return true;
    }

    public boolean isPlayerInSession(UUID uuid) {
        if (this.playersInQueue.contains(uuid))
            return true;
        for (GreetSpot greetSpot : this.greetSpots)
            if (greetSpot.getCurrentUser() == uuid)
                return true;
        return false;
    }

    public int getEta(UUID uuid) {
        int pos = this.getQueuePosition(uuid);
        return (pos + 1) * this.meetTime;
    }

    public int getQueuePosition(UUID uuid) {
        if (!this.playersInQueue.contains(uuid))
            return -1;
        return this.playersInQueue.indexOf(uuid);
    }

    public boolean removePlayer(UUID uuid) {
        if (this.playersInQueue.contains(uuid)) {
            this.playersInQueue.remove(uuid);
            return true;
        }

        for (GreetSpot greetSpot : this.greetSpots) {
            if (greetSpot.getCurrentUser().equals(uuid)) {
                greetSpot.kickCurrentUser();
                return true;
            }
        }
        return false;
    }

    public void pause() {
        this.pauseTime = System.currentTimeMillis();
    }

    public void resume() {
        long timeLeftAtPause = this.endsTime - this.pauseTime;
        this.endsTime = System.currentTimeMillis() + timeLeftAtPause;
        this.pauseTime = 0;
    }

    public void updateMeetTime(int time) {
        int oldMeetTime = this.meetTime;
        this.meetTime = time;

        long lastReached = this.nextTrigger - (oldMeetTime * 60L * 1000L);
        this.nextTrigger = lastReached + (time * 60L * 1000L);
    }

    public void updateSessionTime(int time) {
        this.endsTime = System.currentTimeMillis() + (time * 60L * 1000L);
    }
}
