-- Foreign keys the baseline lacked. Orphan story views and pending flows are junk; remove them first.
-- Problem reports are support records, so they are kept: that key is NOT VALID (checks new rows only).
-- No ON DELETE on the user keys: deleting a user must first clean up these rows.
-- The story cascade only covers hard deletes; StoryService soft-deletes.
DELETE FROM public.story_views v
    WHERE NOT EXISTS (SELECT 1 FROM public.stories s WHERE s.id = v.story_id)
       OR NOT EXISTS (SELECT 1 FROM public.users u WHERE u.id = v.viewer_id);
DELETE FROM public.payment_onboarding p
    WHERE p.user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.users u WHERE u.id = p.user_id);

ALTER TABLE ONLY public.story_views
    ADD CONSTRAINT fk_story_views_story FOREIGN KEY (story_id) REFERENCES public.stories(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.story_views
    ADD CONSTRAINT fk_story_views_viewer FOREIGN KEY (viewer_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.problem_reports
    ADD CONSTRAINT fk_problem_reports_user FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID;
ALTER TABLE ONLY public.payment_onboarding
    ADD CONSTRAINT fk_payment_onboarding_user FOREIGN KEY (user_id) REFERENCES public.users(id);

-- story_views and problem_reports already have indexes on these columns (V1).
CREATE INDEX idx_payment_onboarding_user_id ON public.payment_onboarding USING btree (user_id);
