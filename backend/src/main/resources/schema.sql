ALTER TABLE IF EXISTS study_folder
    ADD COLUMN IF NOT EXISTS depth integer DEFAULT 1 NOT NULL;

ALTER TABLE IF EXISTS study_folder
    ADD COLUMN IF NOT EXISTS parent_id bigint;

ALTER TABLE IF EXISTS study_folder
    ADD CONSTRAINT IF NOT EXISTS fk_study_folder_parent
    FOREIGN KEY (parent_id) REFERENCES study_folder(id);

ALTER TABLE IF EXISTS study_file
    ADD COLUMN IF NOT EXISTS knowledge_enabled boolean DEFAULT true NOT NULL;

UPDATE study_file
SET knowledge_enabled = true
WHERE knowledge_enabled IS NULL;
