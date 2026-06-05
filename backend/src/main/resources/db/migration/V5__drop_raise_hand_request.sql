-- Raise-hand queue moved to Redis (ephemeral). Drop legacy MySQL table.
DROP TABLE IF EXISTS raise_hand_request;
