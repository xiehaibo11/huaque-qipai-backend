package com.nanbei.entertainment.backend.timeloginact.application;

import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginWheelSliceEntity;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * 按权重选出中奖格。概率只存在于服务端，既不下发也不由客户端参与；原版
 * {@code Module.lua:262-275} 也只是拿服务端返回的奖励反查格子索引。
 */
final class TimeLoginWheelPicker {
    private TimeLoginWheelPicker() {}

    static TimeLoginWheelSliceEntity pick(
            List<TimeLoginWheelSliceEntity> slices, IntUnaryOperator randomBound) {
        int total = 0;
        for (TimeLoginWheelSliceEntity slice : slices) {
            total += slice.getWeight();
        }
        if (total <= 0) {
            throw new IllegalStateException("Time login wheel weights must be positive");
        }
        int roll = randomBound.applyAsInt(total);
        int cursor = 0;
        for (TimeLoginWheelSliceEntity slice : slices) {
            cursor += slice.getWeight();
            if (roll < cursor) {
                return slice;
            }
        }
        return slices.get(slices.size() - 1);
    }
}
