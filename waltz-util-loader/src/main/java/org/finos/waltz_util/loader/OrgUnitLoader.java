package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.schema.tables.records.OrganisationalUnitRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.ORGANISATIONAL_UNIT;

public class OrgUnitLoader {

    private final String resource;
    private final DSLContext dsl;
    private Long maxId = 0L;

    public OrgUnitLoader(String resource) {
        this.resource = resource;
        AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(DIBaseConfiguration.class);
        dsl = springContext.getBean(DSLContext.class);
    }

    public void synch() {
        dsl.transaction(ctx -> {
            DSLContext tx = ctx.dsl();


            Set<OrgUnitOverview> existingOUs = getExistingOrgUnits(tx);
            maxId = existingOUs.stream().map(OrgUnitOverview::id).max(Long::compareTo).orElse(0L);
            Set<OrgUnitOverview> unprocessedOUs = loadDesiredOUs(tx);

            Map<String, Long> externalIdToId = mapExternalIDs(existingOUs, unprocessedOUs);
            // go through desiredOUs and set ID's where needed

            Set<OrgUnitOverview> desiredOUs = unprocessedOUs
                    .stream()
                    .map(o -> ImmutableOrgUnitOverview.copyOf(o)
                            .withId(externalIdToId.get(o.externalID()))
                            .withParentID(Optional.ofNullable(o.parentExternalID().map(externalIdToId::get).orElse(null))))
                    .collect(Collectors.toSet());
            desiredOUs.add(createOrphanOrg());

            DiffResult<OrgUnitOverview> diff = DiffResult.mkDiff(
                    existingOUs,
                    desiredOUs,
                    OrgUnitOverview::externalID,
                    Object::equals
            );


            insertNew(diff.otherOnly(), tx);
            updateExisting(diff.differingIntersection(), tx);
            deleteRemoved(diff.waltzOnly(), tx);




        });
    }

    private OrgUnitOverview createOrphanOrg() {
        return ImmutableOrgUnitOverview
                .builder()
                .id(-1L)
                .name("Orphan Organisation")
                .description("This org will catch any malformed entities that have incorrectly set parents. Any entries that reference this org have an incorrect field in the external data, or there is a bug.")
                .lastUpdatedAt(new Timestamp(System.currentTimeMillis()))
                .externalID("ORPHAN")
                .createdBy("waltz-loader")

                .lastUpdatedBy("waltz-loader")
                .provenance("waltz-loader")
                .build();
    }

    private void insertNew(Collection<OrgUnitOverview> toInsert, DSLContext dsl) {
        List<OrganisationalUnitRecord> recordsToInsert = toInsert
                .stream()
                .map(o -> {
                    OrganisationalUnitRecord record = toJooqRecord(o);

                    return record;
                }).collect(Collectors.toList());
        int numInserted = summarizeResults(dsl
                .batchInsert(recordsToInsert)
                .execute());

        System.out.println("Records Created: " + numInserted);

    }

    private void updateExisting(Collection<OrgUnitOverview> toUpdate, DSLContext dsl) {
        List<OrganisationalUnitRecord> recordsToUpdate = toUpdate
                .stream()
                .map(o -> {
                    OrganisationalUnitRecord record = toJooqRecord(o);
                    record.changed(ORGANISATIONAL_UNIT.ID, false);
                    return record;
                })
                .collect(Collectors.toList());

        int numUpdated = summarizeResults(dsl
                .batchUpdate(recordsToUpdate)
                .execute());

        System.out.println("Records Updated: " + numUpdated);
    }

    private void deleteRemoved(Collection<OrgUnitOverview> toDelete, DSLContext dsl) {
        List<Long> idsToRemove = toDelete
                .stream()
                .map(OrgUnitOverview::id)
                .collect(Collectors.toList());

        int numDeleted = dsl.
                deleteFrom(ORGANISATIONAL_UNIT)
                .where(ORGANISATIONAL_UNIT.ID.in(idsToRemove))
                .execute();

        System.out.println("Records Deleted: " + numDeleted);
    }


    private Map<String, Long> mapExternalIDs(Set<OrgUnitOverview> waltzOverviews, Set<OrgUnitOverview> externalOverviews) {
        Map<String, Long> waltzIDMap = waltzOverviews.stream()
                .collect(Collectors.toMap(OrgUnitOverview::externalID, OrgUnitOverview::id));
        Map<String, Long> externalIDMap = externalOverviews.stream()
                .collect(Collectors.toMap(OrgUnitOverview::externalID, OrgUnitOverview::id));

        // map the two maps together and prioritize the internal IDs
        return Stream
                .of(waltzIDMap, externalIDMap)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));


    }

    private Set<OrgUnitOverview> getExistingOrgUnits(DSLContext dsl) {
        return dsl
                .select(ORGANISATIONAL_UNIT.fields())
                .from(ORGANISATIONAL_UNIT)
                .fetch()
                .stream()
                .map(o -> toDomain(o))
                .collect(Collectors.toSet());

    }

    private Set<OrgUnitOverview> loadDesiredOUs(DSLContext dsl) throws IOException {

        InputStream resourceAsStream = new FileInputStream(resource);
        OrgUnitOverview[] rawOverviews = getJsonMapper().readValue(resourceAsStream, OrgUnitOverview[].class);


        Map<String, Long> externalIdToId = externalIDtoIDMap(dsl);


        Set<OrgUnitOverview> desiredOUs = Stream
                .of(rawOverviews)
                .map(o -> {
                    maxId = maxId + 20;
                    ImmutableOrgUnitOverview overview = ImmutableOrgUnitOverview
                            .copyOf(o)
                            .withParentID(Optional.ofNullable(o.parentExternalID())
                                    .map(externalIdToId::get))
                            .withId(maxId);

                    return overview;
                })

                .collect(Collectors.toSet());

        return desiredOUs;


    }


    private OrgUnitOverview toDomain(Record r) {
        OrganisationalUnitRecord record = r.into(ORGANISATIONAL_UNIT);
        return ImmutableOrgUnitOverview.builder()
                .name(record.getName())
                .description(record.getDescription())
                .id(record.getId())
                .parentID(Optional.ofNullable(record.getParentId()))
                .externalID(record.getExternalId())
                // do we need to have the created at/updated at fields, I think not?
//                .createdAt(record.getCreatedAt())
//                .lastUpdatedAt(record.getLastUpdatedAt())
//                .createdBy(record.getCreatedBy())
//                .lastUpdatedBy(record.getLastUpdatedBy())
                //
                .provenance(record.getProvenance())
                .build();


    }

    private OrganisationalUnitRecord toJooqRecord(OrgUnitOverview o) {
        OrganisationalUnitRecord record = new OrganisationalUnitRecord();
        record.setId(o.id());
        record.setName(o.name());
        record.setDescription(o.description().orElse(null));
        record.setParentId(o.parentID().orElse(null));
        record.setExternalId(o.externalID());
        record.setCreatedAt(o.createdAt().orElse(new Timestamp(System.currentTimeMillis())));
        record.setLastUpdatedAt(new Timestamp(System.currentTimeMillis()));
        record.setCreatedBy(o.createdBy());
        record.setLastUpdatedBy(o.lastUpdatedBy());
        record.setProvenance(o.provenance());
        return record;
    }


    public Map<String, Long> externalIDtoIDMap(DSLContext dsl) throws IOException {
        Map<String, Long> internalMap = dsl.select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                .from(ORGANISATIONAL_UNIT)
                .fetchMap(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID);


        InputStream resourceAsStream = new FileInputStream(resource);
        OrgUnitOverview[] rawOverviews = getJsonMapper().readValue(resourceAsStream, OrgUnitOverview[].class);
        Map<String, Long> externalMap = Stream
                .of(rawOverviews)
                .collect(Collectors.toMap(OrgUnitOverview::externalID, OrgUnitOverview::id));


        return Stream
                .of(internalMap, externalMap)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));


    }


    private static int summarizeResults(int[] rcs) {
        return IntStream.of(rcs).sum();
    }

    public static void main(String[] args) {
        new OrgUnitLoader("ORG-UNIT.json").synch();
    }


}
