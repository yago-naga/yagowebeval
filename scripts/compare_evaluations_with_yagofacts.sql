
set enable_seqscan=false;

-------------------------
-- Uncomment one of the following blocks, depending on what you want to do
-------------------------

-------------------------
-- Search all evaluated facts, where corresponding YAGO fact changed subject, predicate or object
-- Note that there is no recursive comparison (e.g. even when an event changed, <occursSince> and <occursUntil> will not automatically be included)
-------------------------
--drop table eval_dangling;
--select e.*
--into eval_dangling
--from evaluation e
--where not exists (
--	select * from yagofacts y
--	where e.factid = y.id
--	and e.subject = y.subject
--	and e.predicate = y.predicate
--	and e.object = y.object);

-------------------------
-- Show information in evaluation table and yagofacts side by side
-------------------------
--select 
--	e.subject as "esubj", e.predicate as "epred", e.object as "eobj", e.eval,
--	y.subject as "ysubj", y.predicate as "ypred", y.object as "yobj"
--from eval_dangling e
--left outer join yagofacts y
--on e.subject = y.subject
--and e.predicate = y.predicate
--order by esubj, epred, eobj, yobj;

-------------------------
-- Remove evaluations, where corresponding yagofacts have changed in the meantime
-------------------------
--delete from evaluation e
--where exists
--( select *
--	from eval_dangling d
--	where e.timepoint = d.timepoint);
