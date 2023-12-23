package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.model.ApplicationKind;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.records.ApplicationRecord;
import org.jooq.DSLContext;
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
                ApplicationOverview::external_id,
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


        return rawApps
                .stream()
                .map(a -> {
                            Long id = externalIdtoId.getOrDefault(a.external_id(), ORPHAN_ORG_UNIT_ID);
                            return ImmutableApplicationOverview.copyOf(a)
                                    .withId(id)
                                    .withOrganisational_unit_id(orgIdByOrgExtId.getOrDefault(
                                            a.organisational_unit_external_id(),
                                            ORPHAN_ORG_UNIT_ID));


                        }
                )
                .collect(Collectors.toSet());
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
                .map(ApplicationOverview::external_id)
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
        // todo: Fix bug with failing to fetch users if ORGID is missing
        Set<ApplicationOverview> existingPeople = tx
                .select(APPLICATION.fields())
                .select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                .from(APPLICATION)
                .innerJoin(ORGANISATIONAL_UNIT)
                .on(ORGANISATIONAL_UNIT.ID.eq(APPLICATION.ORGANISATIONAL_UNIT_ID))
                .fetch()
                .stream()
                .map(r -> toOverview(r))
                .collect(Collectors.toSet());

        return existingPeople;


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
                .external_id(app.getAssetCode())
                .organisational_unit_external_id(r.get(ORGANISATIONAL_UNIT.EXTERNAL_ID))
                .organisational_unit_id(app.getOrganisationalUnitId())
                .name(app.getName())
                .description(app.getDescription())
                .kind(ApplicationKind.valueOf(app.getKind()))
                .lifecycle_phase(app.getLifecyclePhase())
                .parent_external_id(Optional.ofNullable(app.getParentAssetCode()))
                .overall_rating(app.getOverallRating())
                .provenance(app.getProvenance())
                .criticality(Criticality.valueOf(app.getBusinessCriticality()))
                .isRemoved(app.getIsRemoved())
                .entity_lifecycle_status(app.getEntityLifecycleStatus())
                .planned_retirement_date(Optional.ofNullable(app.getPlannedRetirementDate()))
                .actual_retirement_date(Optional.ofNullable(app.getActualRetirementDate()))
                .commission_date(Optional.ofNullable(app.getCommissionDate()))
                .build();
    }


    private ApplicationRecord toJooqRecord(DSLContext dsl, ApplicationOverview app) {
        ApplicationRecord record = dsl.newRecord(APPLICATION);

        record.setId(app.id().orElse(null));
        record.setName(app.name());
        record.setDescription(app.description().orElse(null));
        record.setAssetCode(app.external_id());
        record.setCreatedAt(app.commission_date().orElse(Timestamp.valueOf(LocalDateTime.now())));
        record.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        record.setOrganisationalUnitId(app.organisational_unit_id().orElseThrow(() -> new IllegalArgumentException("No org unit id")));
        record.setKind(app.kind().name());
        record.setLifecyclePhase(app.lifecycle_phase());
        record.setParentAssetCode(app.parent_external_id().orElse(null));
        record.setOverallRating(app.overall_rating());
        record.setProvenance(app.provenance());
        record.setBusinessCriticality(app.criticality().name());
        record.setIsRemoved(false);
        return record;


    }


    private void verifyUniqueIdentifier(Set<ApplicationOverview> apps) {
        // check if all values of propertyName are unique
        // if not, return false, or error?

        // compile all of "propertyName" properties to a Set, check against Len of apps
        Set<String> propertySet = apps.stream().map(a -> a.external_id()).collect(Collectors.toSet());
        if (propertySet.size() != apps.size()) {
            System.out.println("Duplicate Asset Codes found");
        }


    }

}
