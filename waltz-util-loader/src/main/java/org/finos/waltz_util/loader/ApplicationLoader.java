package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.model.ApplicationKind;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.records.ApplicationRecord;
import org.jooq.DSLContext;
import static org.jooq.impl.DSL.*;
import org.jooq.Record;

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
import java.util.stream.Stream;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.APPLICATION;
import static org.finos.waltz_util.schema.Tables.ORGANISATIONAL_UNIT;


public class ApplicationLoader extends Loader<ApplicationOverview> {
    private final static Long ORPHAN_ORG_UNIT_ID = -1L; // this is a constant, so should be declared as static and uppercased

    public ApplicationLoader(String resource) {
        super(resource);
    }


    protected DiffResult<ApplicationOverview> getDiffResults(Set<ApplicationOverview> existingOverviews, Set<ApplicationOverview> desiredOverviews) {
        return DiffResult.mkDiff(
                existingOverviews,
                desiredOverviews,
                ApplicationOverview::asset_code,
                Object::equals
        );
    }

    protected void validate(Set<ApplicationOverview> apps) {
        apps.stream().filter(a -> a.organisational_unit_id().get().equals(ORPHAN_ORG_UNIT_ID)).forEach(a -> System.out.println("Cannot find Org Unit Associated with: " + a.name()));
        verifyUniqueIdentifier(apps);
    }

    protected Set<ApplicationOverview> processOverviews(Set<ApplicationOverview> rawApps) {
        Map<String, Long> externalIdtoId = getExternalIdToIdMap(dsl);
        Map<String, Long> orgIdByOrgExtId = getOrgUnitRelations(dsl);

        rawApps.add(createOrphanApplication());

        return rawApps
                .stream()
                .map(a -> {
                            Long id = externalIdtoId.getOrDefault(a.asset_code(), ORPHAN_ORG_UNIT_ID);
                            return ImmutableApplicationOverview.copyOf(a)
                                    .withId(id)
                                    .withOrganisational_unit_id(orgIdByOrgExtId.getOrDefault(
                                            a.organisational_unit_external_id(),
                                            ORPHAN_ORG_UNIT_ID))
                                    .withIsRemoved(false);


                        }
                )
                .collect(Collectors.toSet());
    }

    private ApplicationOverview createOrphanApplication() {
        return ImmutableApplicationOverview
                .builder()
                .id(-1L)
                .name("Unknown Application")
                .description("This Application can be used as a placeholder for entity linking that has not yet been determined.")
                .provenance("waltz-loader")
                .asset_code("UNKNOWN")
                .organisational_unit_external_id("ORPHAN")
                .build();
    }


    protected void insertNew(DSLContext tx, Collection<ApplicationOverview> toInsert) {
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

    protected void updateExisting(DSLContext tx, Collection<ApplicationOverview> toUpdate) {
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

    protected void deleteExisting(DSLContext tx, Collection<ApplicationOverview> toRemove) {
        Set<String> assetCodesToRemove = toRemove
                .stream()

                .filter(a -> {
                    if (a.isRemoved().isPresent()) {
                        return !a.isRemoved().get();
                    }
                    return false;
                })
                .map(ApplicationOverview::asset_code)
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
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        record -> record.getValue(ORGANISATIONAL_UNIT.EXTERNAL_ID),
                        record -> record.getValue(ORGANISATIONAL_UNIT.ID),
                        (a, b) -> a
                ));
    }

    protected Set<ApplicationOverview> loadFromFile() throws IOException {

        //InputStream resourceAsStream = ApplicationLoader.class.getClassLoader().getResourceAsStream(resource);

        InputStream resourceAsStream = new FileInputStream(resource);
        ApplicationOverview[] rawOverviews = getJsonMapper().readValue(resourceAsStream, ApplicationOverview[].class);


        return Stream
                .of(rawOverviews)
                .map(a -> ImmutableApplicationOverview
                        .copyOf(a))
                .collect(Collectors.toSet());
    }


    protected Set<ApplicationOverview> getExistingRecordsAsOverviews(DSLContext tx) {
        // todo: Fix bug with failing to fetch applications if ORGID is missing


        // select applications and join in ORG_UNIT_EXTERNAL_ID
        Set<ApplicationOverview> existingApplications = tx
                .select(APPLICATION.fields())
                .select(ORGANISATIONAL_UNIT.EXTERNAL_ID)
                .from(APPLICATION)
                .leftJoin(ORGANISATIONAL_UNIT)
                .on(APPLICATION.ORGANISATIONAL_UNIT_ID.eq(ORGANISATIONAL_UNIT.ID))
                .where(ORGANISATIONAL_UNIT.EXTERNAL_ID.isNotNull())
                .fetch()
                .stream()
                .map(r -> toOverview(r))
                .collect(Collectors.toSet());


        return existingApplications;


    }

    private Map<String, Long> getExternalIdToIdMap(DSLContext tx) {
        return tx
                .select(APPLICATION.ASSET_CODE, APPLICATION.ID)
                .from(APPLICATION)
                .fetchMap(APPLICATION.ASSET_CODE, APPLICATION.ID);
    }

    protected ApplicationOverview toOverview(Record r) {
        ApplicationRecord app = r.into(APPLICATION);
        //Map<String, Long> ExtOUIDToOUID = getOrgUnitRelations(dsl);
        return ImmutableApplicationOverview
                .builder()
                .id(app.getId().longValue())
                .asset_code(app.getAssetCode())
                .organisational_unit_external_id(r.get(ORGANISATIONAL_UNIT.EXTERNAL_ID))
                .organisational_unit_id(app.getOrganisationalUnitId())
                .name(app.getName())
                .description(app.getDescription())
                .kind(ApplicationKind.valueOf(app.getKind()))
                .lifecycle_phase(app.getLifecyclePhase())
                .parent_asset_code(Optional.ofNullable(app.getParentAssetCode()))
                .overall_rating(app.getOverallRating())
                .provenance(app.getProvenance())
                .business_criticality(Criticality.valueOf(app.getBusinessCriticality()))
                .isRemoved(app.getIsRemoved())
                .entity_lifecycle_status(app.getEntityLifecycleStatus())
                .planned_retirement_date(Optional.ofNullable(app.getPlannedRetirementDate()))
                .actual_retirement_date(Optional.ofNullable(app.getActualRetirementDate()))
                .commission_date(Optional.ofNullable(app.getCommissionDate()))

                .created_at(app.getCreatedAt())
                .updated_at(app.getUpdatedAt())

                .build();
    }


    private ApplicationRecord toJooqRecord(DSLContext dsl, ApplicationOverview app) {
        ApplicationRecord record = dsl.newRecord(APPLICATION);

        record.setId(app.id().orElse(null));
        record.setName(app.name());
        record.setDescription(app.description().orElse(null));
        record.setAssetCode(app.asset_code());
        record.setCreatedAt(app.created_at());
        record.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        record.setOrganisationalUnitId(app.organisational_unit_id().orElse(-1L));
        record.setKind(app.kind().name());
        record.setLifecyclePhase(app.lifecycle_phase());
        record.setParentAssetCode(app.parent_asset_code().orElse(null));
        record.setOverallRating(app.overall_rating());
        record.setProvenance(app.provenance());
        record.setBusinessCriticality(app.business_criticality().name());
        record.setIsRemoved(app.isRemoved().orElse(false));


        return record;


    }


    private void verifyUniqueIdentifier(Set<ApplicationOverview> apps) {
        // check if all values of propertyName are unique
        // if not, return false, or error?

        // compile all of "propertyName" properties to a Set, check against Len of apps
        Set<String> propertySet = apps.stream().map(a -> a.asset_code()).collect(Collectors.toSet());
        if (propertySet.size() != apps.size()) {
            System.out.println("Duplicate Asset Codes found");
        }


    }

}
