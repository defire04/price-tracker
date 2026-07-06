--liquibase formatted sql

--changeset tracker:003-add-drop-threshold
ALTER TABLE tracked_items ADD COLUMN drop_threshold NUMERIC(5, 2);
