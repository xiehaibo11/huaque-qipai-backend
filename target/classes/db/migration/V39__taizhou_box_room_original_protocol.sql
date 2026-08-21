-- 30109 values below are copied from the checked-in original client evidence:
-- CreateBoxRoom/Config.lua supplies the fixed base-score suffix, while
-- configure/900023/box/30109.json supplies the exact allCostT/aaCostT modes.
UPDATE room_rule_configs
SET config = jsonb_set(
        jsonb_set(
          jsonb_set(
            jsonb_set(
              jsonb_set(
                jsonb_set(
                  jsonb_set(
                    config,
                    '{trailingRule}',
                    to_jsonb('basescore=''1'';'::text),
                    true),
                  '{explicitRoomModes}',
                  'true'::jsonb,
                  true),
                '{categories,0,groups,2,lines,0,options,0,roomModes}',
                '{"ALL":{"0":1},"AA":{"0":5}}'::jsonb,
                true),
              '{categories,0,groups,2,lines,0,options,1,roomModes}',
              '{"ALL":{"0":2},"AA":{"0":6}}'::jsonb,
              true),
            '{categories,1,groups,2,lines,0,options,0,roomModes}',
            '{"ALL":{"0":3},"AA":{"0":7}}'::jsonb,
            true),
          '{categories,1,groups,2,lines,0,options,1,roomModes}',
          '{"ALL":{"0":1},"AA":{"0":5}}'::jsonb,
          true),
        '{categories,1,groups,2,lines,0,options,2,roomModes}',
        '{"ALL":{"0":2},"AA":{"0":6}}'::jsonb,
        true),
    updated_at = CURRENT_TIMESTAMP
WHERE lobby_id = 900023
  AND game_id = 30109;
