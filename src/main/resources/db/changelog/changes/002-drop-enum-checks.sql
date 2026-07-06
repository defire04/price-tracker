--liquibase formatted sql

-- Hibernate ddl-auto=update generated CHECK constraints for @Enumerated(STRING) columns
-- at table creation and never extended them when the Java enums grew (broke on ANSWEAR).
-- Enum validity is enforced in code; the checks only caused failures on every new Site.

--changeset tracker:002-drop-enum-checks
ALTER TABLE tracked_items DROP CONSTRAINT IF EXISTS tracked_items_site_check;
ALTER TABLE tracked_item_events DROP CONSTRAINT IF EXISTS tracked_item_events_event_type_check;
