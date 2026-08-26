update game_rule_documents gold
set blocks = reviewed.blocks,
    source_note = reviewed.source_note,
    updated_at = now()
from game_rule_documents reviewed
where gold.game_id = 30400
  and reviewed.game_id = 30109;
