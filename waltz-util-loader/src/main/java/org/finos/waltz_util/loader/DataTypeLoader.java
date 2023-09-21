package org.finos.waltz_util.loader;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.schema.tables.records.DataTypeRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.DATA_TYPE;

public class DataTypeLoader {

    private final String resource;  // these are initialised at construction, and should not change therefore marked as final
    private final DSLContext dsl;

    public DataTypeLoader(String resource) {
        this.resource = resource;
        AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(DIBaseConfiguration.class);
        dsl = springContext.getBean(DSLContext.class);
    }


    public void synch() {
        dsl.transaction(ctx -> {
            DSLContext tx = ctx.dsl();

            Set<DataTypeOverview> existingDTs = getExistingDataTypes(tx);

            //Fallback DT value, if waltz changes the Unknown DT, this will need to be updated
            DataTypeOverview unknownDT = generateUnknownDT();



            Set<DataTypeOverview> desiredDTs = loadDTsFromFile();
            desiredDTs.add(unknownDT);


            DiffResult<DataTypeOverview> diff = DiffResult.mkDiff(
                    existingDTs,
                    desiredDTs,
                    DataTypeOverview::id,
                    Object::equals
            );


            insertNew(tx, diff.otherOnly());
            updateRelationships(tx, diff.differingIntersection());
            markDepreciated(tx, diff.waltzOnly());

        });


    }

    private DataTypeOverview generateUnknownDT() {
        return ImmutableDataTypeOverview
                .builder()
                .code("UNKNOWN")
                .name("Unknown")
                .description("Unknown")
                .id(1L)
                .concrete(true)
                .unknown(true)
                .depreciated(false)
                .build();
    }

    private void insertNew(DSLContext dsl, Collection<DataTypeOverview> toInsert) {
        List<DataTypeRecord> recordsToInsert = toInsert
                .stream()
                .map(d -> toJooqRecord(d))
                .collect(Collectors.toList());

        int recordsCreated = summarizeResults(dsl
                .batchInsert(recordsToInsert)
                .execute());

        System.out.println("Created: " + recordsCreated);

    }

    private void updateRelationships(DSLContext dsl, Collection<DataTypeOverview> toInsert) {
        List<DataTypeRecord> recordsToUpdate = toInsert
                .stream()
                .map(d -> {
                    DataTypeRecord record = toJooqRecord(d);
                    record.changed(DATA_TYPE.ID, false);
                    return record;
                })
                .collect(Collectors.toList());

        int recordsUpdated = summarizeResults(dsl
                .batchUpdate(recordsToUpdate)
                .execute());

        System.out.println("Updated: " + recordsUpdated);
    }

    private void markDepreciated(DSLContext dsl, Collection<DataTypeOverview> toInsert) {
        Set<Long> idsToRemove = toInsert
                .stream()
                .filter(overview -> !overview.depreciated())
                .map(DataTypeOverview::id)
                .collect(Collectors.toSet());


        int markedRemoved = dsl
                .update(DATA_TYPE)
                .set(DATA_TYPE.DEPRECATED, true)
                .where(DATA_TYPE.ID.in(idsToRemove))
                .execute();

        System.out.println("Depreciated New: " + markedRemoved);
    }


    private Set<DataTypeOverview> getExistingDataTypes(DSLContext dsl) {
        return dsl
                .select(DATA_TYPE.fields())
                .from(DATA_TYPE)
                .fetch()
                .stream()
                .map(T -> toDomain(T))
                .collect(Collectors.toSet());

    }

    private Set<DataTypeOverview> loadDTsFromFile() throws IOException {
        Set<DataTypeOverview> dataTypeOverviews = new HashSet<>();
        ObjectMapper jsonMapper = getJsonMapper();

        // Create an ObjectReader for DataTypeOverview
        ObjectReader reader = jsonMapper.readerFor(DataTypeOverview.class);

        // Read the file as a stream of DataTypeOverview objects
        try (InputStream resourceAsStream = new FileInputStream(resource)) {
            MappingIterator<DataTypeOverview> iterator = reader.readValues(resourceAsStream);
            while (iterator.hasNext()) {
                try {
                    DataTypeOverview overview = iterator.next();
                    dataTypeOverviews.add(overview);
                } catch (Exception e) {
                    // Get the location (line number) of the error
                    System.out.println(e);
                }
            }
        }

        return dataTypeOverviews;
    }



    private DataTypeOverview toDomain(Record r) {

        DataTypeRecord record = r.into(DATA_TYPE);
        return ImmutableDataTypeOverview.builder()
                .code(record.getCode())
                .name(record.getName())
                .description(Optional.ofNullable(record.getDescription()))
                .id(record.getId())
                .parent_id(Optional.ofNullable(record.getParentId()))
                .concrete(record.getConcrete())
                .unknown(record.getUnknown())
                .depreciated(record.getDeprecated())
                .build();

    }


    private DataTypeRecord toJooqRecord(DataTypeOverview d) {
        DataTypeRecord record = new DataTypeRecord();
        record.setCode(d.code());
        record.setName(d.name());
        record.setDescription(d.description().orElse(null));
        record.setId(d.id());
        record.setParentId(d.parent_id().orElse(null));
        record.setConcrete(d.concrete());
        record.setUnknown(d.unknown());
        record.setDeprecated(d.depreciated());
        return record;
    }

    private static int summarizeResults(int[] rcs) {
        return IntStream.of(rcs).sum();
    }


}
