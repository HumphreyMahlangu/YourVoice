package io.github.humphreymahlangu.yourvoice.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit trust for legacy security/tally fixtures only. New verification tests never call this helper. */
public final class TrustedTestProfiles {
    private TrustedTestProfiles() { }
    public static void verify(JdbcTemplate jdbc) {
        jdbc.update("""
            insert into geographic_provinces(id,name,normalised_name)
            select gen_random_uuid(), min(trim(province)), lower(trim(province)) from voting_districts group by lower(trim(province))
            on conflict(normalised_name) do nothing
            """);
        jdbc.update("""
            insert into geographic_municipalities(id,province_id,name,normalised_name)
            select gen_random_uuid(), p.id, min(trim(d.municipality)), lower(trim(d.municipality)) from voting_districts d
            join geographic_provinces p on p.normalised_name = lower(trim(d.province)) group by p.id, lower(trim(d.municipality))
            on conflict(province_id,normalised_name) do nothing
            """);
        jdbc.update("""
            insert into simulation_voter_records(id,reference,id_number_hash,full_name,identity_status,location_status,province_id,municipality_id,district_id,reason,recorded_at)
            select gen_random_uuid(), 'SIM-SECURITY-' || upper(replace(v.id::text,'-','')),v.id_number_hash,v.full_name,'VERIFIED','VERIFIED',p.id,m.id,d.id,'Explicit fictional security fixture',now()
            from voter_profiles v join voting_districts d on d.id=v.voting_district_id
            join geographic_provinces p on p.normalised_name=lower(trim(d.province))
            join geographic_municipalities m on m.province_id=p.id and m.normalised_name=lower(trim(d.municipality))
            on conflict(reference) do nothing
            """);
        jdbc.update("""
            insert into voter_verifications(id,voter_profile_id,identity_status,location_status,established,source_reference,province_id,municipality_id,district_id,message)
            select gen_random_uuid(), v.id, 'VERIFIED','VERIFIED',true,'SIM-SECURITY-FIXTURE',p.id,m.id,d.id,'Explicit fictional security fixture'
            from voter_profiles v join voting_districts d on d.id = v.voting_district_id
            join geographic_provinces p on p.normalised_name = lower(trim(d.province))
            join geographic_municipalities m on m.province_id = p.id and m.normalised_name = lower(trim(d.municipality))
            on conflict(voter_profile_id) do nothing
            """);
        jdbc.update("""
            update election_registrations r set identity_verified=true,location_verified=true,source_reference='SIM-SECURITY-FIXTURE',
            registered_province_id=p.id,registered_municipality_id=m.id,registered_province=d.province,registered_municipality=d.municipality,registered_ward_number=d.ward_number
            from voting_districts d join geographic_provinces p on p.normalised_name=lower(trim(d.province))
            join geographic_municipalities m on m.province_id=p.id and m.normalised_name=lower(trim(d.municipality))
            where r.voting_district_id=d.id and r.identity_verified=false
            """);
    }
}
