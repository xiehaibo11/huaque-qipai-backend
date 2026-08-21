package com.nanbei.entertainment.backend.timeloginact.application;

import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.RewardItem;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.SlotView;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.StateResponse;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.WheelView;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginSlotEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginWheelSliceEntity;
import java.util.ArrayList;
import java.util.List;

/** 把权威日状态投影成客户端响应。这里只做映射，不做任何判定。 */
final class TimeLoginStateAssembler {
    /** 原版转盘固定八格（`_KW_ITEM_1.._KW_ITEM_8`），不足或多于八格都是配置错误。 */
    static final int WHEEL_SLICE_COUNT = 8;

    private TimeLoginStateAssembler() {}

    static StateResponse state(
            TimeLoginDayState day,
            List<TimeLoginWheelSliceEntity> slices,
            ShopWalletResponse wallet,
            long serverTimeEpochSecond) {
        List<SlotView> slots = new ArrayList<>(day.orderedSlots().size());
        for (int index = 0; index < day.orderedSlots().size(); index++) {
            TimeLoginSlotEntity slot = day.orderedSlots().get(index);
            slots.add(
                    new SlotView(
                            slot.getId().toString(),
                            slot.getStartSecond(),
                            slot.getEndSecond(),
                            day.statusAt(index).wireValue(),
                            List.of(
                                    new RewardItem(
                                            slot.getRewardType(),
                                            slot.getRewardAmount(),
                                            slot.getRewardName()))));
        }
        return new StateResponse(
                day.activity().getActivityCode(),
                day.activity().getGoldOver(),
                day.activity().getSupplementCount(),
                day.daySecond(),
                serverTimeEpochSecond,
                slots,
                wheel(day, slices),
                wallet);
    }

    private static WheelView wheel(TimeLoginDayState day, List<TimeLoginWheelSliceEntity> slices) {
        if (slices.size() != WHEEL_SLICE_COUNT) {
            return null;
        }
        List<RewardItem> props = new ArrayList<>(WHEEL_SLICE_COUNT);
        for (TimeLoginWheelSliceEntity slice : slices) {
            props.add(
                    new RewardItem(
                            slice.getRewardType(), slice.getRewardAmount(), slice.getRewardName()));
        }
        return new WheelView(
                day.activity().getId().toString(),
                day.wheelProgress(),
                day.activity().getWheelUnlockCount(),
                props);
    }
}
