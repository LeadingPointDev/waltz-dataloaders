package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.schema.tables.records.PersonRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.ORGANISATIONAL_UNIT;
import static org.finos.waltz_util.schema.Tables.PERSON;


public class PersonLoader {
    /**
     * Plan is:
     * 1. Load new people.
     * 2. Load DB data again, and do new comparison with email : ID comparisons
     * 3. Set Is_Removed for people who are not in the new data.
     */

    private final static Long ORPHAN_ORG_UNIT_ID = -1L; // this is a constant, so should be declared as static and uppercased
    private final String resource;  // these are initialised at construction, and should not change therefore marked as final
    private final DSLContext dsl;

    public PersonLoader(String resource) {
        this.resource = resource;
        AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(DIBaseConfiguration.class);
        dsl = springContext.getBean(DSLContext.class);
    }


    // clearer method name
    public void synch() {

        // updates and insertions should be under a single tx
        dsl.transaction(ctx -> {
            DSLContext tx = ctx.dsl();


            // pulled out the common code into this method
            Map<String, Long> orgIdByOrgExtId = loadOrgIdByExtIdMap(tx);

            Set<PersonOverview> existingPeople = getExistingPeople(tx);

            Map<String, String> emailtoEmployeeID = EmailToEmployeeId();
            emailtoEmployeeID.putAll(mkEmailToEmployeeIdMap(existingPeople));



            // todo: cleanup and put overview processing inside function.

            Set<PersonOverview> desiredPeople = loadPeopleFromFile(orgIdByOrgExtId);
            //
            // take desiredPeople, and if their Employee ID matches, set thier ID to the map
            Map<String, Long> employeeIDtoID = EmployeeIDtoID(tx);
            desiredPeople = desiredPeople
                    .stream()
                    .map(p -> {
                        Long id = employeeIDtoID.get(p.employee_id());
                        return ImmutablePersonOverview.copyOf(p)
                                .withId(Optional.ofNullable(id))
                                .withManager_employee_id(p.manager_employee_id().orElse("0"));
                    })
                    .collect(Collectors.toSet());




            // only need to do the diff once
            DiffResult<PersonOverview> diff = DiffResult.mkDiff(
                    existingPeople,
                    desiredPeople,
                    PersonOverview::employee_id,
                    Object::equals);


            // then use the bits of the diff for the appropriate 'handler' methods
            insertNew(tx, diff.otherOnly());
            updateRelationships(tx, diff.differingIntersection());
            markRemoved(tx, diff.waltzOnly());


        });
    }



    private Set<PersonOverview> getExistingPeople(DSLContext tx) {
         return tx
                .select(PERSON.fields())
                .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                .from(PERSON)
                .innerJoin(ORGANISATIONAL_UNIT)
                .on(ORGANISATIONAL_UNIT.ID.eq(PERSON.ORGANISATIONAL_UNIT_ID))
                .fetch()
                .stream()
                .map(r -> toDomain(r))
                .collect(Collectors.toSet());
    }


    private void insertNew(DSLContext tx, Collection<PersonOverview> toInsert) throws IOException {



        /*
         * 1. Get Current People from DB
         * 2. Get New people from JSON
         * 3. Compare emails (unique employee id if possible) todo: check for UUIDs
         * 4. insert new entries where no email match
         */

        List<PersonRecord> recordsToInsert = toInsert
                .stream()
                .map(p -> {
                    PersonRecord record = toJooqRecord(tx, p);
                    record.changed(PERSON.ID, false);
                    return record;
                })
                .collect(Collectors.toList());


        int recordsCreated = summarizeResults(tx
                .batchInsert(recordsToInsert)
                .execute());

        System.out.printf("Created: %d records\n", recordsCreated);
    }


    private void updateRelationships(DSLContext tx,
                                     Collection<PersonOverview> toUpdate) {


        List<PersonRecord> recordsToUpdate = toUpdate
                .stream()
                .map(p -> {
                    PersonRecord record = toJooqRecord(tx, p);

                    record.changed(PERSON.ID, false);
                    return record;
                })
                .collect(Collectors.toList());


        int recordsUpdated = summarizeResults(tx   // no point splitting decl and assignment
                .batchUpdate(recordsToUpdate)
                .execute());

        System.out.printf("Updated: %d records\n", recordsUpdated);

    }

    private void markRemoved(DSLContext tx,
                             Collection<PersonOverview> toRemove) {
        Set<Long> toRemoveIDs = toRemove.stream()
                .map(PersonOverview::id)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        int numRemoved = tx
                .update(PERSON)
                .set(PERSON.IS_REMOVED, true)
                .where(PERSON.ID.in(toRemoveIDs))
                .execute();

        System.out.println("Removed: " + numRemoved + " records");


    }

    private Map<String, String> EmailToEmployeeId() throws IOException {
        //InputStream resourceAsStream = PersonLoader.class.getClassLoader().getResourceAsStream(resource);

        InputStream resourceAsStream = new FileInputStream(resource);
        PersonOverview[] rawOverviews = getJsonMapper().readValue(resourceAsStream, PersonOverview[].class);
        return Stream
                .of(rawOverviews)
                .collect(Collectors.toMap(
                        PersonOverview::email,
                        PersonOverview::employee_id));

    }


    private Map<String, Long> EmployeeIDtoID(DSLContext tx) throws IOException {
        return tx
                .select(PERSON.EMPLOYEE_ID, PERSON.ID)
                .from(PERSON)
                .fetchMap(PERSON.EMPLOYEE_ID, PERSON.ID);
    }


    private Set<PersonOverview> loadPeopleFromFile(Map<String, Long> orgIdByOrgExtId) throws IOException {
        //InputStream resourceAsStream = PersonLoader.class.getClassLoader().getResourceAsStream(resource);
        InputStream resourceAsStream = new FileInputStream(resource);
        PersonOverview[] rawOverviews = getJsonMapper().readValue(resourceAsStream, PersonOverview[].class);
        return Stream
                .of(rawOverviews)
                .map(d -> {
                    // if cannot find org unit, skip record and print warning



                    PersonOverview person = ImmutablePersonOverview
                        .copyOf(d)
                        .withOrganisational_unit_id(orgIdByOrgExtId.getOrDefault(d.organisational_unit_external_id().toString(), ORPHAN_ORG_UNIT_ID))
                        // this bit is better written the other way around as d.managerEmail may be undefined:
                        // withManagerEmployeeId(emailToEmployeeID.getOrDefault(d.managerEmail().get(), "0")))
                        .withManager_employee_id(d.manager_employee_id())
                        .withEmail(d.email());
                        return person;
                })
                .collect(Collectors.toSet());
    }


    private ImmutablePersonOverview toDomain(Record r) {
        PersonRecord personRecord = r.into(PERSON);
        return ImmutablePersonOverview
                .builder()
                .id(personRecord.getId().longValue())
                .employee_id(personRecord.getEmployeeId())
                .email(personRecord.getEmail())
                .display_name(personRecord.getDisplayName())
                .kind(personRecord.getKind())
                .manager_employee_id(personRecord.getManagerEmployeeId())
                .title(Optional.ofNullable(personRecord.getTitle()))
                .user_principal_name(Optional.ofNullable(personRecord.getUserPrincipalName()))
                .department_name(Optional.ofNullable(personRecord.getDepartmentName()))
                .mobile_phone(Optional.ofNullable(personRecord.getMobilePhone()))
                .officePhone(Optional.ofNullable(personRecord.getOfficePhone()))
                .organisational_unit_id(personRecord.getOrganisationalUnitId())
                .organisational_unit_external_id(r.get(ORGANISATIONAL_UNIT.EXTERNAL_ID))
                .build();
    }


    private PersonRecord toJooqRecord(DSLContext dsl, PersonOverview domain) {
        PersonRecord record = dsl.newRecord(PERSON);

        record.setId(domain.id().orElse(null));
        record.setEmployeeId(domain.employee_id());
        record.setDisplayName(domain.display_name());
        record.setEmail(domain.email());
        record.setDepartmentName(domain.department_name().orElse(null));
        record.setKind(domain.kind());
        record.setUserPrincipalName(domain.user_principal_name().orElse(null));

        // sets unmanaged people to 0 on every insert statement where its not specified, as it will be updated on second pass.
        record.setManagerEmployeeId(domain.manager_employee_id().orElse("0"));
        record.setTitle(domain.title().orElse(null));
        record.setMobilePhone(domain.mobile_phone().orElse(null));
        record.setOfficePhone(domain.officePhone().orElse(null));
        record.setOrganisationalUnitId(domain.organisational_unit_id().orElse(ORPHAN_ORG_UNIT_ID));
        record.setIsRemoved(false);// we only build records for new and updated people, which means they aren't removed
        return record;

    }

    private static int summarizeResults(int[] rcs) {
        return IntStream.of(rcs).sum();
    }


    private Map<String, String> mkEmailToEmployeeIdMap(Set<PersonOverview> existingPeople) {
        return existingPeople
                .stream()
                .collect(Collectors.toMap(
                        PersonOverview::email,
                        PersonOverview::employee_id));
    }


    private static Map<String, Long> loadOrgIdByExtIdMap(DSLContext tx) {
        Map<String, Long> orgIdByOrgExtId = tx
                .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                .from(ORGANISATIONAL_UNIT)
                .fetchMap(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID);
        return orgIdByOrgExtId;
    }


}
