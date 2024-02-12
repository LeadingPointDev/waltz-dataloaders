package org.finos.waltz_util.loader;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.LogicalFlow;
import org.finos.waltz_util.schema.tables.records.*;
;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

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

        Set<PhysicalFlowOverview> processedOverviews = new HashSet<>();
        // get max id from logical_flow table





        //Getting correct maps here:
        HashMap<String, Long> nameToAppID = getNameAppIdMap(dsl);
        HashMap<Long, Long> logicalFlowToDecoratorMap = getFlowDecoratorRelationship(dsl);
        HashMap<String, Long> lfExIDToID = getLfExIDToID(dsl);
        HashMap<String, Long> physicalFlowToSpecMap = getFlowToSpecMap(dsl);
        HashMap<String, Long> pfExIDToID = pfExIDToID(dsl);




        // fill in data for each overview
        for (PhysicalFlowOverview p : rawOverviews){


            PhysicalFlowOverview newO = ImmutablePhysicalFlowOverview.copyOf(p)
                    .withId(pfExIDToID.get(p.external_id()))
                    .withSource_entity_id(nameToAppID.get(p.source_entity_name().get()))
                    .withTarget_entity_id(nameToAppID.get(p.target_entity_name().get()))
                    .withSource_entity_name(p.source_entity_name().get())
                    .withTarget_entity_name(p.target_entity_name().get())
                    .withLogical_flow_id(lfExIDToID.get(p.logical_flow_external_id()))
                    .withLogical_flow_decorator_id(Optional.ofNullable(logicalFlowToDecoratorMap.get(lfExIDToID.get(p.logical_flow_external_id()))))
                    .withPhysical_specification_id(Optional.ofNullable(physicalFlowToSpecMap.get(p.physical_specification_external_id())));




            // do stuff
            //-----------
            processedOverviews.add(newO);

        }
        // return the set of overviews
        return processedOverviews;
    }

    private HashMap<String, Long> pfExIDToID(DSLContext tx) {
        return new HashMap<>(tx.select(PHYSICAL_FLOW.EXTERNAL_ID, PHYSICAL_FLOW.ID)
                .from(PHYSICAL_FLOW)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getValue(PHYSICAL_FLOW.EXTERNAL_ID),
                        r -> r.getValue(PHYSICAL_FLOW.ID),
                        (a,b) -> a)
                ));
    }

    private HashMap<Long, Long> getFlowDecoratorRelationship(DSLContext tx) {
        return new HashMap<>(tx.select(LOGICAL_FLOW_DECORATOR.ID, LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID)
                .from(LOGICAL_FLOW_DECORATOR)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getValue(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID),
                        r -> r.getValue(LOGICAL_FLOW_DECORATOR.ID),
                        (a,b) -> a
                )));
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
    protected void updateExisting(DSLContext tx, Collection<PhysicalFlowOverview> existingOverviews) {
        // for each overview, apply soft update function




        Set<Long> logicalFlowIDs = existingOverviews.stream()
                .map(o -> o.logical_flow_id().orElse(null))
                .collect(Collectors.toSet());
        Set<Long> loigcalFlowDecoratorIDs = existingOverviews.stream()
                .map(o -> o.logical_flow_decorator_id().orElse(null))
                .collect(Collectors.toSet());
        Set<Long> physicalSpecIDs = existingOverviews.stream()
                .map(o -> o.physical_specification_id().orElse(null))
                .collect(Collectors.toSet());
        Set<Long> physicalFlowIDs = existingOverviews.stream()
                .map(o -> o.id().orElse(null))
                .collect(Collectors.toSet());

        int logicalFlowCount = 0;
        int logicalFlowDecoratorCount = 0;
        int physicalSpecCount = 0;
        int physicalFlowCount = 0;

        for (PhysicalFlowOverview overview : existingOverviews) {
            // logical flow
            // logical flow decorator
            // physical spec
            // physical flow
            try {
                tx.transaction(configuration -> {

                    DSLContext transactionContext = DSL.using(configuration);
                    try {
                        boolean flag = true;

                        flag = softInsertLogicalFlow(overview, logicalFlowIDs, transactionContext) == 1 && flag;
                        flag = softInsertLogicalFlowDecorator(overview, loigcalFlowDecoratorIDs, transactionContext) == 1 && flag;
                        flag = softInsertPhysicalFlowSpecification(overview, physicalSpecIDs, transactionContext) == 1 && flag;
                        flag = softInsertPhysicalFlow(overview, physicalFlowIDs, transactionContext) == 1 && flag;

                        if (!flag) {
                            throw new RuntimeException("Failed to update physical flow");
                        }



                    } catch (Exception e) {
                        System.out.println("The physical flow with external ID '" + overview.external_id() +"' has failed to load" );
                        throw new RuntimeException(e); // This will cause the transaction to roll back
                    }

                });
            } catch (Exception e) {
                System.out.println("Transaction failed: " + e.getMessage());
            }
            logicalFlowCount++;
            logicalFlowDecoratorCount++;
            physicalSpecCount++;
            physicalFlowCount++;

        }
        System.out.println("Logical Flows Updated: " + logicalFlowCount + "\nLogical Flow Decorators Updated: " + logicalFlowDecoratorCount + "\nPhysical Specifications Updated: " + physicalSpecCount + "\nPhysical Flows Updated: " + physicalFlowCount);

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
                    .criticality(Criticality.valueOf(record.getValue(PHYSICAL_FLOW.CRITICALITY)))
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
                    .logical_flow_decorator_id(record.getValue(LOGICAL_FLOW_DECORATOR.ID))
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
            record.setId(overview.logical_flow_decorator_id().orElse(null));
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


    private int softInsertLogicalFlow(PhysicalFlowOverview overview,Set<Long> existingIds, DSLContext tx) throws Exception {
        LogicalFlowRecord record = toLogicalFlowRecord(overview);
        try{
            if (existingIds.contains(overview.logical_flow_id().get())){
                return tx.update(LOGICAL_FLOW).set(record).where(LOGICAL_FLOW.ID.eq(overview.logical_flow_id().get())).execute();
            }
            else{
                return tx.insertInto(LOGICAL_FLOW).set(record).execute();
            }
        } catch (Exception e){
            System.out.println("Error inserting logical flow: " + e.getMessage());
            System.out.println("Record: " + record);
            throw new Exception(e);
        }


    }

    private int softInsertLogicalFlowDecorator(PhysicalFlowOverview overview, Set<Long> existingIds, DSLContext tx) throws Exception {
        LogicalFlowDecoratorRecord record = toLogicalFlowDecoratorRecord(overview);
        try{
            if (existingIds.contains(overview.logical_flow_decorator_id().get())){
                return tx.update(LOGICAL_FLOW_DECORATOR).set(record).where(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(record.getLogicalFlowId())).execute();
            }
            else{
                return tx.insertInto(LOGICAL_FLOW_DECORATOR).set(record).execute();
            }
        } catch (Exception e){
            System.out.println(record);
            System.out.println("Error inserting logical flow decorator: " + e.getMessage());
            throw new Exception(e);
        }

    }

    private int softInsertPhysicalFlowSpecification(PhysicalFlowOverview overview, Set<Long> existingIds, DSLContext tx) throws Exception {
        PhysicalSpecificationRecord record = toPhysicalSpecification(overview);
        try{
            if (existingIds.contains(overview.physical_specification_id().get())){
                return tx.update(PHYSICAL_SPECIFICATION).set(record).where(PHYSICAL_SPECIFICATION.ID.eq(overview.physical_specification_id().get())).execute();
            }
            else{
                System.out.println(record);
                return tx.insertInto(PHYSICAL_SPECIFICATION).set(record).execute();
            }
        } catch (Exception e){

            System.out.println("Error inserting physical specification: " + e.getMessage());
            System.out.println(record);
            throw new Exception(e);
        }

    }

    private int softInsertPhysicalFlow(PhysicalFlowOverview overview, Set<Long> existingIds, DSLContext tx) throws Exception {
        PhysicalFlowRecord record = toPhysicalFLow(overview);
        try{
            if (existingIds.contains(overview.id().get())){
                return tx.update(PHYSICAL_FLOW).set(record).where(PHYSICAL_FLOW.ID.eq(overview.id().get())).execute();
            }
            else{
                return tx.insertInto(PHYSICAL_FLOW).set(record).execute();
            }
        } catch (Exception e){
            System.out.println(record);
            System.out.println("Error inserting physical flow: " + e.getMessage());
            throw new Exception(e);
        }

    }


}
