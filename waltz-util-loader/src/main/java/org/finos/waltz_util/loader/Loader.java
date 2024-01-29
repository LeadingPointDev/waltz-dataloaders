package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.IntStream;

public abstract class Loader<T> {
    protected final String resource;
    protected final DSLContext dsl;



    public Loader(String resource) {
        this.resource = resource;
        AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(DIBaseConfiguration.class);
        dsl = springContext.getBean(DSLContext.class);
    }

    public Loader() {
        throw new IllegalArgumentException(this.getClass().getName() + " requires an arguement.");
    }


    //#todo check if this is even needed
    public interface Identifier{
        String getExternalId();
    }


    /**
     * Main Dataloader Function for performing the synchronization between external data and the internal waltz data.
     */
    public void synch(){
        dsl.transaction(ctx -> {
            DSLContext tx = ctx.dsl();

            Set<T> existingOverviews = getExistingRecordsAsOverviews(dsl);

            Set<T> rawOverviews = loadFromFile();
            Set<T> desiredOverviews = processOverviews(rawOverviews);
            validate(desiredOverviews);

            DiffResult<T> diff = getDiffResults(existingOverviews, desiredOverviews);

            insertNew(tx, diff.otherOnly());
            updateExisting(tx, diff.differingIntersection());
            deleteExisting(tx, diff.waltzOnly());
        });



    }


    /**
     * Get the difference between the existing overviews and the desired overviews.
     * @param existingOverviews
     * @param desiredOverviews
     * @return the difference between the existing and desired overviews
     */
    protected abstract DiffResult<T> getDiffResults(Set<T> existingOverviews, Set<T> desiredOverviews);


    /**
     * Validate the desired overviews, throw an exception if they are not valid to prevent erroneous data being inserted.
     * @param overviews
     */
    protected abstract void validate(Set<T> overviews);


    /**
     * Get the raw overviews from the file, unprocessed
     *
     * @return
     * @throws IOException
     */
    protected abstract Set<T> loadFromFile() throws IOException;

    /**
     * Process the raw overviews into desired overviews.
     * This is only needed if the raw overviews' format is not the same as the desired overviews.
     * @param rawOverviews
     * @return
     */
    protected abstract Set<T> processOverviews (Set<T> rawOverviews);


    /**
     * Get the existing records from the database.
     * @param tx
     * @return
     */
    protected abstract Set<T> getExistingRecordsAsOverviews(DSLContext tx);

    protected static int summarizeResults(int[] rcs){
        return IntStream.of(rcs).sum();
    }

    /**
     * Insert new records into the database.
     * @param tx
     * @param newRecords
     */
    protected abstract void insertNew(DSLContext tx, Collection<T> newRecords);

    /**
     * Update existing records in the database, based on the identifier
     * @param tx
     * @param existingRecords
     */
    protected abstract void updateExisting(DSLContext tx, Collection<T> existingRecords);

    /**
     * Deletes or marks removed, existing records in the database, based on the identifier
     * @param tx
     * @param existingRecords
     */
    protected abstract void deleteExisting(DSLContext tx, Collection<T> existingRecords);


    /**
     * Convert a record into a desired Overview
     * @param record
     * @return
     */
    protected abstract T toOverview(Record record);



}
