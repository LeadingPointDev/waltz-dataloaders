package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.helper.LoggingUtilities;
import org.finos.waltz_util.common.model.ApplicationKind;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.records.ApplicationRecord;
import org.finos.waltz_util.schema.tables.records.PersonRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.SelectOnConditionStep;
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
         * 3. Compare emails (unique employee id if possible) todo: check for UUIDs
         * 4. insert new entries where no email match
         *
         */

        dsl.transaction(ctx ->{
            DSLContext tx = ctx.dsl();

            Set<PersonOverview> existingPeople = tx
                    .select(PERSON.fields())
                    .from(PERSON)
                    .fetch()
                    .stream()
                    .map(r -> toDomain(r))
                    .collect(Collectors.toSet());
            System.out.println(existingPeople);
        });



    }

    private void updateRelationships(){
        /**
         * for all entries, compare with JSON
         * todo finish this bit
         */

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
                .managerEmployeeId(
                        personRecord.getManagerEmployeeId())
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
                .isRemoved(
                        personRecord.getIsRemoved())
                .build();
    }
}
