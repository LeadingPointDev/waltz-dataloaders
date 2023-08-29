package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.helper.LoggingUtilities;
import org.finos.waltz_util.common.model.ApplicationKind;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.Person;
import org.finos.waltz_util.schema.tables.records.ApplicationRecord;
import org.finos.waltz_util.schema.tables.records.PersonRecord;
import org.jooq.*;
import org.jooq.Record;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.APPLICATION;
import static org.finos.waltz_util.schema.Tables.ORGANISATIONAL_UNIT;

import static org.finos.waltz_util.schema.Tables.PERSON;


public class PersonLoader {
    /**
     * Plan is:
     * 1. Load new people.
     * 2. Load DB data again, and do new comparison with email : ID comparisons
     * 3. Set Is_Removed for people who are not in the new data.
     *
     */

    String resource;
    Long ORPHAN_ORG_UNIT_ID = 150L;
    DSLContext dsl;

    public PersonLoader(String resource){
        this.resource = resource;

        AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(DIBaseConfiguration.class);

        dsl = springContext.getBean(DSLContext.class);
    }

    public void update(){
        InsertNew();
        updateRelationships();
    }

    private void InsertNew(){
        /**
         * 1. Get Current People from DB
         * 2. Get New people from JSON
         * 3. Compare emails (unique employee id if possible) todo: check for UUIDs with thushan
         * 4. insert new entries where no email match
         *
         */

        dsl.transaction(ctx ->{
            DSLContext tx = ctx.dsl();

            Map<String, Long> OrgIdByOrgExtId = tx
                    .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                    .from(ORGANISATIONAL_UNIT)
                    .fetchMap(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID);





            Set<PersonOverview> existingPeople = tx
                    .select(PERSON.fields())
                    .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                    .from(PERSON)
                    .innerJoin(ORGANISATIONAL_UNIT)
                    .on(ORGANISATIONAL_UNIT.ID.eq(PERSON.ORGANISATIONAL_UNIT_ID))
                    .fetch()
                    .stream()
                    .map(r -> toDomain(r))
                    .collect(Collectors.toSet());

            PersonOverview[] rawOverviews = getJsonMapper().readValue(Main.class.getClassLoader().getResourceAsStream("person.json"), PersonOverview[].class);
            Set<PersonOverview> desiredPeople = Stream
                    .of(rawOverviews)
                            .map(d -> ImmutablePersonOverview
                                    .copyOf(d)
                                    .withOrganisationalUnitId(OrgIdByOrgExtId.getOrDefault(d.organisationalUnitExternalId().toString(), ORPHAN_ORG_UNIT_ID)))


                                    .collect(Collectors.toSet());



            DiffResult<PersonOverview> diff = DiffResult.mkDiff(
                    existingPeople,
                    desiredPeople,
                    PersonOverview::employee_id,
                    Object::equals);

            Collection<PersonOverview> toInsert = diff.otherOnly();

            List<PersonRecord> recordsToInsert = toInsert
                    .stream()
                    .map(p -> {
                        PersonRecord record = toJooqRecord(tx, p);
                        record.changed(PERSON.ID, false);
                        return record;})
                    .collect(Collectors.toList());

            int recordsCreated = summarizeResults(tx
                    .batchInsert(recordsToInsert)
                    .execute());

            System.out.println("Records Created: " + recordsCreated);
            if (recordsCreated != 0){
                System.out.println("Records Created: " + recordsCreated);
            }
        });



    }

    private void updateRelationships(){
        /**
         * for all entries, compare with JSON
         * todo finish this bit
         */

        dsl.transaction(ctx -> {
            DSLContext tx = ctx.dsl();
            /**
             * 1. get email -> manager ID relationships from DB
             * 2. load new people from DB to create Existing PersonOverviews
             * 3. load new people from JSON to create Desired PersonOverviews
             * 4. compare the two sets of PersonOverviews
             * 5. update IntersectingDifferent entries
             * 6. set is_removed for rest
             */

            Map<String, Long> OrgIdByOrgExtId = tx
                    .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                    .from(ORGANISATIONAL_UNIT)
                    .fetchMap(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID);


            Map<String, String> emailToEmployeeID = tx
                    .select(PERSON.EMAIL, PERSON.EMPLOYEE_ID)
                    .from(PERSON)
                    .fetchMap(PERSON.EMAIL, PERSON.EMPLOYEE_ID);


            Set<PersonOverview> existingPeople = tx
                    .select(PERSON.fields())
                    .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                    .from(PERSON)
                    .innerJoin(ORGANISATIONAL_UNIT)
                    .on(ORGANISATIONAL_UNIT.ID.eq(PERSON.ORGANISATIONAL_UNIT_ID))
                    .fetch()
                    .stream()
                    .map(r -> toDomain(r))
                    .collect(Collectors.toSet());

            PersonOverview[] rawOverviews = getJsonMapper().readValue(Main.class.getClassLoader().getResourceAsStream("person.json"), PersonOverview[].class);
            Set<PersonOverview> desiredPeople = Stream
                    .of(rawOverviews)
                    .map(d -> {
                                //???
                                ImmutablePersonOverview personOverview = ImmutablePersonOverview.copyOf(d)
                                        .withOrganisationalUnitId(OrgIdByOrgExtId.getOrDefault(d.organisationalUnitExternalId().toString(), ORPHAN_ORG_UNIT_ID))
                                        .withManagerEmployeeId(emailToEmployeeID.getOrDefault(d.managerEmail().get(), "0"));


                                return personOverview;
                            }
                    ).collect(Collectors.toSet());

            DiffResult<PersonOverview> diff = DiffResult.mkDiff(
                    existingPeople,
                    desiredPeople,
                    PersonOverview::employee_id,
                    Object::equals);


            System.out.println("Diff: " + diff);
            Collection<PersonOverview> toUpdate = diff.differingIntersection();


            List<PersonRecord> recordsToUpdate = toUpdate
                    .stream()
                    .map(p -> {
                        PersonRecord record = toJooqRecord(tx, p);
                        record.changed(PERSON.ID, false);
                        return record;
                    })
                   .collect(Collectors.toList());

            int recordsUpdated;
            recordsUpdated = summarizeResults(tx
                    .batchUpdate(recordsToUpdate)
                    .execute());

            throw new IOException("test");




        });

    }



    private ImmutablePersonOverview toDomain(Record r){
        PersonRecord personRecord = r.into(PERSON);
        return ImmutablePersonOverview
                .builder()

                .employee_id(
                        personRecord.getEmployeeId())
                .email(
                        personRecord.getEmail())
                .displayName(
                        personRecord.getDisplayName())
                .kind(
                        personRecord.getKind())
                .managerEmployeeId(personRecord.getManagerEmployeeId()
                        )
                .title(
                        personRecord.getTitle())
                .departmentName(
                        Optional.ofNullable(personRecord.getDepartmentName()))
                .mobilePhone(
                        Optional.ofNullable(personRecord.getMobilePhone()))
                .officePhone(
                        Optional.ofNullable(personRecord.getOfficePhone()))
                .organisationalUnitId(
                        personRecord.getOrganisationalUnitId())
                .organisationalUnitExternalId(
                        r.get(ORGANISATIONAL_UNIT.EXTERNAL_ID))


                .build();
    }

    private PersonRecord toJooqRecord(DSLContext dsl, PersonOverview domain){
        PersonRecord record = dsl.newRecord(PERSON);
        record.setEmployeeId(domain.employee_id());
        record.setDisplayName(domain.displayName());
        record.setEmail(domain.email());
        record.setDepartmentName(domain.departmentName().orElse(null));
        record.setKind(domain.kind());
        // sets unmanaged people to 0 on every insert statement where its not specified, as it will be updated on second pass.
        record.setManagerEmployeeId(domain.managerEmployeeId().orElse("0"));
        record.setTitle(domain.title().orElse(null));
        record.setMobilePhone(domain.mobilePhone().orElse(null));
        record.setOfficePhone(domain.officePhone().orElse(null));
        record.setOrganisationalUnitId(domain.organisationalUnitId().orElse(null));
        return record;

    }

    public static int summarizeResults(int[] rcs) {
        return IntStream.of(rcs).sum();
    }





}
