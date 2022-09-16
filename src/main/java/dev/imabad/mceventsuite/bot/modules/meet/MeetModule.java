package dev.imabad.mceventsuite.bot.modules.meet;

import dev.imabad.mceventsuite.core.EventCore;
import dev.imabad.mceventsuite.core.api.modules.Module;
import dev.imabad.mceventsuite.core.modules.redis.RedisChannel;
import dev.imabad.mceventsuite.core.modules.redis.RedisMessageListener;
import dev.imabad.mceventsuite.core.modules.redis.RedisModule;
import dev.imabad.mceventsuite.core.modules.redis.RedisRequestListener;
import dev.imabad.mceventsuite.core.modules.redis.messages.BooleanResponse;
import dev.imabad.mceventsuite.core.modules.redis.messages.meet.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MeetModule extends Module {

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private RedisModule redisModule;
    private Map<String, GreetSession> sessionMap;

    @Override
    public String getName() {
        return "meet";
    }

    @Override
    public void onEnable() {
        this.redisModule = EventCore.getInstance().getModuleRegistry().getModule(RedisModule.class);
        this.sessionMap = new HashMap<>();

        this.redisModule.registerRequestListener(AdminCreateSessionRequest.class, new RedisRequestListener<>((request) -> {
            if(this.sessionMap.containsKey(request.getName()))
                return new BooleanResponse(false);
            final GreetSession greetSession = new GreetSession(
                    this.redisModule,
                    request.getName(),
                    request.getDisplayName(),
                    request.getMeetTime(),
                    request.getSessionTime());
            this.sessionMap.put(request.getName(), greetSession);
            return new BooleanResponse(true);
        }));

        this.redisModule.registerRequestListener(AdminCreateSpotMessage.class, new RedisRequestListener<>((request) -> {
            if(!this.sessionMap.containsKey(request.getName()))
                return new BooleanResponse(false);
            this.sessionMap.get(request.getName()).addGreetSpot(request.getX(), request.getY(), request.getZ());
            return new BooleanResponse(true);
        }));

        this.redisModule.registerRequestListener(AdminStartSessionRequest.class, new RedisRequestListener<>((request) -> {
            if(!this.sessionMap.containsKey(request.getName()))
                return new BooleanResponse(false);
            final GreetSession session = this.sessionMap.get(request.getName());
            if(session.getEndsTime() != -1)
                return new BooleanResponse(false);
            session.start();
            return new BooleanResponse(true);
        }));

        this.redisModule.registerRequestListener(AdminMoveAlongSessionRequest.class, new RedisRequestListener<>((request) -> {
            if(!this.sessionMap.containsKey(request.getName()))
                return new BooleanResponse(false);
            final GreetSession session = this.sessionMap.get(request.getName());
            if(session.getEndsTime() == -1)
                return new BooleanResponse(false);
            session.setForceProgression(true);
            return new BooleanResponse(true);
        }));

        this.redisModule.registerRequestListener(PlayerJoinQueueRequest.class, new RedisRequestListener<>((request) -> {
            final String name = request.getName();
            final UUID uuid = request.getUuid();

            if(!sessionMap.containsKey(name))
                return new PlayerJoinQueueResponse(PlayerJoinQueueResponse.Failure.NO_SUCH_QUEUE);

            for(GreetSession session : sessionMap.values()) {
                if(session.isPlayerInSession(uuid))
                    return new PlayerJoinQueueResponse(PlayerJoinQueueResponse.Failure.IN_OTHER_QUEUE, session.getName());
            }

            final GreetSession session = sessionMap.get(name);
            if(session.addPlayer(uuid)) {
                final boolean isFull = (System.currentTimeMillis() + ((long) session.getEta(uuid) * 60 * 1000)) >= session.getEndsTime();
                return new PlayerJoinQueueResponse(isFull, session.getEta(uuid) + ":" + (session.getQueuePosition(uuid) + 1));
            } else {
                return new PlayerJoinQueueResponse(PlayerJoinQueueResponse.Failure.ALREADY_IN_QUEUE);
            }
        }));

        this.redisModule.registerRequestListener(PlayerLeaveQueueRequest.class, new RedisRequestListener<>((request) -> {
            for(GreetSession greetSession : this.sessionMap.values())
                if(greetSession.removePlayer(request.getUuid()))
                    return new BooleanResponse(true);
            return new BooleanResponse(false);
        }));

        executorService.scheduleWithFixedDelay(() -> {
            final long currentTime = System.currentTimeMillis();
            for(final GreetSession session : this.sessionMap.values()) {
                if(session.getEndsTime() <= 0)
                    continue;
                if(System.currentTimeMillis() > session.getEndsTime()) {
                    this.redisModule.publishMessage(RedisChannel.GLOBAL,
                            new ServerAnnounceMeetState(session.getName(), false, 0));
                    this.sessionMap.remove(session.getName());
                    continue;
                }
                if(System.currentTimeMillis() > session.getNextTrigger() || session.isForceProgression()) {
                    session.progressQueue();
                    session.setForceProgression(false);
                    session.setNextTrigger(currentTime + (session.getMeetTime() * 60L * 1000L));
                    continue;
                }

                for(final GreetSpot greetSpot : session.getGreetSpots()) {
                    if(greetSpot.getCurrentUser() != null) {
                        final long timeLeft = (greetSpot.getTimeReached() + session.getMeetTime()) - System.currentTimeMillis();
                        final int seconds = (int) (timeLeft / 1000);
                        if(seconds % 30 == 0) {
                            final int minutes = seconds / 60;
                            String timeString = seconds + " seconds";
                            if(minutes > 0) {
                                final int secondsOn = seconds - (minutes * 60);
                                timeString = minutes + " minute" + (minutes > 1 ? "s " : " ");
                                if(secondsOn > 0)
                                    timeString += secondsOn + " second" +
                                            (secondsOn > 1 ? "s" : "");
                            }
                            this.redisModule.publishMessage(RedisChannel.GLOBAL,
                                    new PlayerTimeReminderMessage(greetSpot.getCurrentUser(), timeString));
                        }
                    }
                }
            }
        }, 5L, 10L, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {

    }

    @Override
    public List<Class<? extends Module>> getDependencies() {
        return null;
    }
}
