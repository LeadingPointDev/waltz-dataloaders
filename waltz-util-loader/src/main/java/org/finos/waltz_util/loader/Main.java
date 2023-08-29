package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.helper.LoggingUtilities;
import org.finos.waltz_util.common.model.ApplicationKind;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.records.ApplicationRecord;
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

public class Main {


    public static final long ORPHAN_ORG_UNIT_ID = 150L;


    public static void main(String[] args) throws IOException {

        PersonLoader PL = new PersonLoader("person.json");
        PL.update();

        if (true) {
            throw new IOException("NO FURTHER!");
        }

        LoggingUtilities.configureLogging();
        OrganisationLoader OL = new OrganisationLoader("orgs.json");
        OL.update();


        if (true){
            throw new IOException("NO FURTHER!");
        }
        System.out.println("Hello, World!");

        AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(DIBaseConfiguration.class);

        DSLContext dsl = springContext.getBean(DSLContext.class);

        Result<Record5<String, Long, String, String, String>> result = dsl
                .select(APPLICATION.NAME,
                        APPLICATION.ID,
                        APPLICATION.ASSET_CODE,
                        ORGANISATIONAL_UNIT.NAME,
                        ORGANISATIONAL_UNIT.EXTERNAL_ID)
                .from(APPLICATION)
                .innerJoin(ORGANISATIONAL_UNIT)
                    .on(ORGANISATIONAL_UNIT.ID.eq(APPLICATION.ORGANISATIONAL_UNIT_ID))
                .fetch();

        System.out.println(result);



        ApplicationOverview myApp = ImmutableApplicationOverview
                .builder()
                .name("Hello")
                .organisationalUnitExternalId("123")
                .externalId("abc")
                .build();


        ApplicationOverview myApp2 = ImmutableApplicationOverview
                .builder()
                .name("Hello")
                .organisationalUnitExternalId("123")
                .externalId("xyz")
                .build();


        System.out.println(myApp);

        System.out.println(myApp.equals(myApp2));


        System.out.println("--- Loader demo");

        dsl.transaction(ctx -> {
            DSLContext tx = ctx.dsl();
            // map for org unit external id to org unit id
            Map<String, Long> orgUnitIdsByOrgUnitExtId = tx
                    .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                    .from(ORGANISATIONAL_UNIT)
                    .fetchMap(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID);

            Map<String, Long> appIdsByAppExtId = tx
                    .select(APPLICATION.ASSET_CODE, APPLICATION.ID)
                    .from(APPLICATION)
                    .fetchMap(APPLICATION.ASSET_CODE, APPLICATION.ID);

            SelectOnConditionStep<Record> qry = tx
                    .select(APPLICATION.fields())
                    .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                    .from(APPLICATION)
                    .innerJoin(ORGANISATIONAL_UNIT)
                    .on(ORGANISATIONAL_UNIT.ID.eq(APPLICATION.ORGANISATIONAL_UNIT_ID));

            Set<ApplicationOverview> existingApps = qry
                    .fetch()
                    .stream()
                    .map(r -> toDomain(r))
                    .collect(Collectors.toSet());

            System.out.println(existingApps);

//            byte[] bytes = Files.readAllBytes(Path.of("waltz-util-loader", "src", "main", "resources","apps.json"));

//            System.out.println(bytes.length);

            ApplicationOverview[] rawOverviews = getJsonMapper().readValue(Main.class.getClassLoader().getResourceAsStream("apps.json"), ApplicationOverview[].class);
            Set<ApplicationOverview> desiredApps = Stream
                    .of(rawOverviews)
                    .map(d -> ImmutableApplicationOverview
                            .copyOf(d)
                            .withId(Optional.ofNullable(appIdsByAppExtId.get(d.externalId())))
                            .withOrgUnitId(orgUnitIdsByOrgUnitExtId.getOrDefault(
                                    d.organisationalUnitExternalId(),
                                    ORPHAN_ORG_UNIT_ID)))
                    .collect(Collectors.toSet());

            DiffResult<ApplicationOverview> diff = DiffResult.mkDiff(
                    existingApps,
                    desiredApps,
                    ApplicationOverview::externalId,
                    Object::equals);

            System.out.println(diff);

            Collection<ApplicationOverview> toRemove = diff.waltzOnly();
            Collection<ApplicationOverview> toInsert = diff.otherOnly();
            Collection<ApplicationOverview> toUpdate = diff.differingIntersection();


            Set<String> assetCodesToRemove = toRemove
                    .stream()
                    .map(ApplicationOverview::externalId)
                    .collect(Collectors.toSet());

            List<ApplicationRecord> recordsToInsert = toInsert
                    .stream()
                    .map(a -> {
                        ApplicationRecord r = toJooqRecord(tx, a);
                        r.changed(APPLICATION.ID, false);
                        return r;
                    })
                    .collect(Collectors.toList());

            List<ApplicationRecord> recordsToUpdate = toUpdate
                    .stream()
                    .map(a -> {
                        ApplicationRecord r = toJooqRecord(tx, a);
                        r.changed(APPLICATION.ID, false);
                        return r;
                    })
                    .collect(Collectors.toList());

            int numRemoved = 0; // tx
//                    .update(APPLICATION)
//                    .set(APPLICATION.IS_REMOVED, true)
//                    .where(APPLICATION.ASSET_CODE.in(assetCodesToRemove))
//                    .execute();

            int recordsCreated = summarizeResults(tx
                    .batchInsert(recordsToInsert)
                    .execute());

            //System.out.println(recordsToUpdate);

            //System.out.println("++++" + appIdsByAppExtId.get("APP-002"));

            int recordsUpdated = summarizeResults(tx
                    .batchUpdate(recordsToUpdate)
                    .execute());

            System.out.printf("Removed: %d, Created: %d, Updated: %d%n", numRemoved, recordsCreated, recordsUpdated);
            throw new RuntimeException("BooooM!");
        });

    }


    private static ImmutableApplicationOverview toDomain(Record r) {
        ApplicationRecord appRecord = r.into(APPLICATION);
        return ImmutableApplicationOverview
                .builder()
                .externalId(appRecord.getAssetCode())
                .name(appRecord.getName())
                .organisationalUnitExternalId(r.get(ORGANISATIONAL_UNIT.EXTERNAL_ID))
                .orgUnitId(appRecord.getOrganisationalUnitId())
                .description(Optional.ofNullable(appRecord.getDescription()))
                .kind(ApplicationKind.valueOf(appRecord.getKind()))
                .provenance(appRecord.getProvenance())
                .lifecyclePhase(appRecord.getLifecyclePhase())
                .criticality(Criticality.valueOf(appRecord.getBusinessCriticality()))
                .build();
    }


    private static ApplicationRecord toJooqRecord(DSLContext dsl, ApplicationOverview domain) {
        ApplicationRecord appRecord = dsl.newRecord(APPLICATION);
        appRecord.setId(domain.id().orElse(null));
        appRecord.setAssetCode(domain.externalId());
        appRecord.setName(domain.name());
        appRecord.setKind(domain.kind().name());
        appRecord.setDescription(domain.description().orElse(null));
        appRecord.setProvenance(domain.provenance());
        appRecord.setOrganisationalUnitId(domain.orgUnitId().orElseThrow(() -> new IllegalArgumentException("No org unit id")));
        appRecord.setBusinessCriticality(domain.criticality().name());
        appRecord.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

        return appRecord;
    }


    public static int summarizeResults(int[] rcs) {
        return IntStream.of(rcs).sum();
    }

}
