package org.finos.waltz_util.loader;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableMeasurablesOverview.class)
@JsonDeserialize(as = ImmutableMeasurablesOverview.class)
public abstract class MeasurablesOverview {
    @Value.Auxiliary
    public abstract Optional<Long> id();
    public abstract Optional<Long> parent_id();
    public abstract String external_id();
    public abstract Optional<String> parent_external_id();
    public abstract Optional<Long> measurable_category_id();


    public abstract String name();

    @Value.Default
    public Boolean concrete(){
        return true;
    }

    public abstract Optional<String> description();

    @Value.Default
    public String provenance(){
        return "waltz-loader";
    }

    @Value.Default
    public String entity_lifecycle_status(){
        return "ACTIVE";
    }

    public abstract Optional<String> organisational_unit_external_id();

    public abstract Optional<Long> organisational_unit_id();

    @Value.Default
    public Integer position(){
        return 0;
    }


    /// used to determine parent




}
