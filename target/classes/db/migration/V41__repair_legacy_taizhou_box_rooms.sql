-- Repair 30109 BOX rooms created before the evidence-backed protocol catalog update.
-- Persisted play_count is the visible count: 4-player values are circles (2/4),
-- while 2-player values are rounds (4/8/16). Unsupported and GOLD rows stay untouched.
UPDATE game_rooms
SET game_rule = game_rule
        || CASE
            WHEN game_rule !~ '(^|;)[[:space:]]*maxQuanShu[[:space:]]*='
                THEN CASE
                    WHEN player_count = 4 AND play_count = 2
                        THEN 'maxQuanShu=''2'';'
                    WHEN player_count = 4 AND play_count = 4
                        THEN 'maxQuanShu=''4'';'
                    WHEN player_count = 2 AND play_count = 4
                        THEN 'maxQuanShu=''2'';'
                    WHEN player_count = 2 AND play_count = 8
                        THEN 'maxQuanShu=''4'';'
                    WHEN player_count = 2 AND play_count = 16
                        THEN 'maxQuanShu=''8'';'
                END
            ELSE ''
        END
        || CASE
            WHEN game_rule !~ '(^|;)[[:space:]]*basescore[[:space:]]*='
                THEN 'basescore=''1'';'
            ELSE ''
        END,
    room_mode = CASE
        WHEN player_count = 4 AND play_count = 2 AND pay_type = 'ALL' THEN 1
        WHEN player_count = 4 AND play_count = 2 AND pay_type = 'AA' THEN 5
        WHEN player_count = 4 AND play_count = 4 AND pay_type = 'ALL' THEN 2
        WHEN player_count = 4 AND play_count = 4 AND pay_type = 'AA' THEN 6
        WHEN player_count = 2 AND play_count = 4 AND pay_type = 'ALL' THEN 3
        WHEN player_count = 2 AND play_count = 4 AND pay_type = 'AA' THEN 7
        WHEN player_count = 2 AND play_count = 8 AND pay_type = 'ALL' THEN 1
        WHEN player_count = 2 AND play_count = 8 AND pay_type = 'AA' THEN 5
        WHEN player_count = 2 AND play_count = 16 AND pay_type = 'ALL' THEN 2
        WHEN player_count = 2 AND play_count = 16 AND pay_type = 'AA' THEN 6
    END
WHERE venue = 'BOX'
  AND game_id = 30109
  AND pay_type IN ('ALL', 'AA')
  AND (
      (player_count = 4 AND play_count IN (2, 4))
      OR (player_count = 2 AND play_count IN (4, 8, 16))
  );

-- Keep persisted display text aligned with TaizhouMahjongRuleDisplay for repaired rows.
UPDATE game_rooms
SET game_rule_display = concat_ws(
        '/',
        CASE
            WHEN game_rule ~ '(^|;)winLostType=''1'';' THEN '不平搓'
            WHEN game_rule ~ '(^|;)winLostType=''2'';' THEN '平搓'
        END,
        CASE WHEN game_rule ~ '(^|;)forceGPS=''1'';' THEN '防作弊' END,
        CASE
            WHEN game_rule ~ '(^|;)liaoDaZiBaoPai=''1'';' THEN '撩搭子包牌'
        END,
        CASE WHEN game_rule ~ '(^|;)lianZhuang=''1'';' THEN '连庄' END,
        CASE
            WHEN game_rule ~ '(^|;)duiDuiHuFourScore=''1'';' THEN '对对胡4胡'
        END,
        CASE
            WHEN game_rule ~ '(^|;)noShengPaiJieDuan=''1'';' THEN '无生牌阶段'
        END,
        CASE WHEN game_rule ~ '(^|;)buSiBao=''1'';' THEN '不死包' END,
        CASE
            WHEN game_rule ~ '(^|;)DelColor=''1'';' THEN '缺一色'
            WHEN game_rule ~ '(^|;)DelColor=''2'';' THEN '缺二色'
        END,
        CASE
            WHEN game_rule ~ '(^|;)FengDing=''0'';' THEN '不封顶'
            WHEN game_rule ~ '(^|;)FengDing=''60'';' THEN '60封顶'
            WHEN game_rule ~ '(^|;)FengDing=''80'';' THEN '80封顶'
        END,
        CASE
            WHEN game_rule ~ '(^|;)PayType=''(1|7)'';' THEN '平摊消耗'
            WHEN game_rule ~ '(^|;)PayType=''0'';' THEN '房主消耗'
            WHEN pay_type = 'AA' THEN '平摊消耗'
            ELSE '房主消耗'
        END,
        player_count::text || '人',
        CASE
            WHEN substring(game_rule FROM 'basescore=''([^'']+)'';') IS NOT NULL
                THEN '底分'
                    || substring(game_rule FROM 'basescore=''([^'']+)'';')
        END,
        play_count::text || CASE WHEN player_count = 2 THEN '局' ELSE '圈' END,
        CASE
            WHEN COALESCE(
                    substring(game_rule FROM 'IsSysTrust=''([0-9]+)'';'),
                    '0')::integer > 0
                THEN '超时'
                    || substring(game_rule FROM 'IsSysTrust=''([0-9]+)'';')
                    || '秒托管'
        END
    )
WHERE venue = 'BOX'
  AND game_id = 30109
  AND pay_type IN ('ALL', 'AA')
  AND (
      (player_count = 4 AND play_count IN (2, 4))
      OR (player_count = 2 AND play_count IN (4, 8, 16))
  );
