package ma.luan.yiyan.service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import ma.luan.yiyan.constants.Key;
import ma.luan.yiyan.util.OptionsUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class LogService extends AbstractVerticle {
    private RedisClient redisClient;
    private Logger log = LogManager.getLogger(this.getClass());

    @Override
    public void start(Future<Void> startFuture) {
        vertx.eventBus().consumer(Key.SET_HISTORY_TO_REDIS, this::setHistoryToRedis);
        vertx.eventBus().consumer(Key.GET_HISTORY_FROM_REDIS, this::getHistoryFromRedis);
        redisClient = RedisClient.create(vertx, OptionsUtil.getRedisOptions(config()));
        startFuture.complete();
    }

    private void setHistoryToRedis(Message<JsonObject> message) {
        redisClient.hincrby(Key.REDIS_CLICKS_HISTORY_HASH, LocalDate.now().toString(), 1, res -> {
            if (res.failed()) {
                log.error("Fail to get data from Redis", res.cause());
            }
        });
        redisClient.hincrby(Key.REDIS_CLICKS_TOTAL_HASH, "total", 1, res -> {
            if (res.failed()) {
                log.error("Fail to get data from Redis", res.cause());
            }
        });
    }

    private void getHistoryFromRedis(Message<JsonObject> message) {
        Future<String> total = Future.future(f -> redisClient.hget(Key.REDIS_CLICKS_TOTAL_HASH, "total", f));
        // 7天的历史点击量
        LocalDate localDate = LocalDate.now();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            keys.add(localDate.toString());
            localDate = localDate.minusDays(1);
        }
        Future<JsonArray> history = Future.future(f -> redisClient.hmget(Key.REDIS_CLICKS_HISTORY_HASH, keys, f));
        CompositeFuture.all(Arrays.asList(total, history)).setHandler(v -> {
            if (v.succeeded()) {
                JsonObject result = new JsonObject();
                result.put("总点击量", total.result());
                result.put("最近七天点击量", history.result());
                message.reply(result);
            } else {
                log.error("日志获取异常", v.cause());
                message.fail(500, v.cause().getMessage());
            }
        });
    }


}
