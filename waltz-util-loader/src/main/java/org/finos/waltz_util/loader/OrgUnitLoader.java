package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.schema.tables.records.OrganisationalUnitRecord;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.ORGANISATIONAL_UNIT;

public class OrgUnitLoader extends Loader<OrgUnitOverview>{

    private Long maxId = 0L;

    public OrgUnitLoader(String resource) {
        super(resource);
    }


    @Override
    protected DiffResult<OrgUnitOverview> getDiffResults(Set<OrgUnitOverview> existingOverviews, Set<OrgUnitOverview> desiredOverviews) {
        return DiffResult.mkDiff(
                existingOverviews,
                desiredOverviews,
                OrgUnitOverview::external_id,
                Object::equals
        );
    }

    private Long getMaxId(DSLContext tx){
        Long maxId = tx
                .select(ORGANISATIONAL_UNIT.ID.max())
                .from(ORGANISATIONAL_UNIT)
                .fetchOne()
                .value1();
        return maxId == null ? 0L : maxId;
    }

    protected void validate(Set<OrgUnitOverview> overviews) {
        //todo
    }

    protected Set<OrgUnitOverview> processOverviews(Set<OrgUnitOverview> rawOUs) {
        Map<String, Long> externalIdToId;
        try {
            externalIdToId = externalIDtoIDMap(dsl, rawOUs.toArray(new OrgUnitOverview[rawOUs.size()]));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        Set<OrgUnitOverview> desiredOUs = rawOUs
                .stream()
                .map(o -> ImmutableOrgUnitOverview.copyOf(o)
                        .withId(externalIdToId.get(o.external_id()))
                        .withParent_id(Optional.ofNullable(o.parent_external_id().map(externalIdToId::get).orElse(null))))
                .collect(Collectors.toSet());
        desiredOUs.add(createOrphanOrg());
        return desiredOUs;
    }

    private OrgUnitOverview createOrphanOrg() {
        return ImmutableOrgUnitOverview
                .builder()
                .id(-1L)
                .name("Orphan Organisation")
                .description("This org will catch any malformed entities that have incorrectly set parents. Any entries that reference this org have an incorrect field in the external data, or there is a bug.")
                .last_updated_at(new Timestamp(System.currentTimeMillis()))
                .external_id("ORPHAN")
                .created_by("waltz-loader")

                .last_updated_by("waltz-loader")
                .provenance("waltz-loader")
                .build();
    }

    protected void insertNew( DSLContext dsl, Collection<OrgUnitOverview> toInsert) {
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

    protected void updateExisting(DSLContext dsl ,Collection<OrgUnitOverview> toUpdate) {
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

    protected void deleteExisting(DSLContext dsl, Collection<OrgUnitOverview> toDelete ) {
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
                .collect(Collectors.toMap(OrgUnitOverview::external_id, OrgUnitOverview::id));
        Map<String, Long> externalIDMap = externalOverviews.stream()
                .collect(Collectors.toMap(OrgUnitOverview::external_id, OrgUnitOverview::id));

        // map the two maps together and prioritize the internal IDs
        return Stream
                .of(waltzIDMap, externalIDMap)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));


    }

    protected Set<OrgUnitOverview> getExistingRecordsAsOverviews(DSLContext dsl) {
        Set<OrgUnitOverview> existingOUs = dsl
                .select(ORGANISATIONAL_UNIT.fields())
                .from(ORGANISATIONAL_UNIT)
                .fetch()
                .stream()
                .filter(r -> r.get(ORGANISATIONAL_UNIT.EXTERNAL_ID) != null) // do not process un-identified orgs (at all)
                .map(o -> toOverview(o))
                .collect(Collectors.toSet());
        this.maxId = getMaxId(dsl);
        return existingOUs;

    }

    protected Set<OrgUnitOverview> loadFromFile() throws IOException {

        InputStream resourceAsStream = new FileInputStream(resource);
        OrgUnitOverview[] rawOverviews = getJsonMapper().readValue(resourceAsStream, OrgUnitOverview[].class);


        Map<String, Long> externalIdToId = externalIDtoIDMap(dsl, new OrgUnitOverview[0]);


        Set<OrgUnitOverview> desiredOUs = Stream
                .of(rawOverviews)
                .map(o -> {
                    this.maxId = this.maxId + 20;
                    ImmutableOrgUnitOverview overview = ImmutableOrgUnitOverview
                            .copyOf(o)
                            .withParent_id(Optional.ofNullable(o.parent_external_id())
                                    .map(externalIdToId::get))
                            .withId(maxId);

                    return overview;
                })

                .collect(Collectors.toSet());

        return desiredOUs;


    }


    protected OrgUnitOverview toOverview(Record r) {
        OrganisationalUnitRecord record = r.into(ORGANISATIONAL_UNIT);
        return ImmutableOrgUnitOverview.builder()
                .name(record.getName())
                .description(Optional.ofNullable(record.getDescription()))
                .id(record.getId())
                .parent_id(Optional.ofNullable(record.getParentId()))
                .external_id(record.getExternalId())
                .created_at(record.getCreatedAt())
                .last_updated_at(record.getLastUpdatedAt())
                .provenance(record.getProvenance())
                .build();


    }

    private OrganisationalUnitRecord toJooqRecord(OrgUnitOverview o) {
        OrganisationalUnitRecord record = new OrganisationalUnitRecord();
        record.setId(o.id());
        record.setName(o.name());
        record.setDescription(o.description().orElse(null));
        record.setParentId(o.parent_id().orElse(null));
        record.setExternalId(o.external_id());
        record.setCreatedAt(o.created_at().orElse(new Timestamp(System.currentTimeMillis())));
        record.setLastUpdatedAt(o.last_updated_at());
        record.setCreatedBy(o.created_by());
        record.setLastUpdatedBy(o.last_updated_by());
        record.setProvenance(o.provenance());
        return record;
    }


    public Map<String, Long> externalIDtoIDMap(DSLContext dsl, OrgUnitOverview[] rawOverviews ) throws IOException {
        Map<String, Long> internalMap = dsl.select(ORGANISATIONAL_UNIT.EXTERNAL_ID, ORGANISATIONAL_UNIT.ID)
                .from(ORGANISATIONAL_UNIT)
                .stream()
                .collect(
                        Collectors.toMap(
                                r -> r.get(ORGANISATIONAL_UNIT.EXTERNAL_ID),
                                r -> r.get(ORGANISATIONAL_UNIT.ID),
                                (a, b) -> a
                        )
                );

        Map<String, Long> externalMap = Stream
                .of(rawOverviews)
                .collect(Collectors.toMap(OrgUnitOverview::external_id, OrgUnitOverview::id));

        // map the two maps together and prioritize the internal IDs
        return Stream
                .of(internalMap, externalMap)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

    }
}
