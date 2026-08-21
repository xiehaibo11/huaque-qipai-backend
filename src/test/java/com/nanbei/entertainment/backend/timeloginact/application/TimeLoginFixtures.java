package com.nanbei.entertainment.backend.timeloginact.application;

import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginActivityEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginSlotEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginWheelSliceEntity;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 配置类实体由 Flyway 种子与 JPA 装载，生产代码没有构造入口。测试用反射装配，
 * 避免为了测试在生产实体上开写接口。
 */
final class TimeLoginFixtures {
    private TimeLoginFixtures() {}

    static TimeLoginActivityEntity activity(
            UUID id, long goldOver, int supplementCount, int wheelUnlockCount) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("activityCode", "TIME_LOGIN_DAILY");
        values.put("enabled", true);
        values.put("goldOver", goldOver);
        values.put("supplementCount", supplementCount);
        values.put("wheelUnlockCount", wheelUnlockCount);
        values.put("dayBoundarySecond", 82800);
        values.put("createdAt", Instant.EPOCH);
        values.put("updatedAt", Instant.EPOCH);
        return build(TimeLoginActivityEntity.class, values);
    }

    static TimeLoginSlotEntity slot(
            UUID id, UUID activityId, int order, int startSecond, int endSecond, long amount) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("activityId", activityId);
        values.put("slotOrder", order);
        values.put("startSecond", startSecond);
        values.put("endSecond", endSecond);
        values.put("rewardType", "COIN");
        values.put("rewardAmount", amount);
        values.put("rewardName", "金币");
        return build(TimeLoginSlotEntity.class, values);
    }

    static TimeLoginWheelSliceEntity slice(
            UUID activityId, int index, String rewardType, long amount, int weight) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", UUID.randomUUID());
        values.put("activityId", activityId);
        values.put("sliceIndex", index);
        values.put("rewardType", rewardType);
        values.put("rewardAmount", amount);
        values.put("rewardName", "COIN".equals(rewardType) ? "金币" : "钻石");
        values.put("weight", weight);
        return build(TimeLoginWheelSliceEntity.class, values);
    }

    private static <T> T build(Class<T> type, Map<String, Object> values) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = constructor.newInstance();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Field field = type.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                field.set(instance, entry.getValue());
            }
            return instance;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to build " + type.getSimpleName(), exception);
        }
    }
}
