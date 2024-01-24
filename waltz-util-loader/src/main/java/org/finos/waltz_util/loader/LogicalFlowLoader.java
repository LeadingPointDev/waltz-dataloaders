package org.finos.waltz_util.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.schema.tables.records.LogicalFlowDecoratorRecord;
import org.finos.waltz_util.schema.tables.records.LogicalFlowRecord;
import org.immutables.value.Value;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.*;


@Value.Immutable
@JsonSerialize(as = ImmutableLogicalFlowOverview.class)
@JsonDeserialize(as = ImmutableLogicalFlowOverview.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogicalFlowLoader extends Loader<LogicalFlowOverview>{

    private Long nextId;
    public LogicalFlowLoader() {
        super();
    }

    public LogicalFlowLoader(String resource) {
        super(resource);
    }

    @Override
    protected DiffResult<LogicalFlowOverview> getDiffResults(Set<LogicalFlowOverview> existingOverviews, Set<LogicalFlowOverview> desiredOverviews) {
        return DiffResult.mkDiff(
                existingOverviews,
                desiredOverviews,
                LogicalFlowOverview::external_id,
                Object::equals);
    }

    @Override
    protected void validate(Set<LogicalFlowOverview> overviews) {

    }

    @Override
    protected Set<LogicalFlowOverview> loadFromFile() throws IOException {
        Set<LogicalFlowOverview> overviews = new HashSet<>();
        ObjectMapper mapper = getJsonMapper();

        ObjectReader reader = mapper.readerFor(LogicalFlowOverview.class);


        try (InputStream resourceAsStream = new FileInputStream(resource)){
            MappingIterator<LogicalFlowOverview> iterator = reader.readValues(resourceAsStream); // this is a stream of objects
            while (iterator.hasNext()){
                try {
                    LogicalFlowOverview next = iterator.next();
                    overviews.add(next);
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        }
        return overviews.stream()
                .filter(r -> r.external_id().isPresent())
                .collect(Collectors.toSet());

    }

    @Override
    protected Set<LogicalFlowOverview> processOverviews(Set<LogicalFlowOverview> rawOverviews) {


        HashMap<String, Long> applicationNameToIDMap = getApplicationNameToIDMap(dsl);
        System.out.println(applicationNameToIDMap);

        Set<LogicalFlowOverview> processedOverviews = new HashSet<>();



        //rawOverviews = rawOverviews.stream()
        //        .filter(o -> o.target_entity_name().orElse("").equals("LPL_DIS_CONTROLLER"))
        //        .collect(Collectors.toSet());



        for (LogicalFlowOverview overview : rawOverviews) {
            // for each overview, get the source and target names, and find the database id's for each
            // for each overview source_entity_name, find the id in the application table
            // for each overview target_entity_name, find the id in the application table
            if (overview == null) {
                System.out.println("Overview is null");
                continue;
            }
            if (overview.source_entity_name().isPresent() && overview.target_entity_name().isPresent()) {
                String sourceName = overview.source_entity_name().get();
                String targetName = overview.target_entity_name().get();
                if (applicationNameToIDMap.containsKey(sourceName) && applicationNameToIDMap.containsKey(targetName)) {
                    Long sourceID = applicationNameToIDMap.get(sourceName);
                    Long targetID = applicationNameToIDMap.get(targetName);
                    processedOverviews.add(ImmutableLogicalFlowOverview.builder()
                            .source_entity_id(sourceID)
                            .target_entity_id(targetID)
                            .source_entity_kind(overview.source_entity_kind())
                            .target_entity_kind(overview.target_entity_kind())
                            .external_id(overview.external_id())
                            .build());
                }
                else {
                    System.out.println("Cannot Identify the source or target by name for: " + overview);
                }
            }
            else {
                System.out.println("Missing source or target name for overview: " + overview);
            }

        }


        return processedOverviews;

    }

    @Override
    protected Set<LogicalFlowOverview> getExistingRecordsAsOverviews(DSLContext tx) {

        // get max id from logical_flow table
        nextId = tx.select(LOGICAL_FLOW.ID.max())
                .from(LOGICAL_FLOW)
                .fetchOne()
                .getValue(LOGICAL_FLOW.ID.max());
        nextId = nextId == null ? 1 : nextId + 1;


        return tx.select(LOGICAL_FLOW.fields())
                .from(LOGICAL_FLOW)
                .fetch()
                .stream()
                .map(r -> toOverview(r))
                .collect(Collectors.toSet());

    }

    @Override
    protected void insertNew(DSLContext tx, Collection<LogicalFlowOverview> newRecords) {
        Set<String> recordExternalIDs = newRecords
                .stream()
                .map(l -> {
                    if (l.external_id().isPresent()) {
                        return l.external_id().get();
                    }
                    else {
                        return null;
                    }
                })
                .collect(Collectors.toSet());

        List<LogicalFlowRecord> recordsToUpdate = newRecords
                .stream()
                .map(d -> toJooqRecord(d))
                .collect(Collectors.toList());


        List<LogicalFlowDecoratorRecord> decorators = new ArrayList<>();
        for (LogicalFlowRecord record : recordsToUpdate) {
            LogicalFlowDecoratorRecord decorator = new LogicalFlowDecoratorRecord();
            decorator.setLogicalFlowId(record.getId());
            decorator.setProvenance("LPL-loader");
            decorator.setLastUpdatedBy("LPL-loader");
            decorator.setLastUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            decorator.setDecoratorEntityKind("DATA_TYPE");
            decorator.setDecoratorEntityId(1L);
            decorators.add(decorator);
        }

        int recordsCreated = summarizeResults(tx
                .batchInsert(recordsToUpdate)
                .execute());


        List<Long> logicalFlowIds = tx.select(LOGICAL_FLOW.ID)
                .from(LOGICAL_FLOW)
                .where(LOGICAL_FLOW.EXTERNAL_ID.in(recordExternalIDs))
                .fetch()
                .map(r -> r.getValue(LOGICAL_FLOW.ID));

        tx.deleteFrom(LOGICAL_FLOW_DECORATOR)
                .where(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.in(logicalFlowIds)).execute();
        int decoratorsCreated = summarizeResults(tx
                .batchInsert(decorators)
                .execute());

        System.out.println("Flows Created: " + recordsCreated);
        System.out.println("Decorators Created: " + decoratorsCreated);

    }

    @Override
    protected void updateExisting(DSLContext tx, Collection<LogicalFlowOverview> existingRecords) {
        List<LogicalFlowRecord> recordsToUpdate = existingRecords
                .stream()
                .map(d -> {
                    LogicalFlowRecord r = toJooqRecord(d);
                    r.changed(LOGICAL_FLOW.EXTERNAL_ID, false);
                    return r;
                })
                .collect(Collectors.toList());

        int numUpdated = summarizeResults(tx
                .batchUpdate(recordsToUpdate)
                .execute());

        System.out.println("Records Updated: " + numUpdated);
    }

    @Override
    protected void deleteExisting(DSLContext tx, Collection<LogicalFlowOverview> existingRecords) {
        Set<String> externalIDsToRemove = existingRecords
                .stream()
                .filter(r -> r.external_id().isPresent())
                .map(r -> r.external_id().get())
                .collect(Collectors.toSet());

        // get ID's of logical flows
        List<Long> flowIds = tx.select(LOGICAL_FLOW.ID)
                .from(LOGICAL_FLOW)
                .where(LOGICAL_FLOW.EXTERNAL_ID.in(externalIDsToRemove))
                .fetch()
                .map(r -> r.getValue(LOGICAL_FLOW.ID));

        int flowsDeleted = tx.deleteFrom(LOGICAL_FLOW)
                .where(LOGICAL_FLOW.EXTERNAL_ID.in(externalIDsToRemove))
                .execute();

        int decoratorsDeleted = tx.deleteFrom(LOGICAL_FLOW_DECORATOR)
                        .where(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.in(flowIds))
                                .execute();
        System.out.println("Records Deleted from Logical Flow: " + flowsDeleted);
        System.out.println("Records Deleted from Logical Flow Decorator: " );
    }

    @Override
    protected LogicalFlowOverview toOverview(Record record) {
        LogicalFlowRecord logicalFlowRecord = record.into(LOGICAL_FLOW);

        return ImmutableLogicalFlowOverview.builder()
                .source_entity_id(logicalFlowRecord.getSourceEntityId())
                .target_entity_id(logicalFlowRecord.getTargetEntityId())
                .source_entity_kind(logicalFlowRecord.getSourceEntityKind())
                .target_entity_kind(logicalFlowRecord.getTargetEntityKind())
                .external_id(Optional.ofNullable(logicalFlowRecord.getExternalId()))
                .build();


    }
    // need to add to "physical_flow" table
    // create link with specficiation_id
    // create link with logical_flow_id ??

    public static HashMap<String, Long> getApplicationNameToIDMap(DSLContext tx) {
        Result<Record> results = tx.
                select(APPLICATION.fields())
                .from(APPLICATION)
                        .fetch();

        // create name to ID hashmap

        HashMap<String, Long> nameToIDMap = new HashMap<>();
        for (Record r : results) {
            nameToIDMap.put(r.getValue(APPLICATION.NAME), r.getValue(APPLICATION.ID));
        }

        return nameToIDMap;

    }

    private LogicalFlowRecord toJooqRecord(LogicalFlowOverview overview) {
       LogicalFlowRecord record = new LogicalFlowRecord();
       record.setId(nextId++);
       record.setSourceEntityId(overview.source_entity_id().get());
       record.setTargetEntityId(overview.target_entity_id().get());
       record.setSourceEntityKind(overview.source_entity_kind());
       record.setTargetEntityKind(overview.target_entity_kind());
       record.setExternalId(overview.external_id().orElse(null));
       record.setProvenance("LPL-loader");
       record.setLastUpdatedBy("LPL-loader");
       record.setLastUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
       record.setCreatedBy("LPL-loader");
       record.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
       return record;
    }
}
