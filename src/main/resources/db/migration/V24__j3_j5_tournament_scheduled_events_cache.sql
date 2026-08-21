alter table provider_response_cache
    drop constraint ck_provider_response_cache_scope;

alter table provider_response_cache
    add constraint ck_provider_response_cache_scope
        check (
            provider = 'SOFASCORE'
            and logical_endpoint in (
                'SCHEDULED_EVENTS',
                'EVENT_DETAILS',
                'TOURNAMENT_SCHEDULED_EVENTS'
            )
        );

comment on table provider_response_cache is
    'Parsed-response cache checkpoints for guarded local SofaScore requests. Raw bytes remain exclusively in provider_snapshot.';
