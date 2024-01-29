package org.finos.waltz_util.loader;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.schema.tables.records.LogicalFlowDecoratorRecord;
import org.finos.waltz_util.schema.tables.records.LogicalFlowRecord;;
import org.finos.waltz_util.schema.tables.records.PhysicalFlowRecord;
import org.finos.waltz_util.schema.tables.records.PhysicalSpecificationRecord;
import org.jooq.*;
import org.jooq.Record;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.*;

public class PhysicalFlowLoader extends Loader<PhysicalFlowOverview>{
    public PhysicalFlowLoader(String resource) {
        super(resource);
    }
    private Long nextId;
    @Override
    protected DiffResult<PhysicalFlowOverview> getDiffResults(Set<PhysicalFlowOverview> existingOverviews, Set<PhysicalFlowOverview> desiredOverviews) {
        return DiffResult.mkDiff(
                existingOverviews,
                desiredOverviews,
                PhysicalFlowOverview::external_id,
                Object::equals);
    }

    @Override
    protected void validate(Set<PhysicalFlowOverview> overviews) {
        //todo: implement validation
    }

    @Override
    protected Set<PhysicalFlowOverview> loadFromFile() throws IOException {
        Set<PhysicalFlowOverview> overviews = new HashSet<>();
        ObjectMapper mapper = getJsonMapper();

        ObjectReader reader = mapper.readerFor(PhysicalFlowOverview.class);


        try (InputStream resourceAsStream = new FileInputStream(resource)){
            MappingIterator<PhysicalFlowOverview> iterator = reader.readValues(resourceAsStream); // this is a stream of objects
            while (iterator.hasNext()){
                try {
                    PhysicalFlowOverview next = iterator.next();
                    overviews.add(next);
                } catch (Exception e) {
                    System.out.println(e);
                    e.printStackTrace();
                }
            }
        }
        return overviews;
    }

    @Override
    protected Set<PhysicalFlowOverview> processOverviews(Set<PhysicalFlowOverview> rawOverviews) {
        /** Heres what to do:
         * 1. Get the Physical Flow Overviews, check what state they come in - this determines the next steps
         * 2 Figure out what to put in the mock JSON and set up a Run Configuration to run the loader (Args etc)
         *
         * For Data onboarding:
         *  - Ensure that it is valid with logical flows as well.
         *
         *  Link with process name (need map)
         *  Need mapping functions for all the different tables we have to pull in, can it be done with SQL?
         *
         *
         *
         */
        Set<PhysicalFlowOverview> processedOverviews = new HashSet<>();
        // get max id from logical_flow table

        //Getting correct maps here:
        HashMap<String, Long> nameToAppID = getNameAppIdMap(dsl);

        // fill in data for each overview
        for (PhysicalFlowOverview p : rawOverviews){
            PhysicalFlowOverview newO = ImmutablePhysicalFlowOverview.copyOf(p)
                    .withSource_entity_id(nameToAppID.get(p.source_entity_name().get()))
                    .withTarget_entity_id(nameToAppID.get(p.target_entity_name().get()))
                    ;




            // do stuff
            //-----------
            processedOverviews.add(newO);

        }
        // return the set of overviews
        return processedOverviews;
    }

    @Override
    protected Set<PhysicalFlowOverview> getExistingRecordsAsOverviews(DSLContext tx) {
        nextId = tx.select(PHYSICAL_FLOW.ID.max())
                .from(PHYSICAL_FLOW)
                .fetchOne()
                .getValue(PHYSICAL_FLOW.ID.max());
        nextId = nextId == null ? 1 : nextId + 1;



        Set<PhysicalFlowOverview> OU = dsl
                .select(PHYSICAL_FLOW.fields())
                .select(PHYSICAL_FLOW_PARTICIPANT.fields())
                .select(LOGICAL_FLOW.fields())
                .select(LOGICAL_FLOW_DECORATOR.fields())
                .select(PHYSICAL_SPECIFICATION.fields())
                .from(PHYSICAL_FLOW)
                .leftJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.ID.eq(PHYSICAL_FLOW.LOGICAL_FLOW_ID))
                .leftJoin(PHYSICAL_FLOW_PARTICIPANT)
                .on(PHYSICAL_FLOW_PARTICIPANT.PHYSICAL_FLOW_ID.eq(PHYSICAL_FLOW.ID))
                .leftJoin(LOGICAL_FLOW_DECORATOR)
                .on(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(LOGICAL_FLOW.ID))
                .leftJoin(PHYSICAL_SPECIFICATION)
                .on(PHYSICAL_FLOW.SPECIFICATION_ID.eq(PHYSICAL_SPECIFICATION.ID))
                .fetch()
                .stream()
                .map(o -> toOverview(o)) // ignores physical flows with no external id
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return OU;
    }

    @Override
    protected void insertNew(DSLContext tx, Collection<PhysicalFlowOverview> overviews) {
        // for each overview, need to insert following records:
        // 1. Physical Flow
        // 2. Logical Flow - done
        // 3. Logical Flow Decorator
        // 4. Physical Flow Participant (servers i think)
        //
        //
        // Create Logical Flow
        // Create Logical Flow Decorator
        // - Check no duplicates, always update existing if there (func)
        // Create Physical Flow with logical flow link
        // Create Physical Flow Participant (duplicates?)
        Set<String> logicalFlowExIDs = getLfExIDToID(tx).keySet();
        List<LogicalFlowRecord> logicalFlowRecords = overviews
                .stream()
                .map(o -> toLogicalFlowRecord(o))
                .filter(o -> !logicalFlowExIDs.contains(o.getExternalId()))
                .collect(Collectors.toList());
        //todo: make sure this works with updating already existing logical flows
        System.out.println("Logical Flows Created");
        int logicalFlowCount = summarizeResults(tx.
                batchInsert(logicalFlowRecords)
                .execute());

        HashMap<String, Long> lfExIDToID = getLfExIDToID(tx);
        // with Logical FLow ID's, add them to the overviews


        Collection<PhysicalFlowOverview> newOverviewsWithLFID = overviews
                .stream()
                .map(o -> ImmutablePhysicalFlowOverview.copyOf(o)
                        .withLogical_flow_id(lfExIDToID.get(o.logical_flow_external_id()))
                        .withId(o.id())
                )
                .collect(Collectors.toList());

        List<LogicalFlowDecoratorRecord> logicalFlowDecoratorRecords = newOverviewsWithLFID
                .stream()
                .map(o -> toLogicalFlowDecoratorRecord(o))
                .collect(Collectors.toList());
        int logicalFlowDecorators = summarizeResults(tx
                .batchInsert(logicalFlowDecoratorRecords)
                .execute());

        List<PhysicalSpecificationRecord> physicalSpecificationRecords = newOverviewsWithLFID
                .stream()
                        .map(o -> toPhysicalSpecification(o))
                                .collect(Collectors.toList());

        int physicalSpecifciationCount = summarizeResults(tx
                .batchInsert(physicalSpecificationRecords)
                .execute());

        HashMap<String, Long> physicalFlowToSpecMap = getFlowToSpecMap(tx);
        Collection<PhysicalFlowOverview> newOverviewsWithSpecIDs = newOverviewsWithLFID
                .stream()
                        .map(o -> ImmutablePhysicalFlowOverview.copyOf(o)
                                .withPhysical_specification_id(physicalFlowToSpecMap.get(o.physical_specification_external_id())))
                .collect(Collectors.toList());
        List<PhysicalFlowRecord> physicalFlowRecords = newOverviewsWithSpecIDs
                .stream()
                        .map(o -> toPhysicalFLow(o))
                                .collect(Collectors.toList());
        int physicalFlowCount = summarizeResults(tx
                .batchInsert(physicalFlowRecords)
                .execute());



        System.out.println("Logical Flow Decorators Created");


        System.out.println("Logical Flows:         : " + logicalFlowCount);
        System.out.println("Logical Flow Decorators: " + logicalFlowDecorators);
        System.out.println("Physical Specifications: " + physicalSpecifciationCount);
        System.out.println("Physical Flows         : " + physicalFlowCount);



    }

    private HashMap<String, Long> getFlowToSpecMap(DSLContext tx) {
        return new HashMap<>(tx.select(PHYSICAL_SPECIFICATION.EXTERNAL_ID, PHYSICAL_SPECIFICATION.ID)
                .from(PHYSICAL_SPECIFICATION)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getValue(PHYSICAL_SPECIFICATION.EXTERNAL_ID),
                        r -> r.getValue(PHYSICAL_SPECIFICATION.ID),
                        (a,b) -> a
                )));

    }

    private static HashMap<String, Long> getLfExIDToID(DSLContext tx) {
        return new HashMap<>(tx.select(LOGICAL_FLOW.EXTERNAL_ID, LOGICAL_FLOW.ID)
                .from(LOGICAL_FLOW)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getValue(LOGICAL_FLOW.EXTERNAL_ID),
                        r -> r.getValue(LOGICAL_FLOW.ID),
                        (a, b) -> a // first value
                )));
    }

    @Override
    protected void updateExisting(DSLContext tx, Collection<PhysicalFlowOverview> existingRecords) {

    }

    @Override
    protected void deleteExisting(DSLContext tx, Collection<PhysicalFlowOverview> existingRecords) {

    }

    @Override
    protected PhysicalFlowOverview toOverview(Record r) {
        Record record = r;

        try {
            return ImmutablePhysicalFlowOverview.builder()
                    .external_id(record.getValue(PHYSICAL_FLOW.EXTERNAL_ID))
                    .description(Optional.ofNullable(record.getValue(PHYSICAL_FLOW.DESCRIPTION)))
                    .basis_offset(record.getValue(PHYSICAL_FLOW.BASIS_OFFSET))
                    .transport(record.getValue(PHYSICAL_FLOW.TRANSPORT))
                    .frequency(record.getValue(PHYSICAL_FLOW.FREQUENCY))
                    .logical_flow_id(Optional.ofNullable(record.getValue(LOGICAL_FLOW.ID)))
                    .source_entity_id(record.getValue(LOGICAL_FLOW.SOURCE_ENTITY_ID))
                    .target_entity_id(record.getValue(LOGICAL_FLOW.TARGET_ENTITY_ID))
                    .source_entity_kind(record.getValue(LOGICAL_FLOW.SOURCE_ENTITY_KIND))
                    .target_entity_kind(record.getValue(LOGICAL_FLOW.TARGET_ENTITY_KIND))
                    .provenance(record.getValue(PHYSICAL_FLOW.PROVENANCE))
                    .entity_lifecycle_status(record.getValue(PHYSICAL_FLOW.ENTITY_LIFECYCLE_STATUS))
                    .physical_flow_is_removed(record.getValue(PHYSICAL_FLOW.IS_REMOVED))
                    .logical_flow_is_removed(record.getValue(LOGICAL_FLOW.IS_REMOVED))
                    .decorator_entity_kind(record.getValue(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND))
                    .id(record.getValue(PHYSICAL_FLOW.ID))
                    .format(record.getValue(PHYSICAL_SPECIFICATION.FORMAT))
                    .owning_entity_id(Optional.ofNullable(record.getValue(PHYSICAL_SPECIFICATION.OWNING_ENTITY_ID)))
                    .owning_entity_kind(record.getValue(PHYSICAL_SPECIFICATION.OWNING_ENTITY_KIND))
                    .physical_specification_id(record.getValue(PHYSICAL_SPECIFICATION.ID))
                    .build();
        } catch (Exception e) {
            System.out.println("Flow " + record.getValue(PHYSICAL_FLOW.ID) + " has an error with the field " + e.getMessage() + ":" + e.getCause() + " and will be ignored.");

        }
        return null;



    }



    private LogicalFlowRecord toLogicalFlowRecord(PhysicalFlowOverview overview){
        LogicalFlowRecord record = new LogicalFlowRecord();
        record.setExternalId(overview.logical_flow_external_id());
        record.setCreatedBy("physical-flow-loader");
        record.setCreatedAt( new Timestamp(System.currentTimeMillis()));
        record.setLastUpdatedBy("physical-flow-loader");
        record.setLastUpdatedAt(new Timestamp(System.currentTimeMillis()));
        record.setSourceEntityId(overview.source_entity_id().get()); // derived
        record.setTargetEntityId(overview.target_entity_id().get()); // derived
        record.setSourceEntityKind(overview.source_entity_kind()); // default = "APPLICATION"
        record.setTargetEntityKind(overview.target_entity_kind()); // default = "APPLICATION"
        record.setProvenance(overview.provenance()); // default = tbc
        record.setEntityLifecycleStatus(overview.entity_lifecycle_status()); // default = tbc
        record.setIsRemoved(overview.logical_flow_is_removed()); // default = false
        return record;

    }


    private LogicalFlowDecoratorRecord toLogicalFlowDecoratorRecord(PhysicalFlowOverview overview){
        LogicalFlowDecoratorRecord record = new LogicalFlowDecoratorRecord();
        if (overview.logical_flow_id().isPresent()){
            record.setLogicalFlowId(overview.logical_flow_id().get());
            record.setDecoratorEntityKind(overview.decorator_entity_kind());
            record.setDecoratorEntityId(overview.decorator_entity_id()); // Default 1L
            record.setRating(overview.decorator_rating()); // Default "NO_OPINION"
            record.setProvenance(overview.provenance());
            record.setLastUpdatedAt(new Timestamp(System.currentTimeMillis()));
            record.setLastUpdatedBy("physical-flow-loader");
        }



        return record;


    }


    private PhysicalSpecificationRecord toPhysicalSpecification(PhysicalFlowOverview overview){
        PhysicalSpecificationRecord record = new PhysicalSpecificationRecord();

        record.setExternalId(overview.physical_specification_external_id());
        record.setName(overview.name());
        record.setDescription(overview.description().orElse(null)); // not really parity
        record.setLastUpdatedAt(new Timestamp(System.currentTimeMillis()));
        record.setLastUpdatedBy("physical-flow-loader");
        record.setFormat(overview.format());
        record.setOwningEntityId(overview.owning_entity_id().orElse(overview.source_entity_id().get()));
        record.setOwningEntityKind(overview.owning_entity_kind());
        record.setProvenance(overview.provenance());
        record.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        record.setCreatedBy("physical-flow-loader");
        // need to add the ID to the processing

        return record;
    }


    private PhysicalFlowRecord toPhysicalFLow(PhysicalFlowOverview overview){
        PhysicalFlowRecord record = new PhysicalFlowRecord();
        record.setExternalId(overview.external_id());
        record.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        record.setCreatedBy("physical-flow-loader");
        record.setBasisOffset(overview.basis_offset());
        record.setCriticality(overview.criticality().name());
        record.setId(overview.id().orElse(nextId++));
        record.setSpecificationId(overview.physical_specification_id().orElse(null));
        record.setDescription(overview.description().orElse(null));
        record.setEntityLifecycleStatus(overview.entity_lifecycle_status());
        record.setFrequency(overview.frequency());
        record.setIsRemoved(overview.physical_flow_is_removed());
        record.setLastUpdatedAt(new Timestamp(System.currentTimeMillis()));
        record.setLastUpdatedBy("physical-flow-loader");
        record.setCreatedBy("physical-flow-loader"); //todo: alter to take table value first
        record.setTransport(overview.transport());
        record.setProvenance(overview.provenance());
        record.setLogicalFlowId(overview.logical_flow_id().orElse(null));
        return record;

    }


    private HashMap<String, Long> getNameAppIdMap(DSLContext tx){
        Result<Record2<String, Long>> records = tx.select(APPLICATION.NAME, APPLICATION.ID)
                .from(APPLICATION)
                .fetch();
        return new HashMap<>(records.stream()
                .collect(
                        Collectors.toMap(
                                r -> r.getValue(APPLICATION.NAME),
                                r -> r.getValue(APPLICATION.ID),
                                (a,b) -> a
                )));
    }

}
