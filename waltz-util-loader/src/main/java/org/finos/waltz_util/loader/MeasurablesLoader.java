package org.finos.waltz_util.loader;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.schema.tables.records.MeasurableRecord;
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

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.MEASURABLE;
import static org.finos.waltz_util.schema.Tables.MEASURABLE_CATEGORY;

public class MeasurablesLoader {
    private final String resource;
    private final String category;
    private final DSLContext dsl;
    private final Long categoryID;
    private Long maxID;
    private Map<String, Long> ExIDToIDMap;

    public MeasurablesLoader(String resource, String category) {
        AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(DIBaseConfiguration.class);
        dsl = springContext.getBean(DSLContext.class);


        this.resource = resource;
        this.category = category;
        this.categoryID = getCategoryID(category, dsl);


    }




    public void synch(){
        dsl.transaction(ctx -> {
            DSLContext tx = ctx.dsl();




            Set<MeasurablesOverview> existingMeasurables = getExistingMeasurables(tx, categoryID);
            ExIDToIDMap = getExternalToInternalIDMap(existingMeasurables);
            maxID = getMaxID(tx);


            //todo: Implement Org unit mapping (not expected in Data rn)

            Set<MeasurablesOverview> externalMeasurables = loadMeasurablesFromFile(ExIDToIDMap);
            Set<MeasurablesOverview> desiredMeasurables = processExternalMeasurables(externalMeasurables);

            DiffResult<MeasurablesOverview> diff = DiffResult.mkDiff(
                    existingMeasurables,
                    desiredMeasurables,
                    MeasurablesOverview::external_id,
                    Object::equals
            );






            insertNew(diff.otherOnly(), tx);
            updateExisting(diff.differingIntersection(), tx);
            deleteRemoved(diff.waltzOnly(), tx);



        });
    }

    private Set<MeasurablesOverview> processExternalMeasurables(Set<MeasurablesOverview> externalMeasurables){
        return externalMeasurables
                .stream()
                .map(m -> ImmutableMeasurablesOverview.copyOf(m)
                        .withParent_id(Optional.ofNullable(m.parent_external_id().map(ExIDToIDMap::get).orElse(null)))
                        .withMeasurable_category_id(Optional.ofNullable(categoryID)))
                .collect(Collectors.toSet());
    }



    private void insertNew(Collection<MeasurablesOverview> toInsert, DSLContext dsl){
        List<MeasurableRecord> recordsToInsert = toInsert
                .stream()
                .map(m -> {
                    MeasurableRecord record = toJooqRecord(m);
                    return record;
                })
                .sorted(Comparator.comparing(MeasurableRecord::getId))
                .collect(Collectors.toList());

        int numInserted = summarizeResults(dsl
                .batchInsert(recordsToInsert)
                .execute());

        System.out.println("Records Created: " + numInserted);


    }

    private void updateExisting(Collection<MeasurablesOverview> toUpdate, DSLContext dsl){
        List<MeasurableRecord> recordToUpdate = toUpdate
                .stream()
                .map(m -> {
                    MeasurableRecord record = toJooqRecord(m);
                    record.changed(MEASURABLE.ID, false);
                    return record;
                })
                .collect(Collectors.toList());

        int numUpdated = summarizeResults(dsl
                .batchUpdate(recordToUpdate)
                .execute());

        System.out.println("Records Updated: " + numUpdated);

    }

    private void deleteRemoved(Collection<MeasurablesOverview> toDelete, DSLContext dsl){
        if (true){
            return;
        }
        Integer count = 0;
        // recursively delete children
        for (MeasurablesOverview m : toDelete){
            if (m.parent_id().isPresent()){
                count = count + deleteChildren(m.id().get(), dsl);
            }
        }





        System.out.println("Records Deleted: " + count);


    }

    private Integer deleteChildren(Long ID, DSLContext dsl){
        Integer count = 0;
        List<Long> childIDs = dsl.select(MEASURABLE.ID)
                .where(MEASURABLE.PARENT_ID.eq(ID))
                .fetchInto(Long.class);

        for (Long childId : childIDs){
            count = count + deleteChildren(childId, dsl);
        }
        dsl.deleteFrom(MEASURABLE)
                .where(MEASURABLE.ID.eq(ID))
                .execute();

        return ++count;



    }




    private MeasurableRecord toJooqRecord(MeasurablesOverview overview){
        MeasurableRecord record = new MeasurableRecord();
        record.setId(overview.id().orElse(null));
        record.setParentId(overview.parent_id().orElse(null));
        record.setName(overview.name());
        record.setConcrete(overview.concrete());
        record.setDescription(overview.description().orElse(null));
        record.setExternalId(overview.external_id());
        record.setLastUpdatedAt(new Timestamp(System.currentTimeMillis()));
        record.setLastUpdatedBy("waltz-loader");
        record.setProvenance(overview.provenance());
        record.setMeasurableCategoryId(overview.measurable_category_id().orElse(categoryID));
        record.setExternalParentId(overview.parent_external_id().orElse(null));
        record.setEntityLifecycleStatus(overview.entity_lifecycle_status());
        record.setOrganisationalUnitId(overview.organisational_unit_id().orElse(null));
        record.setPosition(overview.position());
        return record;
    }










    private Map<String, Long> getExternalToInternalIDMap(Set<MeasurablesOverview> measurables) {
        return measurables
                .stream()
                .filter(m -> m.id().isPresent())
                .collect(
                        Collectors.toMap(
                                MeasurablesOverview::external_id,
                                m -> m.id().get()
                ));
    }

    private Long getMaxID(DSLContext dsl){
        return dsl
                .select(MEASURABLE.ID.max())
                .from(MEASURABLE)
                .fetchOne(MEASURABLE.ID.max());


    }

    private Set<MeasurablesOverview> loadMeasurablesFromFile(Map<String, Long> ExIDtoIDMap) throws IOException {
        Set<MeasurablesOverview> measurables = new HashSet<>();
        ObjectMapper jsonMapper = getJsonMapper();

        // Create an ObjectReader for DataTypeOverview
        ObjectReader reader = jsonMapper.readerFor(MeasurablesOverview.class);

        // Read the file as a stream of DataTypeOverview objects
        try (InputStream resourceAsStream = new FileInputStream(resource)) {
            MappingIterator<MeasurablesOverview> iterator = reader.readValues(resourceAsStream);
            while (iterator.hasNext()) {
                try {
                    MeasurablesOverview overview = iterator.next();
                    Long actualID = ExIDtoIDMap.getOrDefault(overview.external_id(), ++maxID);
                    MeasurablesOverview processedOverview = ImmutableMeasurablesOverview
                            .copyOf(overview)
                                    .withId(actualID);

                    // if the External ID isn't in the map, add it
                    if (!ExIDtoIDMap.containsKey(overview.external_id())) {
                        ExIDtoIDMap.put(overview.external_id(), actualID);
                    }
                    // we can use this to compute parent ID's later


                    measurables.add(processedOverview);
                } catch (Exception e) {
                    // Get the location (line number) of the error
                    System.out.println(e);
                }
            }
        }
        this.ExIDToIDMap = ExIDtoIDMap;
        return measurables;
    }


    private Set<MeasurablesOverview> getExistingMeasurables(DSLContext dsl, Long catID){
        return dsl
                .select(MEASURABLE.fields())
                .from(MEASURABLE)
                .where(MEASURABLE.MEASURABLE_CATEGORY_ID.eq(catID))
                .fetch()
                .stream()
                .map(r -> toDomain(r))
                .collect(Collectors.toSet());

    }

    private MeasurablesOverview toDomain(Record r){
        MeasurableRecord m = r.into(MEASURABLE);
        return ImmutableMeasurablesOverview.builder()
                .id(Optional.ofNullable(m.getId()))
                .name(m.getName())
                .description(m.getDescription())
                .external_id(m.getExternalId())
                .parent_id(Optional.ofNullable(m.getParentId()))
                .provenance(m.getProvenance())
                .measurable_category_id(m.getMeasurableCategoryId())
                .parent_external_id(Optional.ofNullable(m.getExternalParentId()))
                .entity_lifecycle_status(m.getEntityLifecycleStatus())
                .organisational_unit_id(Optional.ofNullable(m.getOrganisationalUnitId()))
                .position(m.getPosition())
                .build();


    }

    private Long getCategoryID(String category, DSLContext tx) {
        return tx.select(MEASURABLE_CATEGORY.ID)
                .from(MEASURABLE_CATEGORY)
                .where(MEASURABLE_CATEGORY.EXTERNAL_ID.eq(category))
                .fetchOne(MEASURABLE_CATEGORY.ID);
    }

    private static int summarizeResults(int[] rcs) {
        return IntStream.of(rcs).sum();
    }




}
