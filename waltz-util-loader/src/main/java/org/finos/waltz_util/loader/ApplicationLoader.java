package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.model.ApplicationKind;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.records.ApplicationRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import static org.finos.waltz_util.schema.Tables.*;


public class ApplicationLoader {
    private final static Long ORPHAN_ORG_UNIT_ID = -1L; // this is a constant, so should be declared as static and uppercased
    private final String resource;  // these are initialised at construction, and should not change therefore marked as final
    private final DSLContext dsl;

    public ApplicationLoader(String resource) {
        this.resource = resource;
        AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(DIBaseConfiguration.class);
        dsl = springContext.getBean(DSLContext.class);
    }


    public void synch() {
        dsl.transaction(ctx -> {
            DSLContext tx = ctx.dsl();
            Map<String, Long> externalIdtoId = getExternalIdToIdMap(tx);


            Set<ApplicationOverview> desiredApps = loadPeopleFromFile(getOrgUnitRelations(tx));
            desiredApps = desiredApps
                    .stream()
                    .map(a -> {
                                Long id = externalIdtoId.getOrDefault(a.externalId(), ORPHAN_ORG_UNIT_ID);
                                return ImmutableApplicationOverview.copyOf(a)
                                        .withId(id);

                            }
                    )
                    .collect(Collectors.toSet());


            Set<ApplicationOverview> existingApps = getExistingPeople(tx);


            // json includes
            // given Asset_code and parent_asset_code
            DiffResult<ApplicationOverview> diff = DiffResult.mkDiff(
                    existingApps,
                    desiredApps,
                    ApplicationOverview::externalId,
                    Object::equals);


            insertNew(tx, diff.otherOnly());
            updateRelationships(tx, diff.differingIntersection());
            markRemoved(tx, diff.waltzOnly());
        });


    }


    private void insertNew(DSLContext tx, Collection<ApplicationOverview> toInsert) {
        List<ApplicationRecord> recordsToInsert = toInsert
                .stream()
                .map(a -> {
                    ApplicationRecord record = toJooqRecord(tx, a);
                    record.changed(APPLICATION.ID, false);
                    return record;


                })
                .collect(Collectors.toList());


        int numInserted = summarizeResults(tx
                .batchInsert(recordsToInsert)
                .execute());

        System.out.println("Records Created: " + numInserted);


    }

    private void updateRelationships(DSLContext tx, Collection<ApplicationOverview> toUpdate) {
        List<ApplicationRecord> recordsToUpdate = toUpdate
                .stream()
                .map(a -> {
                    ApplicationRecord record = toJooqRecord(tx, a);
                    record.changed(APPLICATION.ID, false);
                    return record;
                })
                .collect(Collectors.toList());

        int numUpdated = summarizeResults(tx
                .batchUpdate(recordsToUpdate)
                .execute());

        System.out.println("Records Updated: " + numUpdated);
    }

    private void markRemoved(DSLContext tx, Collection<ApplicationOverview> toRemove) {
        Set<String> assetCodesToRemove = toRemove
                .stream()

                .filter(a -> {
                    if (a.isRemoved().isPresent()){
                        return !a.isRemoved().get();
                    }
                    return false;
                })
                .map(ApplicationOverview::externalId)
                .collect(Collectors.toSet());


        int numRemoved = tx.update(APPLICATION)
                .set(APPLICATION.IS_REMOVED, true)
                .where(APPLICATION.ASSET_CODE.in(assetCodesToRemove))
                .execute();

        System.out.println("Records Removed: " + numRemoved);


    }


    private Map<String, Long> getOrgUnitRelations(DSLContext tx) {
        return tx
                .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                .from(ORGANISATIONAL_UNIT)
                .fetchMap(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID);
    }

    private Set<ApplicationOverview> loadPeopleFromFile(Map<String, Long> orgIdByOrgExtId) throws IOException {

        //InputStream resourceAsStream = ApplicationLoader.class.getClassLoader().getResourceAsStream(resource);

        InputStream resourceAsStream = new FileInputStream(resource);
        ApplicationOverview[] rawOverviews = getJsonMapper().readValue(resourceAsStream, ApplicationOverview[].class);


        return Stream
                .of(rawOverviews)
                .map(a -> ImmutableApplicationOverview
                        .copyOf(a)
                        .withOrgUnitId(orgIdByOrgExtId.getOrDefault(
                                a.organisationalUnitExternalId(),
                                ORPHAN_ORG_UNIT_ID)))

                .collect(Collectors.toSet());
    }


    private Set<ApplicationOverview> getExistingPeople(DSLContext tx) {
        Set<ApplicationOverview> existingPeople = tx
                .select(APPLICATION.fields())
                .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                .from(APPLICATION)
                .innerJoin(ORGANISATIONAL_UNIT)
                .on(ORGANISATIONAL_UNIT.ID.eq(APPLICATION.ORGANISATIONAL_UNIT_ID))
                .fetch()
                .stream()
                .map(r -> toDomain(r))
                .collect(Collectors.toSet());

        return existingPeople;


    }

    private Map<String, Long> getExternalIdToIdMap(DSLContext tx) {
        return tx
                .select(APPLICATION.ASSET_CODE, APPLICATION.ID)
                .from(APPLICATION)
                .fetchMap(APPLICATION.ASSET_CODE, APPLICATION.ID);
    }

    private ApplicationOverview toDomain(Record r) {
        ApplicationRecord app = r.into(APPLICATION);
        //Map<String, Long> ExtOUIDToOUID = getOrgUnitRelations(dsl);
        return ImmutableApplicationOverview
                .builder()
                .id(app.getId().longValue())
                .externalId(app.getAssetCode())
                .organisationalUnitExternalId(r.get(ORGANISATIONAL_UNIT.EXTERNAL_ID))
                .orgUnitId(app.getOrganisationalUnitId())
                .name(app.getName())
                .description(app.getDescription())
                .kind(ApplicationKind.valueOf(app.getKind()))
                .lifecyclePhase(app.getLifecyclePhase())
                .parentExternalId(Optional.ofNullable(app.getParentAssetCode()))
                .overallRating(app.getOverallRating())
                .criticality(Criticality.valueOf(app.getBusinessCriticality()))
                .isRemoved(app.getIsRemoved())
                .entityLifecycleStatus(app.getEntityLifecycleStatus())
                .plannedRetirementDate(Optional.ofNullable(app.getPlannedRetirementDate()))
                .actualRetirementDate(Optional.ofNullable(app.getActualRetirementDate()))
                .commissionDate(Optional.ofNullable(app.getCommissionDate()))
                .build();
    }


    private ApplicationRecord toJooqRecord(DSLContext dsl, ApplicationOverview app) {
        ApplicationRecord record = dsl.newRecord(APPLICATION);

        record.setId(app.id().orElse(null));
        record.setName(app.name());
        record.setDescription(app.description().orElse(null));
        record.setAssetCode(app.externalId());
        record.setCreatedAt(app.commissionDate().orElse(Timestamp.valueOf(LocalDateTime.now())));
        record.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        record.setOrganisationalUnitId(app.orgUnitId().orElseThrow(() -> new IllegalArgumentException("No org unit id")));
        record.setKind(app.kind().name());
        record.setLifecyclePhase(app.lifecyclePhase());
        record.setParentAssetCode(app.parentExternalId().orElse(null));
        record.setOverallRating(app.overallRating());
        record.setProvenance("waltz-dataloaders");
        record.setBusinessCriticality(app.criticality().name());
        record.setIsRemoved(false);
        return record;


    }


    private static int summarizeResults(int[] rcs) {
        return IntStream.of(rcs).sum();
    }


    public static void main(String[] args) {
        new ApplicationLoader("C:\\Data\\Coding Stuff\\Waltz-Loaders\\waltz-util-loader\\src\\main\\resources\\APPLICATION.json").synch();
    }

}
