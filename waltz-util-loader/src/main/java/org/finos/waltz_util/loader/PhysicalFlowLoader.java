package org.finos.waltz_util.loader;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.records.*;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.*;

public class PhysicalFlowLoader extends Loader<PhysicalFlowOverview> {

    HashMap<List<Object>, Long> logicalFlowKeys = new HashMap<>();
    HashMap<List<Object>, Long> logicalFlowDecoratorKeys = new HashMap<>();
    HashMap<String, Long> physicalSpecMap = new HashMap<>();

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


        try (InputStream resourceAsStream = Files.newInputStream(Paths.get(resource))) {
            MappingIterator<PhysicalFlowOverview> iterator = reader.readValues(resourceAsStream); // this is a stream of objects
            while (iterator.hasNext()) {
                try {
                    PhysicalFlowOverview next = iterator.next();
                    overviews.add(next);
                } catch (Exception e) {
                    System.out.println(e);

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
        //HashMap<String, Long> lfExIDToID = getLfExIDToID(dsl);
        HashMap<String, Long> physicalFlowToSpecMap = getFlowToSpecMap(dsl);
        HashMap<String, Long> pfExIDToID = pfExIDToID(dsl);
        HashMap<List<Object>, Long> logicalFlowToIDMap = logicalFlowToKeyMap(dsl);


        // fill in data for each overview
        for (PhysicalFlowOverview p : rawOverviews) {

            // check to make sure that the maps have the fields.
            if (!(nameToAppID.containsKey(p.source_entity_name().get()))) {
                System.out.println("The source entity name " + p.source_entity_name().get() + " cannot be found.");
                continue;
            }

            if (!(nameToAppID.containsKey(p.target_entity_name().get()))) {
                System.out.println("The target entity name " + p.target_entity_name().get() + " cannot be found.");
                continue;
            }




            PhysicalFlowOverview newO = ImmutablePhysicalFlowOverview.copyOf(p)
                    .withId(Optional.ofNullable(pfExIDToID.get(p.external_id())))
                    .withSource_entity_id(nameToAppID.get(p.source_entity_name().get()))
                    .withTarget_entity_id(nameToAppID.get(p.target_entity_name().get()))
                    .withSource_entity_name(p.source_entity_name().get())
                    .withTarget_entity_name(p.target_entity_name().get())
                    .withPhysical_specification_id(Optional.ofNullable(physicalFlowToSpecMap.get(p.physical_specification_external_id())));

            List<Object> lfKey = Arrays.asList(newO.source_entity_id().get(), newO.source_entity_kind(), newO.target_entity_id().get(), newO.target_entity_kind());

            PhysicalFlowOverview newO2 = ImmutablePhysicalFlowOverview.copyOf(newO)
                    .withLogical_flow_id(Optional.ofNullable(logicalFlowToIDMap.get(lfKey)))
                    .withLogical_flow_decorator_id(Optional.ofNullable(logicalFlowToDecoratorMap.get(logicalFlowToIDMap.get(lfKey))));

            processedOverviews.add(newO2);

        }

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
                        (a, b) -> a)
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
                        (a, b) -> a
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

        // link logical flows by keyset
        logicalFlowKeys = logicalFlowToKeyMap(tx);
        logicalFlowDecoratorKeys = logicalFlowDecoratorToKeyMap(tx);
        for (PhysicalFlowOverview overview : OU){
            List<Object> lfKeyArray = Arrays.asList(overview.source_entity_id().get(), overview.source_entity_kind(), overview.target_entity_id().get(), overview.target_entity_kind());
            List<Object> decoratorKeyArray = Arrays.asList(overview.logical_flow_id().get(), overview.decorator_entity_kind(), overview.decorator_entity_id());
            overview = ImmutablePhysicalFlowOverview.copyOf(overview)
                    .withLogical_flow_id(logicalFlowKeys.get(lfKeyArray))
                    .withLogical_flow_decorator_id(logicalFlowDecoratorKeys.get(decoratorKeyArray));
        }

        return OU;
    }

    @Override
    protected void insertNew(DSLContext tx, Collection<PhysicalFlowOverview> overviews) {

        if (logicalFlowKeys.isEmpty()){
            logicalFlowKeys = logicalFlowToKeyMap(tx);
        }
        if (logicalFlowDecoratorKeys.isEmpty()){
            logicalFlowDecoratorKeys = logicalFlowDecoratorToKeyMap(tx);
        }
        int logicalFlowCount = 0;
        int logicalFlowDecorators = 0;
        int physicalSpecifciationCount = 0;
        int physicalFlowCount = 0;
        physicalSpecMap = getFlowToSpecMap(tx);

        for (PhysicalFlowOverview overview : overviews){
            try {




                logicalFlowCount += softInsertLogicalFlow(overview, logicalFlowKeys, tx); // logicalFLow may already exist

                PhysicalFlowOverview decoratorInformationO = ImmutablePhysicalFlowOverview.copyOf(overview)
                        .withLogical_flow_id(logicalFlowKeys.get(Arrays.asList(overview.source_entity_id().get(), overview.source_entity_kind(), overview.target_entity_id().get(), overview.target_entity_kind())));



                logicalFlowDecorators += softInsertLogicalFlowDecorator(decoratorInformationO, logicalFlowDecoratorKeys, tx); // always new for new physicalFLow
                physicalSpecifciationCount += softInsertPhysicalFlowSpecification(overview, new HashSet<>(), tx); // always new for new physicalFLow
                // update flow to include new ID's
                PhysicalFlowOverview updatedOverview = ImmutablePhysicalFlowOverview.copyOf(overview)
                        .withLogical_flow_id(logicalFlowKeys.get(Arrays.asList(overview.source_entity_id().get(), overview.source_entity_kind(), overview.target_entity_id().get(), overview.target_entity_kind())))
                        .withLogical_flow_decorator_id(logicalFlowDecoratorKeys.get(Arrays.asList(overview.logical_flow_id().get(), overview.decorator_entity_kind(), overview.decorator_entity_id())))
                        .withPhysical_specification_id(physicalSpecMap.get(overview.physical_specification_external_id()));


                physicalFlowCount += softInsertPhysicalFlow(updatedOverview, new HashSet<>(), tx); // always new for new physicalFLow
            }
            catch (Exception e){
                System.out.println("The physical flow with external ID '" + overview.external_id() + "' has failed to load");
                throw new RuntimeException(e); // This will cause the transaction to roll back
            }
        }


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
                        (a, b) -> a
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
                    Long logicalFlowID = null;
                    // check if logical flow already exists:
                    if (logicalFlowKeys.isEmpty()) {
                        logicalFlowKeys = logicalFlowToKeyMap(transactionContext);
                    } else {
                        List<Object> keyArray = Arrays.asList(overview.source_entity_id().get(), overview.source_entity_kind(), overview.target_entity_id().get(), overview.target_entity_kind());
                        if (logicalFlowKeys.containsKey(keyArray)) {
                            logicalFlowID = logicalFlowKeys.get(keyArray);
                        }
}
                    Long decoratorID = null;
                    if (logicalFlowDecoratorKeys.isEmpty()) {
                        logicalFlowDecoratorKeys = logicalFlowDecoratorToKeyMap(transactionContext);
                    } else {
                        List<Object> keyArray = Arrays.asList(logicalFlowID, overview.decorator_entity_kind(), overview.decorator_entity_id());
                        if (logicalFlowDecoratorKeys.containsKey(keyArray)) {
                            decoratorID = logicalFlowDecoratorKeys.get(keyArray);
                        }
                    }
                    try {
                        if (logicalFlowID == null) {
                            softInsertLogicalFlow(overview, logicalFlowKeys, transactionContext);
                            logicalFlowID = logicalFlowKeys.get(Arrays.asList(overview.source_entity_id().get(), overview.source_entity_kind(), overview.target_entity_id().get(), overview.target_entity_kind()));
                        }
                        PhysicalFlowOverview lfO = ImmutablePhysicalFlowOverview.copyOf(overview)
                                .withLogical_flow_id(logicalFlowID);

                        if (decoratorID == null) {

                            softInsertLogicalFlowDecorator(lfO, logicalFlowDecoratorKeys, transactionContext);
                            decoratorID = logicalFlowDecoratorKeys.get(Arrays.asList(logicalFlowID, overview.decorator_entity_kind(), overview.decorator_entity_id()));
                        }

                        PhysicalFlowOverview decoratorInformationO = ImmutablePhysicalFlowOverview.copyOf(lfO)
                                .withLogical_flow_decorator_id(decoratorID);

                        softInsertPhysicalFlowSpecification(decoratorInformationO, physicalSpecIDs, transactionContext);

                        softInsertPhysicalFlow(decoratorInformationO, physicalFlowIDs, transactionContext);




                    } catch (Exception e) {
                        System.out.println("The physical flow with external ID '" + overview.external_id() + "' has failed to update");
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


    private LogicalFlowRecord toLogicalFlowRecord(PhysicalFlowOverview overview) {
        LogicalFlowRecord record = new LogicalFlowRecord();

        record.setCreatedBy("physical-flow-loader");
        record.setCreatedAt(new Timestamp(System.currentTimeMillis()));
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


    private LogicalFlowDecoratorRecord toLogicalFlowDecoratorRecord(PhysicalFlowOverview overview) {
        LogicalFlowDecoratorRecord record = new LogicalFlowDecoratorRecord();
        if (overview.logical_flow_id().isPresent()) {
            //record.setId(overview.logical_flow_decorator_id().orElse(null));
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


    private PhysicalSpecificationRecord toPhysicalSpecification(PhysicalFlowOverview overview) {
        PhysicalSpecificationRecord record = new PhysicalSpecificationRecord();

        record.setExternalId(overview.physical_specification_external_id());
        record.setName(overview.name());
        record.setDescription(overview.description().orElse("No Description Provided"));
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


    private PhysicalFlowRecord toPhysicalFLow(PhysicalFlowOverview overview) {
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


    private HashMap<String, Long> getNameAppIdMap(DSLContext tx) {
        Result<Record2<String, Long>> records = tx.select(APPLICATION.NAME, APPLICATION.ID)
                .from(APPLICATION)
                .fetch();
        return new HashMap<>(records.stream()
                .collect(
                        Collectors.toMap(
                                r -> r.getValue(APPLICATION.NAME),
                                r -> r.getValue(APPLICATION.ID),
                                (a, b) -> a
                        )));
    }


    private int softInsertLogicalFlow(PhysicalFlowOverview overview, HashMap<List<Object>, Long> existingflows, DSLContext tx) throws Exception {
        LogicalFlowRecord record = toLogicalFlowRecord(overview);

        List<Object> primaryKey = Arrays.asList(record.getSourceEntityId(), record.getSourceEntityKind(), record.getTargetEntityId(), record.getTargetEntityKind());

        try {
            if (existingflows.containsKey(primaryKey)) {
                return 0;
            } else {
                tx.insertInto(LOGICAL_FLOW).set(record).execute();
                // add new key to map
                Long newID = tx.select(LOGICAL_FLOW.ID).from(LOGICAL_FLOW).where(LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(record.getSourceEntityId()).and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(record.getSourceEntityKind())).and(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(record.getTargetEntityId())).and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(record.getTargetEntityKind()))).fetchOne().getValue(LOGICAL_FLOW.ID);
                logicalFlowKeys.put(primaryKey, newID);

            }
        } catch (Exception e) {
            System.out.println(record);
            System.out.println("Error inserting logical flow: " + e.getMessage());
            throw new Exception(e);
        }
        return 0;

    }

    private int softInsertLogicalFlowDecorator(PhysicalFlowOverview overview,HashMap<List<Object>, Long> decoratorKeyMap, DSLContext tx) throws Exception {
        LogicalFlowDecoratorRecord record = toLogicalFlowDecoratorRecord(overview);

        List<Object> primaryKey = Arrays.asList(record.getLogicalFlowId(), record.getDecoratorEntityKind(), record.getDecoratorEntityId());

        try{
            if (decoratorKeyMap.containsKey(primaryKey)){
                return 0;
            } else {

                tx.insertInto(LOGICAL_FLOW_DECORATOR).set(record).execute();
                // add new key to map
                Long newID = tx.select(LOGICAL_FLOW_DECORATOR.ID).from(LOGICAL_FLOW_DECORATOR).where(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(record.getLogicalFlowId()).and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(record.getDecoratorEntityKind())).and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID.eq(record.getDecoratorEntityId()))).fetchOne().getValue(LOGICAL_FLOW_DECORATOR.ID);
                logicalFlowDecoratorKeys.put(primaryKey, newID);
            }
        } catch (Exception e) {
            System.out.println(record);
            System.out.println("Error inserting logical flow decorator: " + e.getMessage());
            throw new Exception(e);
        }


        return 0;

    }

    private int softInsertPhysicalFlowSpecification(PhysicalFlowOverview overview, Set<Long> existingIds, DSLContext tx) throws Exception {
        PhysicalSpecificationRecord record = toPhysicalSpecification(overview);
        try {
            if (overview.physical_specification_id().isPresent()){
                if (existingIds.contains(overview.physical_specification_id().get())) {
                    return tx.update(PHYSICAL_SPECIFICATION).set(record).where(PHYSICAL_SPECIFICATION.ID.eq(overview.physical_specification_id().get())).execute();
                }}
            else {
                    System.out.println(record);
                    tx.insertInto(PHYSICAL_SPECIFICATION).set(record).execute();
                    Long newID = tx.select(PHYSICAL_SPECIFICATION.ID).from(PHYSICAL_SPECIFICATION).where(PHYSICAL_SPECIFICATION.EXTERNAL_ID.eq(record.getExternalId())).fetchOne().getValue(PHYSICAL_SPECIFICATION.ID);
                    physicalSpecMap.put(record.getExternalId(), newID);
                    return 1;

            }
        } catch (Exception e) {
            System.out.println("Error inserting physical specification: " + e.getMessage());
            System.out.println(record);
            throw new Exception(e);
        }
        return 0;

    }

    private int softInsertPhysicalFlow(PhysicalFlowOverview overview, Set<Long> existingIds, DSLContext tx) throws Exception {
        PhysicalFlowRecord record = toPhysicalFLow(overview);


        try {
            if (overview.id().isPresent()){
                if (existingIds.contains(overview.id().get())) {
                    return tx.update(PHYSICAL_FLOW).set(record).where(PHYSICAL_FLOW.ID.eq(overview.id().get())).execute();
                }}
            else {
                System.out.println(record);
                return tx.insertInto(PHYSICAL_FLOW).set(record).execute();
            }
        } catch (Exception e) {
            System.out.println("Error inserting physical flow: " + e.getMessage());
            System.out.println(record);
            throw new Exception(e);
        }
        return 0;
    }


    private HashMap<List<Object>, Long> logicalFlowToKeyMap(DSLContext tx) {
        // select all flow keys from db, along with their id
        Set<List<Object>> flows = new HashSet<>(tx.select(LOGICAL_FLOW.ID, LOGICAL_FLOW.SOURCE_ENTITY_ID, LOGICAL_FLOW.SOURCE_ENTITY_KIND, LOGICAL_FLOW.TARGET_ENTITY_ID, LOGICAL_FLOW.TARGET_ENTITY_KIND)
                .from(LOGICAL_FLOW)
                .fetch()
                .map(r -> {
                            List<Object> list = new ArrayList<>();
                            list.add(r.getValue(LOGICAL_FLOW.ID));
                            list.add(r.getValue(LOGICAL_FLOW.SOURCE_ENTITY_ID));
                            list.add(r.getValue(LOGICAL_FLOW.SOURCE_ENTITY_KIND));
                            list.add(r.getValue(LOGICAL_FLOW.TARGET_ENTITY_ID));
                            list.add(r.getValue(LOGICAL_FLOW.TARGET_ENTITY_KIND));
                            return list;
                        }
                ));

        System.out.println(flows);

        HashMap<List<Object>, Long> flowMap = new HashMap<>();
        for (List<Object> flow : flows) {
            flowMap.put(Arrays.asList(flow.get(1), flow.get(2), flow.get(3), flow.get(4)), (Long) flow.get(0));
        }

        return flowMap;
    }

    private HashMap<List<Object>, Long> logicalFlowDecoratorToKeyMap(DSLContext tx) {
        // need logicalFlow id, decorator entity kind, decorator entity id
        Set<List<Object>> flowDecorators = new HashSet<>(tx.select(LOGICAL_FLOW_DECORATOR.ID, LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID, LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND, LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID)
                .from(LOGICAL_FLOW_DECORATOR)
                .fetch()
                .map(r -> {
                            List<Object> list = new ArrayList<>();
                            list.add(r.getValue(LOGICAL_FLOW_DECORATOR.ID));
                            list.add(r.getValue(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID));
                            list.add(r.getValue(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND));
                            list.add(r.getValue(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID));
                            return list;
                        }
                ));
        HashMap<List<Object>, Long> flowDecoratorMap = new HashMap<>();
        for (List<Object> flow : flowDecorators) {
            flowDecoratorMap.put(Arrays.asList(flow.get(1), flow.get(2), flow.get(3)), (Long) flow.get(0));
        }
        return flowDecoratorMap;
    }
}
