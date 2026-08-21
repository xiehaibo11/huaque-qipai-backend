-- Additive normalization after V41: preserve the recovered protocol order for the
-- exact supported BOX/30109 combinations.  Counter radio precedes FengDing;
-- basescore immediately precedes the terminal RoomFee.
WITH supported AS (
    SELECT id,
           CASE
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
           END AS max_quan_rule,
           regexp_replace(
               regexp_replace(
                   game_rule,
                   'maxQuanShu[[:space:]]*=[[:space:]]*''[^'']*'';',
                   '',
                   'g'),
               'basescore[[:space:]]*=[[:space:]]*''[^'']*'';',
               '',
               'g') AS stripped_rule
    FROM game_rooms
    WHERE venue = 'BOX'
      AND game_id = 30109
      AND pay_type IN ('ALL', 'AA')
      AND (
          (player_count = 4 AND play_count IN (2, 4))
          OR (player_count = 2 AND play_count IN (4, 8, 16))
      )
), with_counter AS (
    SELECT id,
           CASE
               WHEN position('FengDing=' IN stripped_rule) > 0
                   THEN overlay(
                       stripped_rule PLACING max_quan_rule
                       FROM position('FengDing=' IN stripped_rule) FOR 0)
               WHEN position('PayType=' IN stripped_rule) > 0
                   THEN overlay(
                       stripped_rule PLACING max_quan_rule
                       FROM position('PayType=' IN stripped_rule) FOR 0)
               WHEN position('IsSysTrust=' IN stripped_rule) > 0
                   THEN overlay(
                       stripped_rule PLACING max_quan_rule
                       FROM position('IsSysTrust=' IN stripped_rule) FOR 0)
               WHEN position('RoomFee=' IN stripped_rule) > 0
                   THEN overlay(
                       stripped_rule PLACING max_quan_rule
                       FROM position('RoomFee=' IN stripped_rule) FOR 0)
               ELSE stripped_rule || max_quan_rule
           END AS counter_rule
    FROM supported
), canonical AS (
    SELECT id,
           CASE
               WHEN position('RoomFee=' IN counter_rule) > 0
                   THEN overlay(
                       counter_rule PLACING 'basescore=''1'';'
                       FROM position('RoomFee=' IN counter_rule) FOR 0)
               ELSE counter_rule || 'basescore=''1'';'
           END AS game_rule
    FROM with_counter
)
UPDATE game_rooms AS room
SET game_rule = canonical.game_rule
FROM canonical
WHERE room.id = canonical.id;

-- V40 established that QA GOLD rooms count rounds, not BOX circles.  Repair only
-- the known self-built QA rows that may retain a pre-fix nonblank "N圈" display;
-- do not apply any BOX protocol, room-mode, or room-card repair here.
UPDATE game_rooms
SET game_rule_display = replace(
        game_rule_display,
        '/' || play_count::text || '圈',
        '/' || play_count::text || '局')
WHERE venue = 'GOLD'
  AND game_id = 30109
  AND creation_request_hash = 'qa-gold-match'
  AND count_unit = 'ROUND'
  AND game_rule_display LIKE '%/' || play_count::text || '圈%';
